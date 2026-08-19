#!/usr/bin/env python3
"""Dall'illustrazione all'icona dell'app.

    python3 icona/genera.py icona/camper.png

Cosa fa, e perche':

1. **Toglie la cornice bianca** e gli angoli arrotondati dell'originale. Il
   raggio non si indovina: si scende lungo la diagonale fino al primo pixel che
   non e' bianco, e si taglia di quello. Un angolo arrotondato dentro la
   maschera arrotondata del sistema si vede, e sembra un errore.
2. **Inquadra il camper** e non tutta la scena: a icona piena l'illustrazione
   intera fa un camper piccolo in mezzo al paesaggio, e a 48dp non si capisce
   cos'e'. Il riquadro e' scelto a occhio ([RIQUADRO]) e verificato guardando il
   risultato — riconoscere il soggetto in una foto e' l'unica cosa qui che il
   codice non sa fare da se'. Si tiene il **basso**, perche' le ruote sull'asfalto
   dicono "camper" mentre il cielo non dice niente.
3. **Rimpicciolisce al 82%** del riquadro e riempie il bordo **allungando i pixel
   di contorno**: il cielo continua sopra, l'asfalto sotto. Le maschere di Android
   ritagliano fino al 18% per lato, e un bordo che continua il disegno non si
   vede. Sfocare il centro invece che stirare i bordi dava una cornice scura,
   perche' al centro c'e' il camper.
4. Salva un PNG a 324 px: sono 108dp a densita' xxhdpi, quella dei telefoni su
   cui gira. Il doppio dei pixel raddoppia il peso dell'APK per un'icona che
   nessuno guarda con la lente.

Con `--anteprima` scrive anche come la ritaglia un lanciatore col cerchio, che e'
il caso peggiore: e' il controllo che ha portato a questo riquadro invece di uno
piu' stretto, in cui la maschera mangiava il tetto.

L'originale resta qui accanto e **non** finisce nell'APK: sta in `icona/` e non
in `res/`, cosi' il pacchetto porta solo il file generato.
"""

import sys
from PIL import Image, ImageDraw

LATO = 324
DENTRO = 0.82

# Il riquadro dentro l'illustrazione, in frazioni del lato: sinistra, alto,
# destra, basso. Scelto guardando il camper, non calcolato: cercarlo per colore
# trova anche le nuvole, che sono dello stesso bianco-crema della carrozzeria.
RIQUADRO = (0.02, 0.10, 0.94, 1.00)


def bordi(immagine: Image.Image) -> tuple[int, int, int, int]:
    """Il rettangolo dell'illustrazione, senza la cornice bianca."""
    px = immagine.convert("RGB").load()
    larghezza, altezza = immagine.size

    def bianco(colore) -> bool:
        return min(colore) > 245

    sinistra = 0
    while sinistra < larghezza and bianco(px[sinistra, altezza // 2]):
        sinistra += 1
    destra = larghezza - 1
    while destra > 0 and bianco(px[destra, altezza // 2]):
        destra -= 1
    alto = 0
    while alto < altezza and bianco(px[larghezza // 2, alto]):
        alto += 1
    basso = altezza - 1
    while basso > 0 and bianco(px[larghezza // 2, basso]):
        basso -= 1
    return sinistra, alto, destra + 1, basso + 1


def raggio(immagine: Image.Image, riquadro: tuple[int, int, int, int]) -> int:
    """Quanto misura l'angolo arrotondato, in pixel, lungo la diagonale."""
    px = immagine.convert("RGB").load()
    sinistra, alto = riquadro[0], riquadro[1]
    passo = 0
    limite = min(riquadro[2] - sinistra, riquadro[3] - alto) // 3
    while passo < limite and min(px[sinistra + passo, alto + passo]) > 240:
        passo += 1
    return passo


def genera(origine: str, destinazione: str) -> None:
    immagine = Image.open(origine).convert("RGB")
    riquadro = bordi(immagine)
    sinistra, alto, destra, basso = riquadro

    # Il taglio e' il raggio dell'angolo, non una percentuale a caso: con un
    # margine troppo piccolo restano quattro spicchi bianchi negli angoli.
    margine = raggio(immagine, riquadro)
    ritagliata = immagine.crop(
        (sinistra + margine, alto + margine, destra - margine, basso - margine)
    )

    inquadrata = inquadra(ritagliata)
    dentro = int(LATO * DENTRO)
    ridotta = inquadrata.resize((dentro, dentro), Image.LANCZOS)

    scarto = (LATO - dentro) // 2
    tela = Image.new("RGB", (LATO, LATO))
    tela.paste(ridotta, (scarto, scarto))
    contorno(tela, ridotta, scarto)
    tela.save(destinazione, optimize=True)
    print(f"{destinazione}: {LATO}x{LATO}")


def inquadra(disegno: Image.Image) -> Image.Image:
    """Il quadrato attorno al camper, ancorato in basso.

    Ancorato in basso e non centrato: tagliando si perde il cielo, che in un'icona
    da 48dp non dice niente, e si tengono le ruote sull'asfalto, che dicono
    "camper".
    """
    larghezza, altezza = disegno.size
    sinistra, alto, destra, basso = RIQUADRO
    riquadro = disegno.crop(
        (
            int(larghezza * sinistra),
            int(altezza * alto),
            int(larghezza * destra),
            int(altezza * basso),
        )
    )
    lato = min(riquadro.size)
    scarto = (riquadro.size[0] - lato) // 2
    return riquadro.crop((scarto, riquadro.size[1] - lato, scarto + lato, riquadro.size[1]))


def contorno(tela: Image.Image, disegno: Image.Image, scarto: int) -> None:
    """Riempie il bordo stirando la riga e la colonna di contorno del disegno.

    Quattro fasce e quattro angoli. Gli angoli sono un pixel solo, allargato:
    la' si incontrano due direzioni e qualunque scelta e' una macchia di colore,
    tanto vale la piu' semplice.
    """
    lato = disegno.size[0]
    fine = scarto + lato
    resto = tela.size[0] - fine

    tela.paste(disegno.crop((0, 0, lato, 1)).resize((lato, scarto)), (scarto, 0))
    tela.paste(disegno.crop((0, lato - 1, lato, lato)).resize((lato, resto)), (scarto, fine))
    tela.paste(disegno.crop((0, 0, 1, lato)).resize((scarto, lato)), (0, scarto))
    tela.paste(disegno.crop((lato - 1, 0, lato, lato)).resize((resto, lato)), (fine, scarto))

    for (px, py, x, y, larghezza, altezza) in (
        (0, 0, 0, 0, scarto, scarto),
        (lato - 1, 0, fine, 0, resto, scarto),
        (0, lato - 1, 0, fine, scarto, resto),
        (lato - 1, lato - 1, fine, fine, resto, resto),
    ):
        angolo = disegno.crop((px, py, px + 1, py + 1)).resize((larghezza, altezza))
        tela.paste(angolo, (x, y))


def anteprima(icona: str, destinazione: str) -> None:
    """Come la ritaglia un lanciatore col cerchio: 66dp su 108dp, il caso peggiore."""
    tela = Image.open(icona).convert("RGB")
    lato = tela.size[0]
    diametro = int(lato * 66 / 108)
    maschera = Image.new("L", (lato, lato), 0)
    ImageDraw.Draw(maschera).ellipse(
        ((lato - diametro) // 2, (lato - diametro) // 2,
         (lato + diametro) // 2, (lato + diametro) // 2),
        fill=255,
    )
    fuori = Image.new("RGB", (lato, lato), (30, 30, 30))
    fuori.paste(tela, (0, 0), maschera)
    fuori.save(destinazione)
    print(f"{destinazione}: come la vede un lanciatore col cerchio")


if __name__ == "__main__":
    argomenti = [a for a in sys.argv[1:] if not a.startswith("--")]
    origine = argomenti[0] if argomenti else "icona/camper.png"
    icona = "app/src/main/res/drawable-nodpi/ic_launcher_foto.png"
    genera(origine, icona)
    if "--anteprima" in sys.argv:
        anteprima(icona, "/tmp/icona-cerchio.png")
