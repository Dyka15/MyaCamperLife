#!/usr/bin/env bash
# L'armatura: un progetto Gradle JVM che compila ed esegue i sorgenti Kotlin
# senza dipendenze Android — il dominio puro e la parte dell'archivio che lavora
# su java.io.File.
#
# Serve dove l'SDK Android non c'e' (dl.google.com bloccato): senza, il
# compilatore vede il codice per la prima volta in CI, dieci minuti dopo il push.
#
#   armatura.sh crea      crea lo scheletro
#   armatura.sh sync      copia i sorgenti elencati in armatura.txt
#   armatura.sh prova     esegue le prove
#   armatura.sh tutto     sync + prova
#
# ARMATURA e' la cartella dell'armatura (fuori dal repository), REPO la radice
# del progetto. Si possono passare come variabili d'ambiente.
#
# **La sincronizzazione e' a senso unico: repository -> armatura.** Correggere
# nella copia dell'armatura fa passare le prove e lascia il repository rotto:
# e' successo, e la CI l'ha scoperto al posto mio.

set -euo pipefail

REPO="${REPO:-$(pwd)}"
ARMATURA="${ARMATURA:-${TMPDIR:-/tmp}/armatura-jvm}"
ELENCO="${ELENCO:-$REPO/armatura.txt}"
# Cartella di fixture da mettere nel classpath delle prove, se esiste.
RISORSE="${RISORSE:-esempi}"

crea() {
  mkdir -p "$ARMATURA/src/main/kotlin" "$ARMATURA/src/test/kotlin"

  cat > "$ARMATURA/settings.gradle.kts" <<'EOF'
rootProject.name = "armatura"
EOF

  cat > "$ARMATURA/build.gradle.kts" <<'EOF'
// Le versioni seguono quelle del progetto: se il progetto usa Kotlin 2.0.21,
// qui va la stessa, altrimenti l'armatura accetta codice che il progetto
// rifiuta (o viceversa).
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

repositories { mavenCentral() }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    testLogging { events("failed"); showStandardStreams = false }
}
EOF

  if [ ! -f "$ELENCO" ]; then
    cat > "$ELENCO" <<'EOF'
# I file da specchiare nell'armatura, uno per riga, con i percorsi relativi
# alla radice del repository. Le righe che cominciano con # sono commenti.
#
# Si costruisce per sottrazione: metti tutto quello che c'e' sotto dominio/ e
# archivio/, compila, e togli quello che tira dentro android.* o androidx.*.
# I sorgenti vanno prima delle prove, ma l'ordine non conta per Gradle.
#
# esempio:
# app/src/main/java/it/esempio/app/dominio/Tappa.kt
# app/src/test/java/it/esempio/app/dominio/TappaTest.kt
EOF
    echo "creato $ELENCO — elenca i file da specchiare"
  fi
  echo "armatura in $ARMATURA"
}

sync() {
  [ -f "$ELENCO" ] || { echo "manca $ELENCO: lancia prima 'armatura.sh crea'" >&2; exit 1; }
  local copiati=0
  while IFS= read -r riga; do
    case "$riga" in ''|'#'*) continue ;; esac
    local origine="$REPO/$riga"
    [ -f "$origine" ] || { echo "manca: $riga" >&2; continue; }
    # main/java -> main/kotlin, test/java -> test/kotlin, il resto del percorso
    # (il package) si conserva: Kotlin vuole che le cartelle rispecchino i package.
    local relativo="${riga#*src/}"
    local sotto="${relativo%%/*}"          # main o test
    local dentro="${relativo#*/java/}"     # it/esempio/app/...
    local destinazione="$ARMATURA/src/$sotto/kotlin/$dentro"
    mkdir -p "$(dirname "$destinazione")"
    cp "$origine" "$destinazione"
    copiati=$((copiati + 1))
  done < "$ELENCO"

  # Le fixture: i documenti veri dell'utente, che le prove leggono dal classpath.
  # Nel progetto stanno in esempi/ e ci arrivano con una srcDir aggiunta al
  # sourceSet di test; qui si copiano, perche' l'armatura non ha quel blocco.
  if [ -d "$REPO/$RISORSE" ]; then
    mkdir -p "$ARMATURA/src/test/resources"
    cp "$REPO/$RISORSE"/* "$ARMATURA/src/test/resources/" 2>/dev/null || true
    echo "fixture da $RISORSE specchiate"
  fi
  echo "$copiati file specchiati in $ARMATURA"
}

prova() {
  ( cd "$ARMATURA" && gradle test --offline -q ) || true
  python3 - "$ARMATURA" <<'EOF'
import glob, sys, xml.etree.ElementTree as ET
totale = falliti = 0
for f in glob.glob(f"{sys.argv[1]}/build/test-results/test/*.xml"):
    r = ET.parse(f).getroot()
    totale += int(r.get("tests")); falliti += int(r.get("failures")) + int(r.get("errors"))
    for caso in r.iter("testcase"):
        for guaio in caso.iter("failure"):
            print("FALLITO:", caso.get("classname").split(".")[-1], ">", caso.get("name"))
            print((guaio.text or "")[:800], "\n")
print(f"{totale} prove, {falliti} fallite")
sys.exit(1 if falliti else 0)
EOF
}

case "${1:-tutto}" in
  crea)  crea ;;
  sync)  sync ;;
  prova) prova ;;
  tutto) sync && prova ;;
  *) echo "uso: armatura.sh [crea|sync|prova|tutto]" >&2; exit 2 ;;
esac
