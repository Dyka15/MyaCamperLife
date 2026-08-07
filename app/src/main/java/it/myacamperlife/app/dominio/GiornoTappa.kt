package it.myacamperlife.app.dominio

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Legge la data di una tappa dal campo `giorno` dell'itinerario.
 *
 * Il campo e' testo libero: l'itinerario lo scrive chi lo scrive, e le forme
 * che arrivano davvero sono `2026-08-06`, `06/08/2026`, `6 agosto` e `mer 6`.
 * Il briefing serale ha bisogno di una data vera per sapere cosa c'e' domani,
 * e questa e' la funzione che gliela ricava.
 *
 * **Tollerante di proposito.** Una forma che non si riconosce da' `null`, e la
 * tappa finisce fra quelle senza data invece di far cadere il briefing o, molto
 * peggio, di comparire nel giorno sbagliato.
 *
 * Le forme parziali — `6 agosto`, `mer 6` — hanno bisogno di un riferimento per
 * decidere l'anno, o addirittura il mese. Si risolvono **in avanti**: un
 * itinerario parla del viaggio che devi fare, non di quello che hai fatto.
 *
 * Funzione pura.
 */
object GiornoTappa {

    /**
     * **La prima forma riconosciuta e' l'ultima parola.** Se il testo ha la
     * forma di una data ma la data non esiste — `2026-02-31` — il risultato e'
     * `null`, non il ripiego sulla forma successiva: dentro `2026-02-31` c'e'
     * un `02` che sembra un giorno del mese, e leggerlo come tale
     * significherebbe mettere la tappa a caso.
     */
    fun leggi(testo: String?, riferimento: LocalDate): LocalDate? {
        val pulito = testo?.trim()?.lowercase()?.takeUnless { it.isEmpty() } ?: return null
        if (ORDINALE.containsMatchIn(pulito)) return null

        ISO.find(pulito)?.let { return iso(it) }
        NUMERICA.find(pulito)?.let { return numerica(it) }
        MESE_ESTESO.find(pulito)?.let { trovato ->
            // Solo se il mese e' un mese: "6 tappe" non e' una data, e deve
            // poter ripiegare sul giorno da solo.
            if (trovato.groupValues[2] in MESI) return conMese(trovato, riferimento)
        }
        return soloIlNumero(pulito, riferimento)
    }

    /**
     * Raggruppa le tappe per data, tenendo da parte quelle senza.
     *
     * Le date escono in ordine di calendario; dentro un giorno le tappe restano
     * nell'ordine dell'itinerario, che e' quello in cui le farai.
     */
    fun perGiorno(
        tappe: List<Tappa>,
        riferimento: LocalDate,
    ): Pair<Map<LocalDate, List<Tappa>>, List<Tappa>> {
        val conData = sortedMapOf<LocalDate, MutableList<Tappa>>()
        val senzaData = mutableListOf<Tappa>()
        tappe.forEach { tappa ->
            val giorno = leggi(tappa.giorno, riferimento)
            if (giorno == null) senzaData.add(tappa)
            else conData.getOrPut(giorno) { mutableListOf() }.add(tappa)
        }
        return conData.mapValues { it.value.toList() } to senzaData.toList()
    }

    // --- le forme -------------------------------------------------------------

    /** `2026-08-06`. */
    private fun iso(trovato: MatchResult): LocalDate? = data(
        anno = trovato.groupValues[1].toInt(),
        mese = trovato.groupValues[2].toInt(),
        giorno = trovato.groupValues[3].toInt(),
    )

    /** `06/08/2026`, `6.8.26`: giorno prima del mese, come si scrive in Italia. */
    private fun numerica(trovato: MatchResult): LocalDate? = data(
        anno = anno(trovato.groupValues[3]),
        mese = trovato.groupValues[2].toInt(),
        giorno = trovato.groupValues[1].toInt(),
    )

    /** `6 agosto 2026`, e senza anno `6 agosto`, `gio 6 ago`. */
    private fun conMese(trovato: MatchResult, riferimento: LocalDate): LocalDate? {
        val mese = MESI[trovato.groupValues[2]] ?: return null
        val giorno = trovato.groupValues[1].toInt()

        trovato.groupValues[3].takeUnless { it.isEmpty() }?.let { scritto ->
            return data(scritto.toInt(), mese, giorno)
        }

        val questAnno = data(riferimento.year, mese, giorno) ?: return null
        // Un itinerario guarda avanti: "6 gennaio" letto a dicembre e' fra un
        // mese, non undici mesi fa. Un mese di tolleranza indietro tiene buona
        // la tappa di ieri che non hai ancora spuntato.
        return if (questAnno >= riferimento.minusMonths(1)) questAnno
        else data(riferimento.year + 1, mese, giorno)
    }

    /** `mer 6`, `giovedi 6`, `6`: solo il numero del giorno. */
    private fun soloIlNumero(testo: String, riferimento: LocalDate): LocalDate? {
        val trovato = SOLO_NUMERO.find(testo) ?: return null
        val numero = trovato.groupValues[1].toInt()
        if (numero !in 1..31) return null

        val questoMese = data(riferimento.year, riferimento.monthValue, numero)
        // Il 3 letto il 28 agosto e' il 3 settembre. La settimana di tolleranza
        // indietro tiene buona la tappa di ieri.
        if (questoMese != null && questoMese >= riferimento.minusDays(7)) return questoMese

        val prossimo = riferimento.plusMonths(1)
        return data(prossimo.year, prossimo.monthValue, numero)
    }

    // --- utilita' -------------------------------------------------------------

    /** `null` invece di un'eccezione: il 31 febbraio non esiste e non e' un crash. */
    private fun data(anno: Int, mese: Int, giorno: Int): LocalDate? =
        try {
            LocalDate.of(anno, mese, giorno)
        } catch (e: DateTimeException) {
            null
        }

    /** `26` sta per `2026`: due cifre valgono gli anni Duemila. */
    private fun anno(campo: String): Int {
        val numero = campo.toInt()
        return if (campo.length <= 2) 2000 + numero else numero
    }

    private val ISO = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val NUMERICA = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})\b""")
    private val MESE_ESTESO = Regex("""\b(\d{1,2})\s*°?\s+([a-zàèéìòù]{3,})\.?(?:\s+(\d{4}))?""")

    /**
     * Il numero da solo, ma non se sta dentro qualcosa di piu' grande: `mer 6`
     * si', `2026` no.
     */
    private val SOLO_NUMERO = Regex("""(?<!\d)(\d{1,2})(?!\d)""")

    /**
     * `giorno 1`, `day 2`, `tappa 3`: e' il numero **d'ordine** del giorno di
     * viaggio, non una data. Senza sapere quando parti non si puo' convertire,
     * e indovinare metterebbe la tappa in un giorno sbagliato — che e' molto
     * peggio che lasciarla senza data.
     */
    private val ORDINALE = Regex("""\b(giorno|giornata|gg|day|tappa)\s*\.?\s*\d""")

    private val MESI: Map<String, Int> = buildMap {
        listOf(
            "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
            "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
        ).forEachIndexed { indice, nome ->
            put(nome, indice + 1)
            // Le abbreviazioni che si scrivono davvero: "ago", "set", "dic".
            put(nome.take(3), indice + 1)
        }
    }
}
