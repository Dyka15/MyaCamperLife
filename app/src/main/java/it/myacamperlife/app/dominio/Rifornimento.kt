package it.myacamperlife.app.dominio

import java.time.OffsetDateTime

/**
 * Un rifornimento di carburante.
 *
 * **Quello che si legge sullo scontrino sono l'importo e il prezzo al litro**,
 * non i litri: alla colonnina si guarda quanto si e' speso, e il prezzo sta sul
 * cartello. I litri sono il quoziente dei due, e li calcola l'app — chiederli
 * significherebbe far fare una divisione a mano a chi ha la pompa in una mano e
 * il telefono nell'altra.
 *
 * `pieno` non e' un dettaglio: **solo i pieni permettono di calcolare un
 * consumo**, perche' solo con il serbatoio riempito fino in cima si sa quanto
 * carburante e' entrato per fare quei chilometri.
 */
data class Rifornimento(
    val id: String,
    /** Quando hai fatto il rifornimento, non quando l'hai registrato. */
    val istante: OffsetDateTime,
    /**
     * Il contachilometri al momento del rifornimento, **per le righe scritte
     * cosi'**.
     *
     * Nullo su quelle nuove: alla colonnina si azzera il totalizzatore parziale e
     * si legge quello, non i sei numeri del contachilometri — e chi ha la pompa in
     * una mano e il telefono nell'altra copia il numero corto. Le righe vecchie
     * restano leggibili e continuano a valere: e' la stessa tolleranza con cui il
     * prezzo al litro e' comparso dopo i litri.
     */
    val km: Int? = null,
    /**
     * I chilometri fatti **dal rifornimento precedente**: il totalizzatore
     * parziale azzerato all'ultima colonnina.
     *
     * E' il numero che si legge davvero, ed e' anche quello che non si puo'
     * sbagliare di mille: un contachilometri copiato male fa un tratto di
     * ventimila chilometri, un parziale copiato male fa un tratto un po' storto.
     *
     * Fra due pieni si sommano, come i litri: un rabbocco in mezzo porta i suoi
     * chilometri e i suoi litri nello stesso tratto, dove appartengono.
     */
    val kmDaPieno: Int? = null,
    val litri: Double,
    /** L'importo speso: e' il dato primario, quello scritto sullo scontrino. */
    val euro: Double? = null,
    /** Il prezzo al litro del cartello. Con l'importo da' i litri. */
    val prezzoLitro: Double? = null,
    val pieno: Boolean,
    val luogo: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val punto: Punto?
        get() = if (lat != null && lon != null) Punto(istante, lat, lon) else null
}

/**
 * L'aritmetica della colonnina. Funzione pura.
 *
 * Tre numeri legati da una moltiplicazione: importo, prezzo al litro, litri.
 * Sapendone due si ricava il terzo, e l'app ricava sempre **i litri**, perche'
 * sono l'unico dei tre che non c'e' scritto da nessuna parte.
 */
object Carburante {

    /**
     * I litri da importo e prezzo al litro.
     *
     * `null` se uno dei due manca o non e' positivo: un prezzo a zero darebbe
     * infinito litri, e un rifornimento da infinito litri manderebbe il consumo
     * a zero senza che nulla segnali il problema.
     */
    fun litri(euro: Double?, prezzoLitro: Double?): Double? {
        if (euro == null || prezzoLitro == null) return null
        if (euro <= 0 || prezzoLitro <= 0) return null
        return euro / prezzoLitro
    }

    /**
     * Il prezzo al litro da importo e litri: serve a rileggere i rifornimenti
     * scritti prima che il prezzo esistesse come colonna.
     */
    fun prezzo(euro: Double?, litri: Double?): Double? {
        if (euro == null || litri == null) return null
        if (euro <= 0 || litri <= 0) return null
        return euro / litri
    }

    /**
     * Oltre questo prezzo al litro non e' un prezzo, e' una cifra digitata
     * male: in Europa il gasolio non ha mai superato i tre euro.
     */
    const val PREZZO_MASSIMO = 5.0
}
