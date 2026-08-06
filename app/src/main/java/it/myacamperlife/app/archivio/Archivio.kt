package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.dominio.Tappe
import it.myacamperlife.app.dominio.Voce
import it.myacamperlife.app.dominio.Waypoint
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** I dati di un viaggio che non stanno in una tabella. */
@Serializable
data class Viaggio(
    val slug: String,
    val nome: String,
    val creatoIl: String,
    /** Nome del file `.md` da cui e' stato importato, quando lo si sa. */
    val importatoDa: String? = null,
)

/**
 * La cartella dei file, e le operazioni che la riguardano.
 *
 * Per ora l'archivio vive nell'area privata dell'app: funziona sempre e non
 * chiede permessi. Lo specchio nella cartella scelta dall'utente arriva alla
 * fase 9; quando arrivera', questa classe resta la copia di lavoro.
 */
class Archivio(private val radice: File) {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun prepara() {
        cartellaViaggi().mkdirs()
        scriviFormati()
    }

    fun cartellaViaggi(): File = File(radice, "viaggi")

    fun cartellaViaggio(slug: String): File = File(cartellaViaggi(), slug)

    fun tabellaTappe(slug: String): Tabella =
        Tabella(File(cartellaViaggio(slug), TappeTabella.NOME_FILE), TappeTabella.COLONNE)

    fun tabellaSpostamenti(slug: String): Tabella =
        Tabella(File(cartellaViaggio(slug), SpostamentiTabella.NOME_FILE), SpostamentiTabella.COLONNE)

    fun tabellaNote(slug: String): Tabella =
        Tabella(File(cartellaViaggio(slug), NoteTabella.NOME_FILE), NoteTabella.COLONNE)

    fun tabellaFoto(slug: String): Tabella =
        Tabella(File(cartellaViaggio(slug), FotoTabella.NOME_FILE), FotoTabella.COLONNE)

    fun cartellaFoto(slug: String): File =
        File(cartellaViaggio(slug), FotoTabella.CARTELLA).apply { mkdirs() }

    fun diario(slug: String): Diario = Diario(File(cartellaViaggio(slug), "diario.md"))

    // --- viaggi -------------------------------------------------------------

    fun viaggi(): List<Viaggio> = (cartellaViaggi().listFiles() ?: emptyArray())
        .filter { it.isDirectory }
        .mapNotNull { leggiViaggio(it.name) }
        .sortedByDescending { istante(it.creatoIl) }

    /**
     * Ordina per istante assoluto e non per testo: due viaggi creati in fusi
     * diversi si metterebbero in fila sbagliata. Un `creatoIl` illeggibile
     * finisce in fondo invece di far cadere l'elenco.
     */
    private fun istante(iso: String): java.time.Instant =
        runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrDefault(java.time.Instant.EPOCH)

    fun leggiViaggio(slug: String): Viaggio? {
        val file = File(cartellaViaggio(slug), NOME_VIAGGIO)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Viaggio>(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun scriviViaggio(viaggio: Viaggio) {
        cartellaViaggio(viaggio.slug).mkdirs()
        File(cartellaViaggio(viaggio.slug), NOME_VIAGGIO)
            .writeText(json.encodeToString(viaggio), Charsets.UTF_8)
    }

    /**
     * Crea un viaggio dai punti letti da un itinerario e ne scrive le tappe.
     *
     * Gli identificativi nascono qui e non nel lettore dell'itinerario: il
     * lettore e' una funzione pura e deve restare verificabile senza sorprese.
     */
    fun creaViaggio(
        nome: String,
        punti: List<Waypoint>,
        importatoDa: String? = null,
        oggi: LocalDate = LocalDate.now(),
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Viaggio {
        val viaggio = Viaggio(
            slug = slugLibero(slug(nome, oggi)),
            nome = nome,
            creatoIl = adesso.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            importatoDa = importatoDa,
        )
        scriviViaggio(viaggio)

        val ts = viaggio.creatoIl
        val tappe = punti.mapIndexed { indice, punto ->
            Tappa(
                id = UUID.randomUUID().toString().take(8),
                ordine = indice + 1,
                nome = punto.nome,
                lat = punto.lat,
                lon = punto.lon,
                tipo = punto.tipo,
                giorno = punto.giorno,
                descrizione = punto.descrizione,
            )
        }
        tabellaTappe(viaggio.slug).accodaTutte(tappe.map { TappeTabella.riga(it, ts) })
        return viaggio
    }

    fun tappe(slug: String): List<Tappa> = tabellaTappe(slug)
        .vive()
        .mapNotNull { TappeTabella.tappa(it) }
        .sortedBy { it.ordine }


    // --- la giornata di viaggio ---------------------------------------------

    /**
     * Check-in sulla tappa: la marca fatta, registra l'arrivo fra gli
     * spostamenti e aggiorna il diario del giorno.
     *
     * Tre scritture in fila, tutte locali e tutte in aggiunta: nessuna puo'
     * fallire perche' manca la rete.
     */
    fun checkin(slug: String, tappa: Tappa, posizione: Posizione? = null, adesso: OffsetDateTime = OffsetDateTime.now()) {
        val ts = ts(adesso)
        val fatta = Tappe.checkin(tappa, adesso)
        tabellaTappe(slug).accoda(TappeTabella.riga(fatta, ts))
        tabellaSpostamenti(slug).accoda(
            mapOf(
                Csv.ID to nuovoId(),
                Csv.TS to ts,
                SpostamentiTabella.GENERE to SpostamentiTabella.ARRIVO,
                SpostamentiTabella.TAPPA to Csv.testo(tappa.nome),
                SpostamentiTabella.LAT to coordinata(posizione?.lat ?: tappa.lat),
                SpostamentiTabella.LON to coordinata(posizione?.lon ?: tappa.lon),
            ),
        )
        aggiornaDiario(slug, adesso.toLocalDate())
    }

    /** Salta la tappa, o la ripristina se era gia' saltata. */
    fun alternaSalto(slug: String, tappa: Tappa, adesso: OffsetDateTime = OffsetDateTime.now()) {
        val cambiata = Tappe.alterna(tappa)
        if (cambiata == tappa) return
        tabellaTappe(slug).accoda(TappeTabella.riga(cambiata, ts(adesso)))
    }

    /**
     * Aggiunge una tappa all'itinerario, prima di [primaDi] o in fondo.
     *
     * Si riscrivono **solo le tappe il cui numero d'ordine e' cambiato**:
     * inserire in mezzo ne sposta parecchie, ma quelle prima del punto di
     * inserimento restano dove sono e non serve toccarle.
     */
    fun aggiungiTappa(
        slug: String,
        nome: String,
        lat: Double,
        lon: Double,
        giorno: String? = null,
        primaDi: String? = null,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Tappa {
        val prima = tappe(slug)
        val nuova = Tappa(
            id = nuovoId(),
            ordine = 0,
            nome = nome.trim(),
            lat = lat,
            lon = lon,
            giorno = giorno?.trim()?.takeUnless { it.isEmpty() },
        )
        val dopo = Tappe.inserisci(prima, nuova, primaDi)
        val ts = ts(adesso)
        tabellaTappe(slug).accodaTutte(Tappe.cambiate(prima, dopo).map { TappeTabella.riga(it, ts) })
        return dopo.first { it.id == nuova.id }
    }

    fun registraPosizione(
        slug: String,
        posizione: Posizione,
        nota: String? = null,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ) {
        tabellaSpostamenti(slug).accoda(
            mapOf(
                Csv.ID to nuovoId(),
                Csv.TS to ts(adesso),
                SpostamentiTabella.GENERE to SpostamentiTabella.POSIZIONE,
                SpostamentiTabella.TAPPA to Csv.testo(luogo(slug)),
                SpostamentiTabella.LAT to coordinata(posizione.lat),
                SpostamentiTabella.LON to coordinata(posizione.lon),
                SpostamentiTabella.NOTA to Csv.testo(nota),
            ),
        )
        aggiornaDiario(slug, adesso.toLocalDate())
    }

    fun registraNota(
        slug: String,
        testo: String,
        posizione: Posizione? = null,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ) {
        val pulito = Csv.testo(testo)
        if (pulito.isEmpty()) return
        tabellaNote(slug).accoda(
            mapOf(
                Csv.ID to nuovoId(),
                Csv.TS to ts(adesso),
                NoteTabella.TESTO to pulito,
                NoteTabella.TAPPA to Csv.testo(luogo(slug)),
                NoteTabella.LAT to coordinata(posizione?.lat),
                NoteTabella.LON to coordinata(posizione?.lon),
            ),
        )
        aggiornaDiario(slug, adesso.toLocalDate())
    }

    fun registraFoto(
        slug: String,
        nomeFile: String,
        didascalia: String? = null,
        posizione: Posizione? = null,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ) {
        tabellaFoto(slug).accoda(
            mapOf(
                Csv.ID to nuovoId(),
                Csv.TS to ts(adesso),
                FotoTabella.FILE to Csv.testo(nomeFile),
                FotoTabella.DIDASCALIA to Csv.testo(didascalia),
                FotoTabella.TAPPA to Csv.testo(luogo(slug)),
                FotoTabella.LAT to coordinata(posizione?.lat),
                FotoTabella.LON to coordinata(posizione?.lon),
            ),
        )
        aggiornaDiario(slug, adesso.toLocalDate())
    }

    // --- diario --------------------------------------------------------------

    /** Tutte le voci del viaggio, in ordine di ora. */
    fun voci(slug: String): List<Voce> = VociDelGiorno.tutte(
        spostamenti = tabellaSpostamenti(slug).vive(),
        note = tabellaNote(slug).vive(),
        foto = tabellaFoto(slug).vive(),
    )

    fun aggiornaDiario(slug: String, giorno: LocalDate) {
        val tutte = voci(slug)
        diario(slug).aggiorna(
            giorno = giorno,
            voci = VociDelGiorno.delGiorno(tutte, giorno),
            luogo = luogoDelGiorno(tutte, giorno) ?: luogo(slug),
            titolo = leggiViaggio(slug)?.nome,
        )
    }

    /** Rigenera tutte le giornate: serve se il file viene perso o modificato. */
    fun rigeneraDiario(slug: String) {
        val tutte = voci(slug)
        VociDelGiorno.giorni(tutte).forEach { giorno -> aggiornaDiario(slug, giorno) }
    }

    /**
     * Il luogo da mettere nell'intestazione della giornata: l'ultimo arrivo di
     * quel giorno. Se in quel giorno non si e' arrivati da nessuna parte, chi
     * chiama usa la tappa corrente.
     */
    private fun luogoDelGiorno(voci: List<Voce>, giorno: LocalDate): String? =
        VociDelGiorno.delGiorno(voci, giorno)
            .lastOrNull { it.genere == Genere.ARRIVO && it.testo.isNotBlank() }
            ?.testo

    /** Dove sei ora, secondo l'itinerario: serve a nominare foto e note. */
    fun luogo(slug: String): String? = Tappe.corrente(tappe(slug))?.nome

    // --- utilita' -----------------------------------------------------------

    private fun nuovoId(): String = UUID.randomUUID().toString().take(8)

    private fun ts(adesso: OffsetDateTime): String = adesso.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun coordinata(valore: Double?): String =
        valore?.let { Csv.numero(it, 6) } ?: ""

    fun elimina(slug: String) {
        cartellaViaggio(slug).deleteRecursively()
    }

    /** Aggiunge un suffisso se lo slug e' gia' occupato da un altro viaggio. */
    private fun slugLibero(desiderato: String): String {
        if (!cartellaViaggio(desiderato).exists()) return desiderato
        var n = 2
        while (cartellaViaggio("$desiderato-$n").exists()) n++
        return "$desiderato-$n"
    }

    // --- documentazione del formato ----------------------------------------

    /**
     * Scrive `FORMATI.md` nella cartella.
     *
     * Un CSV non descrive se stesso: fra cinque anni una colonna in piu' e
     * nessuno che ricordi cosa significhi e' un problema che il JSON non
     * avrebbe. Il file si rigenera dagli elenchi di colonne del codice, cosi'
     * non puo' andare fuori sincrono con la realta'.
     */
    private fun scriviFormati() {
        val testo = buildString {
            appendLine("# Formati dei file")
            appendLine()
            appendLine("Generato dall'app. Separatore `;`, virgola decimale, codifica UTF-8.")
            appendLine("Una riga fisica e' un record: nessun campo contiene ritorni a capo.")
            appendLine()
            appendLine("Colonne presenti in ogni tabella:")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `id` | Identifica il record. Una riga nuova con lo stesso `id` corregge quella di prima |")
            appendLine("| `ts` | Quando la riga e' stata scritta, ISO-8601 con fuso. A pari `id` vince il `ts` piu' recente |")
            appendLine("| `cancellato` | `si` marca il record come cancellato senza toglierlo dal file |")
            appendLine()
            appendLine("## tappe.csv")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `ordine` | Posizione nell'itinerario, da 1 |")
            appendLine("| `nome` | Nome della tappa |")
            appendLine("| `lat`, `lon` | Coordinate in gradi decimali, sei decimali |")
            appendLine("| `tipo` | Come lo scrive l'itinerario di partenza |")
            appendLine("| `giorno` | Il giorno previsto, come lo scrive l'itinerario |")
            appendLine("| `descrizione` | Testo libero, su una riga sola |")
            appendLine("| `stato` | `da_fare`, `fatta` oppure `saltata` |")
            appendLine("| `checkin` | Istante del check-in, quando c'e' stato |")
            appendLine()
            appendLine("## spostamenti.csv")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `genere` | `arrivo` per un check-in su una tappa, `posizione` per una posizione registrata |")
            appendLine("| `tappa` | Dove eri, secondo l'itinerario |")
            appendLine("| `lat`, `lon` | Coordinate del punto |")
            appendLine("| `nota` | Testo facoltativo |")
            appendLine()
            appendLine("## note.csv")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `testo` | La nota, su una riga sola |")
            appendLine("| `tappa` | Dove eri quando l'hai scritta |")
            appendLine("| `lat`, `lon` | Coordinate, se il GPS le aveva |")
            appendLine()
            appendLine("## foto.csv")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `file` | Nome del file nella sottocartella `foto/` |")
            appendLine("| `didascalia` | Testo facoltativo |")
            appendLine("| `tappa` | Dove eri quando l'hai scattata |")
            appendLine("| `lat`, `lon` | Coordinate, se il GPS le aveva |")
            appendLine()
            appendLine("## diario.md")
            appendLine()
            appendLine("Non e' una tabella: e' il diario del viaggio, una sezione per giorno.")
            appendLine("L'intestazione porta la data in forma ISO, cosi' la sezione di un giorno")
            appendLine("si ritrova per riscriverla. E' una vista degli eventi delle tabelle: se")
            appendLine("si perde, l'app la rigenera.")
        }
        File(radice, "FORMATI.md").writeText(testo, Charsets.UTF_8)
    }

    companion object {
        const val NOME_CARTELLA = "MyaCamperLife"
        private const val NOME_VIAGGIO = "viaggio.json"

        /**
         * Il nome della cartella di un viaggio: `2026-08-toscana`.
         *
         * Anno e mese davanti perche' l'ordine alfabetico diventa cronologico,
         * che e' come si vuole vedere un elenco di viaggi in un gestore file.
         *
         * Funzione pura, quindi verificabile: prende la data invece di
         * leggere l'orologio.
         */
        fun slug(nome: String, oggi: LocalDate): String {
            val parte = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replace(SEGNI, "")
                .lowercase()
                .replace(NON_PAROLA, "-")
                .trim('-')
                .take(40)
                .trim('-')
                .ifEmpty { "viaggio" }
            return "%04d-%02d-%s".format(oggi.year, oggi.monthValue, parte)
        }

        private val SEGNI = "\\p{Mn}+".toRegex()
        private val NON_PAROLA = "[^a-z0-9]+".toRegex()
    }
}
