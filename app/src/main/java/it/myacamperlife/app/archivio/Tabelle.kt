package it.myacamperlife.app.archivio

/**
 * Le colonne delle tabelle del diario.
 *
 * Tre file invece di un registro unico, come le schede del foglio di calcolo
 * di prima: ogni file ha le sue colonne e nessuna riga porta campi che non
 * la riguardano.
 *
 * Tutte e tre ripetono `tappa`, `lat` e `lon`. E' ridondanza voluta: una
 * riga deve raccontarsi da sola quando la si guarda in un foglio di calcolo,
 * senza dover incrociare un'altra tabella per sapere dov'eri.
 */
object SpostamentiTabella {
    const val NOME_FILE = "spostamenti.csv"

    /** `arrivo` per un check-in, `posizione` per una posizione registrata. */
    const val GENERE = "genere"
    const val TAPPA = "tappa"
    const val LAT = "lat"
    const val LON = "lon"
    const val NOTA = "nota"

    const val ARRIVO = "arrivo"
    const val POSIZIONE = "posizione"

    val COLONNE = listOf(Csv.ID, Csv.TS, Csv.CANCELLATO, GENERE, TAPPA, LAT, LON, NOTA)
}

object NoteTabella {
    const val NOME_FILE = "note.csv"

    const val TESTO = "testo"
    const val TAPPA = "tappa"
    const val LAT = "lat"
    const val LON = "lon"

    val COLONNE = listOf(Csv.ID, Csv.TS, Csv.CANCELLATO, TESTO, TAPPA, LAT, LON)
}

object FotoTabella {
    const val NOME_FILE = "foto.csv"
    const val CARTELLA = "foto"

    const val FILE = "file"
    const val DIDASCALIA = "didascalia"
    const val TAPPA = "tappa"
    const val LAT = "lat"
    const val LON = "lon"

    val COLONNE = listOf(Csv.ID, Csv.TS, Csv.CANCELLATO, FILE, DIDASCALIA, TAPPA, LAT, LON)
}

/** Coordinate, quando il GPS le ha date. */
data class Posizione(val lat: Double, val lon: Double)

/**
 * Le spese del viaggio.
 *
 * Tre colonne per un solo importo — [IMPORTO], [VALUTA], [CAMBIO] — e una
 * quarta, [EURO], che e' il loro prodotto. La ridondanza e' voluta: `importo`
 * e' quello che c'era scritto sullo scontrino e non cambia mai, `euro` e'
 * quello che serve a un foglio di calcolo per fare una somma senza sapere
 * niente di cambi.
 *
 * Il carburante non sta qui: ha la sua tabella, che ne chiede gia' l'importo.
 */
object SpeseTabella {
    const val NOME_FILE = "spese.csv"
    const val CARTELLA = "scontrini"

    const val CATEGORIA = "categoria"
    const val DESCRIZIONE = "descrizione"
    const val IMPORTO = "importo"
    const val VALUTA = "valuta"
    const val CAMBIO = "cambio"
    const val EURO = "euro"
    const val MODALITA = "modalita"
    const val TAPPA = "tappa"
    const val LAT = "lat"
    const val LON = "lon"
    const val SCONTRINO = "scontrino"

    val COLONNE = listOf(
        Csv.ID, Csv.TS, Csv.CANCELLATO,
        CATEGORIA, DESCRIZIONE, IMPORTO, VALUTA, CAMBIO, EURO, MODALITA,
        TAPPA, LAT, LON, SCONTRINO,
    )
}

object RifornimentiTabella {
    const val NOME_FILE = "rifornimenti.csv"

    const val KM = "km"
    const val LITRI = "litri"
    const val EURO = "euro"
    const val PIENO = "pieno"
    const val LUOGO = "luogo"
    const val LAT = "lat"
    const val LON = "lon"

    val COLONNE = listOf(Csv.ID, Csv.TS, Csv.CANCELLATO, KM, LITRI, EURO, PIENO, LUOGO, LAT, LON)
}
