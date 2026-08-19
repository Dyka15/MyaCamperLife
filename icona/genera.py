#!/usr/bin/env python3
"""Dall'illustrazione all'icona dell'app.

    python3 icona/genera.py icona/camper.png

Cosa fa, e perche':

1. **Toglie la cornice bianca** e gli angoli arrotondati dell'originale. Il
   raggio non si indovina: si scende lungo la diagonale fino al primo pixel che
   non e' bianco, e si taglia di quello. Un angolo arrotondato dentro la
   maschera arrotondata del sistema si vede, e sembra un errore.
2. **Rimpicciolisce l'illustrazione al 78%** del riquadro e riempie il bordo
   **allungando i pixel di contorno**: il cielo continua sopra, l'asfalto sotto,
   gli alberi ai lati. Le maschere di Android ritagliano fino al 18% per lato — su
   un lanciatore col cerchio si perderebbero i cartelli e le ruote — e un bordo
   che continua il disegno non si vede. Sfocare il centro invece che stirare i
   bordi dava una cornice scura, perche' al centro c'e' il camper.
3. Salva un PNG a 324 px: sono 108dp a densita' xxhdpi, quella dei telefoni su
   cui gira. Il doppio dei pixel raddoppia il peso dell'APK per un'icona che
   nessuno guarda con la lente.

L'originale resta qui accanto e **non** finisce nell'APK: sta in `icona/` e non
in `res/`, cosi' il pacchetto porta solo il file generato.
"""

import sys
from PIL import Image

LATO = 324
DENTRO = 0.78


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

    dentro = int(LATO * DENTRO)
    ridotta = ritagliata.resize((dentro, dentro), Image.LANCZOS)

    scarto = (LATO - dentro) // 2
    tela = Image.new("RGB", (LATO, LATO))
    tela.paste(ridotta, (scarto, scarto))
    contorno(tela, ridotta, scarto)
    tela.save(destinazione, optimize=True)
    print(f"{destinazione}: {LATO}x{LATO}")


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


if __name__ == "__main__":
    origine = sys.argv[1] if len(sys.argv) > 1 else "icona/camper.png"
    genera(origine, "app/src/main/res/drawable-nodpi/ic_launcher_foto.png")
