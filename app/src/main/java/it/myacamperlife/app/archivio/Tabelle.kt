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

    /** Quando hai speso, che non e' `ts`. Vedi [RifornimentiTabella.ISTANTE]. */
    const val ISTANTE = Csv.ISTANTE

    val COLONNE = listOf(
        Csv.ID, Csv.TS, Csv.CANCELLATO, ISTANTE,
        CATEGORIA, DESCRIZIONE, IMPORTO, VALUTA, CAMBIO, EURO, MODALITA,
        TAPPA, LAT, LON, SCONTRINO,
    )
}

object RifornimentiTabella {
    const val NOME_FILE = "rifornimenti.csv"

    /**
     * Quando hai fatto il rifornimento, che **non e' `ts`**: quello dice quando
     * la riga e' stata scritta, e serve alla regola "vince l'ultima". Sono due
     * cose diverse dal momento in cui si puo' registrare uno scontrino di ieri,
     * e confonderle vorrebbe dire che correggere una riga vecchia la sposta nel
     * diario di oggi.
     *
     * Un file scritto prima che questa colonna esistesse non ce l'ha, e in quel
     * caso vale `ts`: allora le due cose coincidevano davvero.
     */
    const val ISTANTE = Csv.ISTANTE

    const val KM = "km"
    const val LITRI = "litri"
    const val EURO = "euro"

    /**
     * Il prezzo al litro del cartello. Con [EURO] da' i [LITRI], che sono un
     * valore derivato: alla colonnina si legge l'importo, non il volume.
     */
    const val PREZZO_LITRO = "prezzo_litro"

    const val PIENO = "pieno"
    const val LUOGO = "luogo"
    const val LAT = "lat"
    const val LON = "lon"

    val COLONNE = listOf(
        Csv.ID, Csv.TS, Csv.CANCELLATO, ISTANTE,
        KM, EURO, PREZZO_LITRO, LITRI, PIENO, LUOGO, LAT, LON,
    )
}
