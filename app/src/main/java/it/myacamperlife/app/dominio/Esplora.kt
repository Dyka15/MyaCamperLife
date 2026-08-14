package it.myacamperlife.app.dominio

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Il prompt di Esplora, e il contesto che gli si mette davanti.
 *
 * **Il prompt e' un'impostazione, non una costante.** Questo qui e' un punto di
 * partenza ragionevole; si modifica dalle impostazioni e finisce in
 * `impostazioni.json`, quindi si aggiusta viaggiando e senza ricompilare.
 *
 * **Il contesto lo compone l'app, non il modello.** Dove sei, che tempo fa, cosa
 * c'e' nei dintorni: sono dati che l'app ha gia' in locale, e passarglieli evita
 * che il modello li inventi o li cerchi in rete peggio di come li sappiamo noi.
 * E' lo stesso principio del diario: l'app produce la giornata strutturata, il
 * modello ci scrive sopra.
 */
object Esplora {

    /**
     * Il prompt di sistema di riposo.
     *
     * Le regole che contiene rispondono a difetti concreti di questo genere di
     * risposte:
     *
     * - **niente elenchi di dieci cose**: in viaggio si decide fra due o tre
     * - **la distanza da dove sei, sempre**: un posto senza distanza non e' una
     *   risposta, e' un suggerimento
     * - **dire quando non si sa**: un'area di sosta inventata fa fare
     *   quaranta chilometri a vuoto, e per un camper e' un guaio vero
     * - **niente indicazioni stradali**: la navigazione la fanno le mappe, e
     *   ripeterla male occupa lo spazio di quello che serve
     * - **verificare gli orari e i prezzi in rete**, perche' cambiano e
     *   OpenStreetMap invecchia
     */
    val PROMPT_DI_RIPOSO: String = """
        Sei l'assistente di viaggio di chi gira in camper. Rispondi in italiano,
        in modo asciutto e concreto, come un amico che quella zona la conosce.

        Come rispondere:
        - Due o tre proposte, non un elenco. Meglio una risposta breve che una
          lista da cui scegliere.
        - Per ogni posto: quanto dista da dove si trova chi chiede, e perche' lo
          proponi. La distanza prima di tutto il resto.
        - Se una cosa non la sai o non la trovi, dillo. Non inventare aree di
          sosta, prezzi, orari o numeri di telefono: chi ti legge guida un mezzo
          di tre tonnellate e mezza e un'informazione sbagliata gli costa
          quaranta chilometri a vuoto.
        - Verifica in rete quello che invecchia: apertura stagionale, prezzi,
          se un'area e' ancora attiva, divieti e limiti in vigore.
        - Tieni conto che si tratta di un camper: altezza e lunghezza contano,
          i centri storici hanno ZTL, e dormire dove non si puo' costa una multa.
        - Niente indicazioni stradali passo per passo: alla navigazione ci pensa
          l'app di mappe.
        - Se il contesto qui sotto dice qualcosa, fidati di quello prima che
          della rete: sono dati misurati sul posto.
    """.trimIndent()

    /**
     * Il prompt per riscrivere una giornata di diario in prosa.
     *
     * La regola che conta e' l'ultima: **non aggiungere niente**. La cronaca
     * della giornata e' un documento, e una prosa che ci infila dettagli
     * inventati la rende inutilizzabile come ricordo.
     */
    val PROMPT_DIARIO: String = """
        Riscrivi in prosa la cronaca di questa giornata di viaggio in camper.
        Italiano, prima persona plurale, tono di un diario personale: asciutto,
        senza enfasi e senza aggettivi da brochure.

        Regole:
        - Da otto a quindici righe. Un paragrafo, due al massimo.
        - Tieni i nomi dei posti, gli orari e i numeri come stanno.
        - **Non aggiungere niente che non sia nella cronaca**: nessun dettaglio
          sul paesaggio, sul cibo o sul tempo che non sia scritto qui. Questo e'
          un ricordo, e un ricordo inventato non vale niente.
        - Se la giornata ha pochi eventi, scrivi poche righe. Non riempire.
        - Non elencare: racconta.
    """.trimIndent()

    /**
     * Il contesto da mettere davanti alla domanda.
     *
     * Solo quello che c'e': una riga assente e' meglio di una riga che dice
     * "non disponibile", che il modello leggerebbe come un fatto.
     */
    fun contesto(
        dove: String?,
        posizione: Coordinate?,
        oggi: LocalDate,
        meteo: Previsione? = null,
        vicini: List<PoiVicino> = emptyList(),
        prossima: Tappa? = null,
    ): String = buildString {
        appendLine("Contesto, misurato dall'app:")
        appendLine("- oggi e' $oggi")
        dove?.let { appendLine("- ci troviamo a $it") }
        posizione?.let { appendLine("- coordinate: ${it.lat} ${it.lon}") }
        prossima?.let { appendLine("- la prossima tappa in programma e' ${it.nome}") }
        meteo?.let { appendLine("- previsione: ${TestoMeteo.riga(it)}") }

        if (vicini.isNotEmpty()) {
            appendLine("- nei dintorni, da OpenStreetMap:")
            vicini.forEach { vicino ->
                appendLine(
                    "  · ${vicino.poi.etichetta()} (${vicino.poi.categoria.senzaNome}), " +
                        "${vicino.distanza}",
                )
            }
            appendLine(
                "  Questo elenco viene da OpenStreetMap e puo' essere incompleto o " +
                    "invecchiato: usalo come punto di partenza, non come verita'.",
            )
        }
    }.trim()

    /**
     * Il contesto di **una tappa**, che non e' dove sei.
     *
     * E' la differenza che rende utile il pulsante nella scheda: si chiede di
     * Bolsena stando a Orvieto, tre giorni prima di arrivarci, e il modello deve
     * ragionare su Bolsena e su quel giorno — non su qui e adesso. Da qui le due
     * date distinte: `oggi` perche' serve a sapere quanto e' lontano il fatto, e
     * il giorno della tappa perche' e' quello che conta per il meteo e per le
     * aperture stagionali.
     */
    fun contestoDiTappa(
        tappa: Tappa,
        giorno: LocalDate?,
        oggi: LocalDate,
        previsione: Previsione? = null,
        vicini: List<PoiVicino> = emptyList(),
        da: Tappa? = null,
    ): String = buildString {
        appendLine("Contesto, misurato dall'app:")
        appendLine("- oggi e' $oggi")
        appendLine("- la tappa di cui si parla e' ${tappa.nome}")
        appendLine("- le sue coordinate: ${tappa.lat} ${tappa.lon}")
        giorno?.let {
            val fra = it.toEpochDay() - oggi.toEpochDay()
            appendLine("- ci si arriva il $it, ${TestoTappa.quando(fra)}")
        }
        da?.let { appendLine("- si arriva da ${it.nome}") }
        tappa.descrizione?.trim()?.takeUnless { it.isEmpty() }?.let {
            // Fra virgolette e attribuita: e' testo di chi ha scritto
            // l'itinerario, non una misura dell'app, e le due cose non si
            // confondono.
            appendLine("- dall'itinerario, scritto da chi viaggia: \"$it\"")
        }
        previsione?.let { appendLine("- previsione per quel giorno: ${TestoMeteo.riga(it)}") }

        if (vicini.isNotEmpty()) {
            appendLine("- nei dintorni della tappa, da OpenStreetMap:")
            vicini.forEach { vicino ->
                appendLine(
                    "  · ${vicino.poi.etichetta()} (${vicino.poi.categoria.senzaNome}), " +
                        "${vicino.distanza}",
                )
            }
            appendLine(
                "  Questo elenco viene da OpenStreetMap e puo' essere incompleto o " +
                    "invecchiato: usalo come punto di partenza, non come verita'.",
            )
        }
    }.trim()

    /**
     * La domanda che parte dal pulsante della scheda.
     *
     * **E' scritta e non chiesta.** Un campo di testo su quella schermata
     * significherebbe comporre una domanda ogni volta per chiedere sempre la
     * stessa cosa; il campo libero resta in Esplora, dove serve a chiedere
     * qualcosa di diverso. Qui un tocco, come le azioni rapide.
     */
    val DOMANDA_TAPPA: String = "Cosa c'e' da sapere nei dintorni di questa tappa? " +
        "Dove si passa la notte in camper, cosa vale la pena vedere, e cosa devo " +
        "tenere presente arrivandoci."

    /** La domanda completa: contesto, poi quello che ha scritto l'utente. */
    fun domanda(contesto: String, chiesto: String): String =
        if (contesto.isBlank()) chiesto.trim() else "$contesto\n\nDomanda: ${chiesto.trim()}"

    /**
     * Quanti punti di interesse mettere nel contesto.
     *
     * Otto e non trenta: il contesto si paga a token, e trenta righe di
     * fontanelle affogano le due cose che contano.
     */
    const val VICINI_NEL_CONTESTO = 8

    /** Un titolo per il dossier, ricavato dalla domanda. */
    fun titolo(domanda: String): String {
        val pulito = domanda.trim().replace("\\s+".toRegex(), " ")
        return if (pulito.length <= 60) pulito else pulito.take(57).trimEnd() + "…"
    }

    /** Il nome del file del dossier: `AAAAMMGG_HHMMSS_domanda.md`. */
    fun nomeFile(istante: java.time.OffsetDateTime, domanda: String): String {
        val quando = istante.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val parte = java.text.Normalizer
            .normalize(domanda.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .take(40)
            .trim('-')
        return if (parte.isEmpty()) "$quando.md" else "${quando}_$parte.md"
    }

    /**
     * Il dossier come finisce su file: domanda, risposta, fonti, contesto.
     *
     * **Si salva anche il contesto.** Fra sei mesi, rileggendo una risposta che
     * si e' rivelata sbagliata, la domanda vera e' "cosa sapeva l'app quando l'ha
     * chiesto": senza il contesto non si puo' rispondere.
     */
    fun dossier(
        domanda: String,
        contesto: String,
        risposta: RispostaModello,
        istante: java.time.OffsetDateTime,
    ): String = buildString {
        appendLine("# ${titolo(domanda)}")
        appendLine()
        appendLine("_${istante.format(QUANDO)} · ${risposta.modello.nome}_")
        appendLine()
        appendLine(risposta.testo.trim())

        // La sezione c'e' **sempre**, anche vuota: "nessuna fonte dichiarata" e'
        // un'informazione — dice che quella risposta viene dalla memoria del
        // modello e non da qualcosa che si puo' andare a controllare. La sua
        // assenza invece si confonde con un difetto dell'app, ed e' successo.
        appendLine()
        appendLine("## Fonti")
        appendLine()
        if (risposta.fonti.isEmpty()) {
            appendLine("_Nessuna fonte dichiarata: il modello ha risposto senza cercare._")
        } else {
            risposta.fonti.forEach { fonte ->
                appendLine("- [${fonte.etichetta()}](${fonte.indirizzo})")
            }
        }

        appendLine()
        appendLine("## La domanda")
        appendLine()
        appendLine("```")
        appendLine(domanda.trim())
        appendLine("```")

        if (contesto.isNotBlank()) {
            appendLine()
            appendLine("## Cosa sapeva l'app")
            appendLine()
            appendLine("```")
            appendLine(contesto.trim())
            appendLine("```")
        }
    }

    private val QUANDO = java.time.format.DateTimeFormatter
        .ofPattern("d MMMM yyyy, HH:mm", java.util.Locale.ITALIAN)

    /** I chilometri come li legge un modello: arrotondati, senza decimali finti. */
    fun km(valore: Double): String = "${valore.roundToInt()} km"
}

/**
 * Una risposta salvata: quello che si ritrova arrivando sul posto, offline.
 *
 * Il testo non e' qui dentro — sta nel file — perche' un elenco di dossier deve
 * potersi mostrare senza leggere mezzo megabyte di prosa dal disco.
 */
data class Dossier(
    val id: String,
    val istante: java.time.OffsetDateTime,
    val domanda: String,
    val tappa: String?,
    val modello: String?,
    val file: String,
) {
    fun titolo(): String = Esplora.titolo(domanda)
}
