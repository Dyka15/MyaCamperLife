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
