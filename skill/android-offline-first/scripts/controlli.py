#!/usr/bin/env python3
"""Controlli statici su un progetto Android Kotlin/Compose, senza SDK.

Da lanciare dalla radice del progetto:

    python3 skill/android-offline-first/scripts/controlli.py

Quattro controlli, ognuno nato da un difetto che e' arrivato in CI:

1. import Compose mancanti nei sorgenti (il compilatore non li deduce)
2. import di asserzioni JUnit mancanti nei test (un'asserzione senza import non
   la vede nessuno finche' la CI non cade)
3. stringhe usate ma non definite, e definite ma non usate
4. XML delle risorse sintatticamente valido

Esce con codice 1 se trova qualcosa: si puo' usare in un hook o in un workflow.
"""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

# --- dove guardare -----------------------------------------------------------
# Cambia questi due se il progetto ha un'altra struttura.
SORGENTI = pathlib.Path("app/src/main/java")
PROVE = pathlib.Path("app/src/test/java")
RISORSE = pathlib.Path("app/src/main/res")
MANIFEST = pathlib.Path("app/src/main/AndroidManifest.xml")

# --- simboli Compose e il pacchetto da cui vengono ---------------------------
# L'elenco copre quello che si usa davvero in un'app di questo tipo. Aggiungine
# quando ne usi di nuovi: il costo e' una riga, il beneficio e' non scoprirlo
# dieci minuti dopo il push.
PACCHETTI: dict[str, str] = {
    "androidx.compose.foundation.layout": (
        "padding fillMaxWidth fillMaxSize fillMaxHeight width height size Row Column Box IntrinsicSize "
        "Spacer Arrangement PaddingValues wrapContentWidth FlowRow FlowColumn "
        "ExperimentalLayoutApi"
    ),
    # Solo simboli di **primo livello**: `stickyHeader` e' un membro di
    # LazyListScope e non si importa. Metterlo qui ha fatto chiedere al
    # controllo un import inesistente, e la CI e' caduta su quello: una voce
    # sbagliata in questa tabella non e' un falso allarme innocuo, fabbrica
    # una correzione sbagliata.
    "androidx.compose.foundation.lazy": "LazyColumn LazyRow items itemsIndexed",
    "androidx.compose.foundation.pager": "HorizontalPager VerticalPager rememberPagerState",
    "androidx.compose.foundation": (
        "clickable verticalScroll horizontalScroll rememberScrollState background border Image "
        "ExperimentalFoundationApi"
    ),
    "androidx.compose.foundation.shape": "RoundedCornerShape CircleShape",
    "androidx.compose.foundation.text": "KeyboardOptions",
    "androidx.compose.foundation.text.selection": "SelectionContainer DisableSelection",
    "androidx.compose.material3": (
        "Text Card CardDefaults Icon IconButton TextButton Switch Checkbox AlertDialog "
        "OutlinedTextField TextField MaterialTheme Scaffold HorizontalDivider VerticalDivider "
        "DropdownMenu DropdownMenuItem NavigationBar NavigationBarItem TopAppBar "
        "TopAppBarDefaults SnackbarHost SnackbarHostState LinearProgressIndicator "
        "CircularProgressIndicator ExtendedFloatingActionButton FloatingActionButton Button "
        "FilledTonalButton OutlinedButton Surface ListItem Slider FilterChip AssistChip"
    ),
    "androidx.compose.runtime": (
        "Composable remember LaunchedEffect mutableStateOf getValue setValue "
        "rememberCoroutineScope DisposableEffect snapshotFlow derivedStateOf produceState"
    ),
    "androidx.compose.runtime.saveable": "rememberSaveable",
    "androidx.compose.ui": "Modifier Alignment",
    "androidx.compose.ui.unit": "dp sp Dp",
    "androidx.compose.ui.res": "stringResource painterResource",
    "androidx.compose.ui.draw": "clip alpha",
    "androidx.compose.ui.layout": "ContentScale",
    "androidx.compose.ui.text": "AnnotatedString",
    "androidx.compose.ui.text.font": "FontFamily FontWeight",
    "androidx.compose.ui.text.style": "TextDecoration TextAlign TextOverflow",
    "androidx.compose.ui.text.input": "KeyboardType PasswordVisualTransformation",
    "androidx.compose.ui.platform": "LocalContext LocalConfiguration LocalClipboardManager",
    "androidx.compose.ui.graphics": "Color asImageBitmap",
    "androidx.lifecycle.compose": "collectAsStateWithLifecycle",
    "androidx.core.content": "FileProvider ContextCompat",
}
SIMBOLI = {s: p for p, elenco in PACCHETTI.items() for s in elenco.split()}

ASSERZIONI = {
    s: "org.junit.Assert"
    for s in "assertEquals assertTrue assertFalse assertNull assertNotNull assertSame fail".split()
}


def corpo_e_import(testo: str) -> tuple[str, set[str]]:
    """Il file senza le righe di import, e i nomi importati."""
    importati = set(re.findall(r"^import\s+[\w.]*\.(\w+)", testo, re.M))
    corpo = "\n".join(r for r in testo.splitlines() if not r.startswith("import "))
    return corpo, importati


def import_mancanti(cartella: pathlib.Path, simboli: dict[str, str]) -> int:
    problemi = 0
    if not cartella.exists():
        return 0
    for file in sorted(cartella.rglob("*.kt")):
        corpo, importati = corpo_e_import(file.read_text(encoding="utf-8"))
        for simbolo, pacchetto in simboli.items():
            usato = re.search(rf"(?<![\w.]){simbolo}\s*[({{.]", corpo) or re.search(
                rf"\.{simbolo}\(", corpo
            )
            if not usato or simbolo in importati:
                continue
            # definito nel file stesso? allora l'import non serve
            if re.search(rf"(fun|val|var|class|object|enum class)\s+{simbolo}\b", corpo):
                continue
            print(f"{file}: usa '{simbolo}' senza import ({pacchetto}.{simbolo})")
            problemi += 1
    return problemi


def stringhe() -> int:
    file_stringhe = RISORSE / "values" / "strings.xml"
    if not file_stringhe.exists():
        return 0
    definite = {
        e.get("name")
        for e in ET.parse(file_stringhe).getroot()
        if e.tag in ("string", "plurals", "string-array")
    }
    usate: set[str] = set()
    for file in SORGENTI.rglob("*.kt"):
        usate |= set(re.findall(r"R\.string\.(\w+)", file.read_text(encoding="utf-8")))
    for file in RISORSE.rglob("*.xml"):
        usate |= set(re.findall(r"@string/(\w+)", file.read_text(encoding="utf-8")))
    if MANIFEST.exists():
        usate |= set(re.findall(r"@string/(\w+)", MANIFEST.read_text(encoding="utf-8")))

    problemi = 0
    for nome in sorted(usate - definite):
        print(f"strings.xml: manca '{nome}', usata nel codice")
        problemi += 1
    for nome in sorted(definite - usate):
        # Una stringa non usata e' quasi sempre il residuo di una funzione
        # cambiata: non rompe niente, ma il file si sporca in fretta.
        print(f"strings.xml: '{nome}' non e' usata da nessuna parte")
        problemi += 1
    return problemi


def xml_valido() -> int:
    problemi = 0
    for file in sorted(RISORSE.rglob("*.xml")) + ([MANIFEST] if MANIFEST.exists() else []):
        try:
            ET.parse(file)
        except ET.ParseError as errore:
            print(f"{file}: XML non valido — {errore}")
            problemi += 1
    return problemi


def main() -> int:
    problemi = 0
    problemi += import_mancanti(SORGENTI, SIMBOLI)
    problemi += import_mancanti(PROVE, ASSERZIONI)
    problemi += stringhe()
    problemi += xml_valido()
    print(f"--- {problemi} problemi")
    return 1 if problemi else 0


if __name__ == "__main__":
    sys.exit(main())
