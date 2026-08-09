package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Briefing
import it.myacamperlife.app.dominio.Briefings
import it.myacamperlife.app.dominio.Carburante
import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.Coordinate
import it.myacamperlife.app.dominio.Dintorno
import it.myacamperlife.app.dominio.Dossier
import it.myacamperlife.app.dominio.Esplora
import it.myacamperlife.app.dominio.Consumi
import it.myacamperlife.app.dominio.Consumo
import it.myacamperlife.app.dominio.Conto
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.GiornoTappa
import it.myacamperlife.app.dominio.Luoghi
import it.myacamperlife.app.dominio.Meteo
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.Overpass
import it.myacamperlife.app.dominio.Poi
import it.myacamperlife.app.dominio.Punto
import it.myacamperlife.app.dominio.PuntoMeteo
import it.myacamperlife.app.dominio.PuntoTratta
import it.myacamperlife.app.dominio.RispostaMeteo
import it.myacamperlife.app.dominio.Rifornimento
import it.myacamperlife.app.dominio.Slittamenti
import it.myacamperlife.app.dominio.RispostaModello
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Spese
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.StimaAutonomia
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.dominio.Tappe
import it.myacamperlife.app.dominio.Tratta
import it.myacamperlife.app.dominio.Tratte
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
 * L'archivio vive nell'area privata dell'app: funziona sempre, non chiede
 * permessi, e su di esso valgono le proprieta' del formato — `append` piu'
 * `fsync`, rinomina atomica, correggere senza distruggere.
 *
 * **Questa e' la copia di lavoro, e resta l'autorita'.** La copia leggibile da
 * fuori la fa [Specchio], ricopiando in una cartella scelta dall'utente: su un
 * albero SAF non esiste `append`, quindi scrivere direttamente la' farebbe
 * perdere tutte quelle proprieta'.
 */
class Archivio(private val radice: File) {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun prepara() {
        cartellaViaggi().mkdirs()
        scriviFormati()
    }

    /**
     * La radice dell'archivio: quello che lo specchio ricopia fuori.
     *
     * Sta nell'area privata dell'app, dove funziona sempre. La copia leggibile
     * dall'esterno la fa [Specchio], e resta una copia.
     */
    fun radiceArchivio(): File = radice

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

    fun tabellaRifornimenti(slug: String): Tabella =
        Tabella(File(cartellaViaggio(slug), RifornimentiTabella.NOME_FILE), RifornimentiTabella.COLONNE)

    fun tabellaSpese(slug: String): Tabella =
        Tabella(File(cartellaViaggio(slug), SpeseTabella.NOME_FILE), SpeseTabella.COLONNE)

    fun cartellaFoto(slug: String): File =
        File(cartellaViaggio(slug), FotoTabella.CARTELLA).apply { mkdirs() }

    fun cartellaScontrini(slug: String): File =
        File(cartellaViaggio(slug), SpeseTabella.CARTELLA).apply { mkdirs() }

    fun diario(slug: String): Diario = Diario(File(cartellaViaggio(slug), "diario.md"))

    // --- la scorta: rete presa in anticipo ------------------------------------

    /**
     * La cartella della scorta: quello che arriva dalla rete e viene messo da
     * parte per quando la rete non c'e'.
     *
     * Sta dentro il viaggio e non accanto all'archivio: le tratte e le
     * previsioni riguardano quell'itinerario, e cancellando il viaggio se ne
     * vanno con lui.
     */
    fun cartellaScorta(slug: String): File =
        File(cartellaViaggio(slug), TratteTabella.CARTELLA).apply { mkdirs() }

    fun tabellaTratte(slug: String): Tabella =
        Tabella(File(cartellaScorta(slug), TratteTabella.NOME_FILE), TratteTabella.COLONNE)

    fun tratte(slug: String): Tratte = TratteTabella.tratte(tabellaTratte(slug).vive())

    /**
     * Salva le tratte precalcolate.
     *
     * Righe accodate come tutto il resto: ricalcolarle dopo aver aggiunto una
     * tappa corregge quelle vecchie invece di ammucchiarle, perche' l'`id`
     * viene dalle coordinate.
     */
    fun salvaTratte(slug: String, tratte: List<Tratta>, adesso: OffsetDateTime = OffsetDateTime.now()) {
        if (tratte.isEmpty()) return
        val ts = ts(adesso)
        tabellaTratte(slug).accodaTutte(tratte.map { TratteTabella.riga(it, ts) })
    }

    private fun fileMeteo(slug: String): File =
        File(cartellaScorta(slug), TratteTabella.NOME_METEO)

    /**
     * Le previsioni messe da parte, o `null` se non ce ne sono.
     *
     * Un file illeggibile vale come assente: il briefing esce lo stesso, senza
     * meteo. Non c'e' niente che l'utente possa fare con un errore di parsing
     * alle 19:00.
     */
    fun meteo(slug: String): Meteo? {
        val file = fileMeteo(slug)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Meteo>(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun salvaMeteo(slug: String, meteo: Meteo) {
        cartellaScorta(slug)
        fileMeteo(slug).writeText(json.encodeToString(meteo), Charsets.UTF_8)
    }

    /**
     * I punti di cui chiedere il meteo: le tappe da fare nei prossimi giorni.
     *
     * Non tutte le tappe del viaggio: un itinerario di trenta tappe farebbe una
     * richiesta enorme per previsioni che scadranno prima di servire.
     */
    fun puntiMeteo(slug: String, oggi: LocalDate = LocalDate.now(), giorni: Int = RispostaMeteo.GIORNI): List<PuntoMeteo> {
        val daFare = tappe(slug).filter { it.stato == StatoTappa.DA_FARE }
        val (perGiorno, senzaData) = GiornoTappa.perGiorno(daFare, oggi)
        val finestra = perGiorno.filterKeys { it <= oggi.plusDays(giorni.toLong()) }.values.flatten()
        // Le tappe senza data entrano lo stesso: non si sa quando ci arrivi, ma
        // il meteo di dove stai andando serve comunque.
        return (finestra + senzaData)
            .distinctBy { it.id }
            .map { PuntoMeteo(it.nome, it.lat, it.lon) }
    }

    /** I punti per cui chiedere le tratte: tutte le tappe, in ordine. */
    fun puntiTratte(slug: String): List<PuntoTratta> =
        tappe(slug).map { PuntoTratta(it.nome, it.lat, it.lon) }

    // --- i dintorni: punti di interesse e toponimi -----------------------------

    fun tabellaPoi(slug: String): Tabella =
        Tabella(File(cartellaScorta(slug), PoiTabella.NOME_FILE), PoiTabella.COLONNE)

    fun tabellaLuoghi(slug: String): Tabella =
        Tabella(File(cartellaScorta(slug), LuoghiTabella.NOME_FILE), LuoghiTabella.COLONNE)

    fun poi(slug: String): List<Poi> = tabellaPoi(slug).vive().mapNotNull { PoiTabella.poi(it) }

    fun luoghi(slug: String): Luoghi =
        Luoghi(tabellaLuoghi(slug).vive().mapNotNull { LuoghiTabella.luogo(it) })

    /**
     * Salva i dintorni scaricati.
     *
     * Righe accodate: riscaricare aggiorna quello che c'era, perche' l'`id` di
     * un punto di interesse e' quello di OpenStreetMap e quello di un toponimo
     * viene dal nome e dalla posizione.
     */
    fun salvaDintorni(slug: String, dintorno: Dintorno, adesso: OffsetDateTime = OffsetDateTime.now()) {
        val ts = ts(adesso)
        if (dintorno.poi.isNotEmpty()) {
            tabellaPoi(slug).accodaTutte(dintorno.poi.map { PoiTabella.riga(it, ts) })
        }
        if (dintorno.luoghi.isNotEmpty()) {
            tabellaLuoghi(slug).accodaTutte(dintorno.luoghi.map { LuoghiTabella.riga(it, ts) })
        }
    }

    /**
     * I punti su cui centrare la richiesta dei dintorni: le tappe che devi
     * ancora fare, piu' quella dove sei.
     *
     * Non tutte: la polilinea di un itinerario di cinquanta tappe metterebbe in
     * ginocchio il server pubblico, e i dintorni delle tappe gia' fatte non
     * servono piu' a niente.
     */
    fun puntiDintorni(slug: String, quanti: Int = Overpass.PUNTI_MASSIMI): List<Coordinate> {
        val tappe = tappe(slug)
        val corrente = Tappe.corrente(tappe)
        val daFare = tappe.filter { it.stato == StatoTappa.DA_FARE }
        return (listOfNotNull(corrente) + daFare)
            .take(quanti)
            .map { Coordinate(it.lat, it.lon) }
    }

    /**
     * Come si chiama il posto dove sei, senza rete.
     *
     * Il toponimo vince sul nome della tappa quando c'e': "3 km da Bolsena"
     * dice dove sei davvero, "Orvieto" dice dove hai fatto l'ultimo check-in, e
     * fra i due il primo e' piu' onesto. Senza scorta di toponimi si ripiega
     * sulla tappa, che e' come funzionava prima e funziona comunque.
     */
    fun dove(slug: String, posizione: Posizione?): String? {
        if (posizione != null) {
            luoghi(slug).nome(posizione.lat, posizione.lon)?.let { return it }
        }
        return luogo(slug)
    }

    /**
     * Da dove cercare nei dintorni: l'ultimo punto registrato, o la tappa
     * corrente, o la prima tappa dell'itinerario.
     *
     * Tre ripieghi in fila perche' Esplora deve dare una risposta anche appena
     * importato un itinerario, prima di aver registrato qualunque cosa: la prima
     * tappa e' dove sarai, ed e' meglio di una schermata vuota.
     */
    fun dovePunto(slug: String): Coordinate? {
        punti(slug).lastOrNull()?.let { return Coordinate(it.lat, it.lon) }
        val tappe = tappe(slug)
        val riferimento = Tappe.corrente(tappe) ?: tappe.firstOrNull() ?: return null
        return Coordinate(riferimento.lat, riferimento.lon)
    }

    /** La stessa cosa con la distanza dentro: "3 km da Bolsena". */
    fun doveDetto(slug: String, posizione: Posizione?): String? {
        if (posizione != null) {
            luoghi(slug).descrizione(posizione.lat, posizione.lon)?.let { return it }
        }
        return luogo(slug)
    }

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
                altro = punto.altro,
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
                SpostamentiTabella.TAPPA to Csv.testo(dove(slug, posizione)),
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
                NoteTabella.TAPPA to Csv.testo(dove(slug, posizione)),
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
                FotoTabella.TAPPA to Csv.testo(dove(slug, posizione)),
                FotoTabella.LAT to coordinata(posizione?.lat),
                FotoTabella.LON to coordinata(posizione?.lon),
            ),
        )
        aggiornaDiario(slug, adesso.toLocalDate())
    }

    /**
     * Registra un rifornimento.
     *
     * **Si scrivono importo e prezzo al litro, e i litri si calcolano**: alla
     * colonnina si legge quanto si e' speso e il prezzo sul cartello, mai il
     * volume. La colonna `litri` viene scritta comunque, perche' e' quella che
     * un foglio di calcolo somma, ma in lettura si rifa' il conto: correggere il
     * prezzo aggiorna il consumo.
     *
     * `pieno` decide se il tratto e' misurabile: solo fra due pieni si sa quanto
     * carburante e' entrato per quei chilometri. Vale la pena chiederlo ogni
     * volta, anche se sembra un dettaglio.
     *
     * [istante] e' quando hai fatto il rifornimento; [adesso] quando lo stai
     * registrando. Coincidono quasi sempre, ma non quando ritrovi lo scontrino
     * di ieri in tasca.
     */
    fun registraRifornimento(
        slug: String,
        km: Int,
        euro: Double,
        prezzoLitro: Double,
        pieno: Boolean = true,
        posizione: Posizione? = null,
        adesso: OffsetDateTime = OffsetDateTime.now(),
        istante: OffsetDateTime = adesso,
    ) {
        val litri = Carburante.litri(euro, prezzoLitro) ?: return
        tabellaRifornimenti(slug).accoda(
            mapOf(
                Csv.ID to nuovoId(),
                Csv.TS to ts(adesso),
                RifornimentiTabella.ISTANTE to ts(istante),
                RifornimentiTabella.KM to km.toString(),
                RifornimentiTabella.EURO to Csv.numero(euro),
                // Tre decimali: il gasolio costa 1,719 euro al litro, e
                // arrotondare a due sposterebbe i litri di mezzo per cento.
                RifornimentiTabella.PREZZO_LITRO to Csv.numero(prezzoLitro, 3),
                RifornimentiTabella.LITRI to Csv.numero(litri, 2),
                RifornimentiTabella.PIENO to Csv.booleano(pieno),
                RifornimentiTabella.LUOGO to Csv.testo(dove(slug, posizione)),
                RifornimentiTabella.LAT to coordinata(posizione?.lat),
                RifornimentiTabella.LON to coordinata(posizione?.lon),
            ),
        )
        aggiornaDiario(slug, istante.toLocalDate())
    }

    /**
     * Registra una spesa.
     *
     * Si scrive quello che c'era sullo scontrino — importo e valuta — e il
     * cambio applicato in quel momento. La colonna `euro` e' il prodotto dei
     * due, calcolato qui una volta sola: un foglio di calcolo la somma senza
     * dover sapere niente di valute.
     */
    fun registraSpesa(
        slug: String,
        categoria: Categoria,
        importo: Double,
        modalita: Modalita,
        descrizione: String? = null,
        valuta: String = Spesa.EURO,
        cambio: Double? = null,
        scontrino: String? = null,
        posizione: Posizione? = null,
        adesso: OffsetDateTime = OffsetDateTime.now(),
        istante: OffsetDateTime = adesso,
    ): Spesa {
        val spesa = Spesa(
            id = nuovoId(),
            istante = istante,
            categoria = categoria,
            importo = importo,
            modalita = modalita,
            descrizione = Csv.testo(descrizione).takeUnless { it.isEmpty() },
            valuta = valuta.trim().uppercase().ifEmpty { Spesa.EURO },
            cambio = cambio,
            tappa = dove(slug, posizione),
            scontrino = scontrino,
        )
        tabellaSpese(slug).accoda(
            mapOf(
                Csv.ID to spesa.id,
                Csv.TS to ts(adesso),
                SpeseTabella.ISTANTE to ts(istante),
                SpeseTabella.CATEGORIA to spesa.categoria.codice,
                SpeseTabella.DESCRIZIONE to Csv.testo(spesa.descrizione),
                SpeseTabella.IMPORTO to Csv.numero(spesa.importo),
                SpeseTabella.VALUTA to spesa.valuta,
                // Quattro decimali: un cambio a due arrotonderebbe di piu' di
                // quanto valga la spesa che sta convertendo.
                SpeseTabella.CAMBIO to (spesa.cambio?.let { Csv.numero(it, 4) } ?: ""),
                SpeseTabella.EURO to Csv.numero(spesa.euro),
                SpeseTabella.MODALITA to spesa.modalita.codice,
                SpeseTabella.TAPPA to Csv.testo(spesa.tappa),
                SpeseTabella.LAT to coordinata(posizione?.lat),
                SpeseTabella.LON to coordinata(posizione?.lon),
                SpeseTabella.SCONTRINO to Csv.testo(spesa.scontrino),
            ),
        )
        aggiornaDiario(slug, istante.toLocalDate())
        return spesa
    }

    // --- quando la scorta e' stata presa --------------------------------------

    /**
     * Quando i dintorni sono stati scaricati: il `ts` piu' recente fra le righe.
     *
     * Non serve una colonna nuova ne' un file di stato: **la data e' gia' nei
     * dati**, perche' ogni riga porta quando e' stata scritta. E' una proprieta'
     * del formato, e vale la pena usarla invece di duplicarla.
     */
    fun dintorniAggiornatiIl(slug: String): OffsetDateTime? =
        (tabellaPoi(slug).vive() + tabellaLuoghi(slug).vive())
            .mapNotNull { runCatching { OffsetDateTime.parse(it.ts) }.getOrNull() }
            .maxOrNull()

    /** Quando il meteo e' stato scaricato, secondo la scorta stessa. */
    fun meteoAggiornatoIl(slug: String): OffsetDateTime? = meteo(slug)?.istante

    // --- ritardi e anticipi ---------------------------------------------------

    /**
     * Sposta di [giorni] le tappe che restano dopo [da].
     *
     * Il gesto che rimedia a un ritardo: da un check-in fuori programma tutte le
     * date successive sono sbagliate, e con esse il riepilogo della sera e il
     * meteo di ogni tappa. La regola sta in [Slittamenti], qui c'e' solo la
     * scrittura.
     *
     * @return quante tappe sono state spostate.
     */
    fun slittaTappe(
        slug: String,
        da: Tappa,
        giorni: Long,
        oggi: LocalDate = LocalDate.now(),
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Int {
        val cambiate = Slittamenti.slitta(tappe(slug), da, giorni, oggi)
        if (cambiate.isEmpty()) return 0
        val ts = ts(adesso)
        tabellaTappe(slug).accodaTutte(cambiate.map { TappeTabella.riga(it, ts) })
        return cambiate.size
    }

    // --- correggere e cancellare ----------------------------------------------

    /*
     * Il formato e' stato scritto per questo dal primo giorno — `id`, `ts`,
     * `cancellato`, "vince l'ultima" — e per nove fasi nessuna schermata ha
     * saputo usarlo: un rifornimento col chilometraggio sbagliato si aggiustava
     * solo aprendo il CSV. Queste funzioni sono la parte mancante, e non
     * introducono nessun meccanismo nuovo.
     *
     * **Niente riscrive niente.** Correggere accoda una riga con lo stesso `id`,
     * cancellare accoda una lapide. Il file cresce, e va bene: e' il prezzo di
     * un archivio in cui una correzione non puo' distruggere l'originale, e
     * "compatta" lo rimette in ordine quando la vista da foglio di calcolo si fa
     * confusa.
     */

    /**
     * Cancella una voce accodando una lapide.
     *
     * @return `false` se quell'`id` non esiste piu': cancellare due volte non e'
     *   un errore da segnalare, ma non e' nemmeno un successo da annunciare.
     */
    fun cancellaVoce(
        slug: String,
        genere: Genere,
        id: String,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean {
        val tabella = tabellaDi(slug, genere)
        val riga = tabella.vive().firstOrNull { it.id == id } ?: return false
        val giorno = riga.quando?.toLocalDate()

        tabella.accoda(
            mapOf(
                Csv.ID to id,
                Csv.TS to ts(dopoDi(riga, adesso)),
                Csv.CANCELLATO to Csv.booleano(true),
            ),
        )

        giorno?.let { aggiornaDiario(slug, it) }
        return true
    }

    /** Corregge il testo di una nota. */
    fun correggiNota(
        slug: String,
        id: String,
        testo: String,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean {
        val pulito = Csv.testo(testo)
        if (pulito.isEmpty()) return false
        return correggi(slug, Genere.NOTA, id, adesso, mapOf(NoteTabella.TESTO to pulito))
    }

    /** Corregge la didascalia di una foto. La foto resta dov'e'. */
    fun correggiDidascalia(
        slug: String,
        id: String,
        didascalia: String?,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean = correggi(
        slug, Genere.FOTO, id, adesso,
        mapOf(FotoTabella.DIDASCALIA to Csv.testo(didascalia)),
    )

    /**
     * Corregge un rifornimento: il caso per cui tutto questo serve.
     *
     * I litri si riscrivono ricalcolandoli, come alla registrazione: la verita'
     * sono importo e prezzo, la colonna e' una comodita' per il foglio di
     * calcolo, e lasciarla al valore vecchio la renderebbe una bugia.
     */
    fun correggiRifornimento(
        slug: String,
        id: String,
        km: Int,
        euro: Double,
        prezzoLitro: Double,
        pieno: Boolean,
        istante: OffsetDateTime,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean {
        val litri = Carburante.litri(euro, prezzoLitro) ?: return false
        return correggi(
            slug, Genere.RIFORNIMENTO, id, adesso,
            mapOf(
                RifornimentiTabella.ISTANTE to ts(istante),
                RifornimentiTabella.KM to km.toString(),
                RifornimentiTabella.EURO to Csv.numero(euro),
                RifornimentiTabella.PREZZO_LITRO to Csv.numero(prezzoLitro, 3),
                RifornimentiTabella.LITRI to Csv.numero(litri, 2),
                RifornimentiTabella.PIENO to Csv.booleano(pieno),
            ),
        )
    }

    /** Corregge una spesa. Lo scontrino allegato resta quello. */
    fun correggiSpesa(
        slug: String,
        id: String,
        categoria: Categoria,
        importo: Double,
        modalita: Modalita,
        descrizione: String?,
        valuta: String,
        cambio: Double?,
        istante: OffsetDateTime,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean {
        val sigla = valuta.trim().uppercase().ifEmpty { Spesa.EURO }
        val estera = sigla != Spesa.EURO
        val euro = if (estera) importo * (cambio ?: 1.0) else importo
        return correggi(
            slug, Genere.SPESA, id, adesso,
            mapOf(
                SpeseTabella.ISTANTE to ts(istante),
                SpeseTabella.CATEGORIA to categoria.codice,
                SpeseTabella.DESCRIZIONE to Csv.testo(descrizione),
                SpeseTabella.IMPORTO to Csv.numero(importo),
                SpeseTabella.VALUTA to sigla,
                SpeseTabella.CAMBIO to (cambio?.takeIf { estera }?.let { Csv.numero(it, 4) } ?: ""),
                SpeseTabella.EURO to Csv.numero(euro),
                SpeseTabella.MODALITA to modalita.codice,
            ),
        )
    }

    /**
     * Il cuore di tutte le correzioni: **si parte dalla riga viva** e ci si
     * sovrascrive solo quello che e' cambiato.
     *
     * E' l'unico modo corretto, e la ragione sta in "vince l'ultima": la riga
     * nuova sostituisce la vecchia **per intero**, quindi accodarne una con soli
     * i campi corretti cancellerebbe tutto il resto — la tappa, le coordinate, lo
     * scontrino allegato. Partendo dalla riga esistente il chiamante puo'
     * elencare solo quello che gli interessa senza dover ricordare il resto.
     *
     * Il diario si rigenera per **due** giorni quando l'istante cambia: quello da
     * cui la voce esce e quello in cui entra. Rigenerarne uno solo lascerebbe la
     * voce scritta in due giornate.
     */
    private fun correggi(
        slug: String,
        genere: Genere,
        id: String,
        adesso: OffsetDateTime,
        cambi: Map<String, String>,
    ): Boolean {
        val tabella = tabellaDi(slug, genere)
        val riga = tabella.vive().firstOrNull { it.id == id } ?: return false
        val prima = riga.quando?.toLocalDate()

        tabella.accoda(riga.mappa() + cambi + mapOf(Csv.TS to ts(dopoDi(riga, adesso))))

        val dopo = tabella.vive().firstOrNull { it.id == id }?.quando?.toLocalDate()
        setOfNotNull(prima, dopo).forEach { aggiornaDiario(slug, it) }
        return true
    }

    /**
     * Un istante di scrittura **certamente successivo** a quello della riga che
     * si sta superando.
     *
     * Serve perche' "vince l'ultima" guarda il `ts`: una lapide con un `ts` piu'
     * vecchio della riga che dovrebbe uccidere viene scartata, e la cancellazione
     * non cancella **riferendo di essere riuscita**. E' un guasto silenzioso, ed
     * e' il genere che si scopre mesi dopo.
     *
     * Capita per davvero: l'orologio di un telefono torna indietro dopo una
     * sincronizzazione, e in viaggio si cambia fuso. Quando l'orologio e' avanti
     * — cioe' quasi sempre — questa funzione restituisce [adesso] e non tocca
     * niente; solo quando e' indietro sposta la scrittura di un millesimo oltre
     * la riga precedente. Non e' una data che qualcuno legge: `ts` dice *quando
     * la riga e' stata scritta*, e quando il fatto e' accaduto lo dice `istante`.
     */
    private fun dopoDi(riga: Riga, adesso: OffsetDateTime): OffsetDateTime {
        val precedente = runCatching { OffsetDateTime.parse(riga.ts) }.getOrNull() ?: return adesso
        return if (adesso.isAfter(precedente)) adesso else precedente.plusNanos(1_000_000)
    }

    private fun tabellaDi(slug: String, genere: Genere): Tabella = when (genere) {
        Genere.ARRIVO, Genere.POSIZIONE -> tabellaSpostamenti(slug)
        Genere.NOTA -> tabellaNote(slug)
        Genere.FOTO -> tabellaFoto(slug)
        Genere.RIFORNIMENTO -> tabellaRifornimenti(slug)
        Genere.SPESA -> tabellaSpese(slug)
    }

    /** La riga viva di una voce, per riempire la form di correzione. */
    fun voce(slug: String, genere: Genere, id: String): Riga? =
        tabellaDi(slug, genere).vive().firstOrNull { it.id == id }

    // --- consumi e autonomia -------------------------------------------------

    /**
     * I rifornimenti registrati.
     *
     * **I litri si ricalcolano** da importo e prezzo al litro, come gli euro di
     * una spesa in valuta estera: correggere il prezzo in un foglio di calcolo
     * aggiorna il consumo, e la cifra dello scontrino — l'unica verificabile —
     * resta intatta. La colonna `litri` vale da ripiego per le righe scritte
     * prima che il prezzo esistesse.
     */
    fun rifornimenti(slug: String): List<Rifornimento> = tabellaRifornimenti(slug)
        .vive()
        .mapNotNull { riga ->
            val id = riga.id ?: return@mapNotNull null
            val istante = riga.quando ?: return@mapNotNull null
            val km = riga.intero(RifornimentiTabella.KM) ?: return@mapNotNull null
            val euro = riga.numero(RifornimentiTabella.EURO)
            val prezzo = riga.numero(RifornimentiTabella.PREZZO_LITRO)
            val litri = Carburante.litri(euro, prezzo)
                ?: riga.numero(RifornimentiTabella.LITRI)
                ?: return@mapNotNull null
            Rifornimento(
                id = id,
                istante = istante,
                km = km,
                litri = litri,
                euro = euro,
                prezzoLitro = prezzo ?: Carburante.prezzo(euro, litri),
                pieno = riga.booleano(RifornimentiTabella.PIENO),
                luogo = riga.testo(RifornimentiTabella.LUOGO),
                lat = riga.numero(RifornimentiTabella.LAT),
                lon = riga.numero(RifornimentiTabella.LON),
            )
        }
        .sortedBy { it.istante }

    fun consumo(slug: String): Consumo = Consumi.calcola(rifornimenti(slug))

    // --- spese ---------------------------------------------------------------

    /**
     * Le spese registrate.
     *
     * Gli euro si **ricalcolano** da importo e cambio invece di leggere la
     * colonna `euro`: se qualcuno corregge il cambio in un foglio di calcolo,
     * il totale lo segue. La colonna resta per chi legge il file, non per
     * l'app.
     */
    fun spese(slug: String): List<Spesa> = tabellaSpese(slug)
        .vive()
        .mapNotNull { riga ->
            val id = riga.id ?: return@mapNotNull null
            val istante = riga.quando ?: return@mapNotNull null
            val importo = riga.numero(SpeseTabella.IMPORTO) ?: return@mapNotNull null
            Spesa(
                id = id,
                istante = istante,
                categoria = Categoria.da(riga.testo(SpeseTabella.CATEGORIA)),
                importo = importo,
                modalita = Modalita.da(riga.testo(SpeseTabella.MODALITA)),
                descrizione = riga.testo(SpeseTabella.DESCRIZIONE),
                valuta = riga.testo(SpeseTabella.VALUTA)?.uppercase() ?: Spesa.EURO,
                cambio = riga.numero(SpeseTabella.CAMBIO),
                tappa = riga.testo(SpeseTabella.TAPPA),
                scontrino = riga.testo(SpeseTabella.SCONTRINO),
            )
        }
        .sortedBy { it.istante }

    /**
     * Il conto del viaggio: le spese piu' il carburante.
     *
     * Il carburante arriva dai rifornimenti e non dalle spese, perche' e' li'
     * che lo si registra. E' l'unico punto in cui le due tabelle si toccano.
     */
    fun conto(slug: String): Conto {
        val rifornimenti = rifornimenti(slug)
        return Spese.conta(
            spese = spese(slug),
            carburante = rifornimenti.sumOf { it.euro ?: 0.0 },
            giorniDelCarburante = rifornimenti
                .filter { it.euro != null }
                .map { it.istante.toLocalDate() },
        )
    }

    /**
     * Tutti i punti con coordinate registrati nel viaggio: check-in, posizioni,
     * note, foto, rifornimenti.
     *
     * E' la base della stima dell'autonomia, e usarli **tutti** e non solo i
     * check-in e' la differenza fra vedere una gita fuori itinerario e non
     * vederla.
     */
    fun punti(slug: String): List<Punto> = buildList {
        fun raccogli(righe: List<Riga>, colonnaLat: String, colonnaLon: String) {
            righe.forEach { riga ->
                val istante = runCatching { OffsetDateTime.parse(riga.ts) }.getOrNull()
                    ?: return@forEach
                val lat = riga.numero(colonnaLat) ?: return@forEach
                val lon = riga.numero(colonnaLon) ?: return@forEach
                add(Punto(istante, lat, lon))
            }
        }
        raccogli(tabellaSpostamenti(slug).vive(), SpostamentiTabella.LAT, SpostamentiTabella.LON)
        raccogli(tabellaNote(slug).vive(), NoteTabella.LAT, NoteTabella.LON)
        raccogli(tabellaFoto(slug).vive(), FotoTabella.LAT, FotoTabella.LON)
        raccogli(tabellaRifornimenti(slug).vive(), RifornimentiTabella.LAT, RifornimentiTabella.LON)
        raccogli(tabellaSpese(slug).vive(), SpeseTabella.LAT, SpeseTabella.LON)
    }.sortedBy { it.istante }

    // --- i dossier: le risposte del modello -----------------------------------

    fun tabellaDossier(slug: String): Tabella =
        Tabella(File(cartellaViaggio(slug), DossierTabella.NOME_FILE), DossierTabella.COLONNE)

    fun cartellaDossier(slug: String): File =
        File(cartellaViaggio(slug), DossierTabella.CARTELLA).apply { mkdirs() }

    /**
     * Salva una risposta del modello: il testo in un `.md`, una riga d'indice
     * nel CSV.
     *
     * **Il dossier e' la ragione per cui questa funzione esiste.** Una risposta
     * letta e chiusa e' persa; scritta su file si ritrova arrivando sul posto,
     * tre giorni dopo, senza campo. E' il pezzo che rende utile una funzione
     * altrimenti solo online.
     */
    /**
     * @param tappa a quale tappa attribuire la risposta. Di riposo e' dove eri
     *   quando hai chiesto, che e' giusto per una domanda fatta in Esplora; una
     *   domanda fatta **su** una tappa la passa esplicitamente, perche' e' con
     *   quel nome che la si ritrova nella scheda di quella tappa — e chiedere di
     *   Bolsena stando a Orvieto e' il caso normale, non l'eccezione.
     */
    fun salvaDossier(
        slug: String,
        domanda: String,
        contesto: String,
        risposta: RispostaModello,
        posizione: Posizione? = null,
        adesso: OffsetDateTime = OffsetDateTime.now(),
        tappa: String? = null,
    ): String {
        val nome = Esplora.nomeFile(adesso, domanda)
        File(cartellaDossier(slug), nome)
            .writeText(Esplora.dossier(domanda, contesto, risposta, adesso), Charsets.UTF_8)

        tabellaDossier(slug).accoda(
            mapOf(
                Csv.ID to nuovoId(),
                Csv.TS to ts(adesso),
                DossierTabella.ISTANTE to ts(adesso),
                DossierTabella.DOMANDA to Csv.testo(domanda),
                DossierTabella.TAPPA to Csv.testo(tappa ?: dove(slug, posizione)),
                DossierTabella.MODELLO to risposta.modello.codice,
                DossierTabella.FILE to nome,
            ),
        )
        return nome
    }

    /** I dossier salvati, dal piu' recente. */
    fun dossier(slug: String): List<Dossier> = tabellaDossier(slug)
        .vive()
        .mapNotNull { riga ->
            val nome = riga.testo(DossierTabella.FILE) ?: return@mapNotNull null
            Dossier(
                id = riga.id ?: return@mapNotNull null,
                istante = riga.quando ?: return@mapNotNull null,
                domanda = riga.testo(DossierTabella.DOMANDA) ?: "",
                tappa = riga.testo(DossierTabella.TAPPA),
                modello = riga.testo(DossierTabella.MODELLO),
                file = nome,
            )
        }
        .sortedByDescending { it.istante }

    /** Il testo di un dossier, o `null` se il file non c'e' piu'. */
    fun testoDossier(slug: String, nome: String): String? {
        val file = File(cartellaDossier(slug), nome)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    // --- briefing serale -----------------------------------------------------

    /**
     * Il riepilogo della sera per un viaggio.
     *
     * Il punto di partenza dei chilometri di domani e' **l'ultima posizione
     * registrata**, non la tappa corrente: se oggi ti sei spostato di
     * cinquanta chilometri fuori itinerario, domani parti da li'.
     */
    fun briefing(
        slug: String,
        oggi: LocalDate = LocalDate.now(),
        adesso: OffsetDateTime = OffsetDateTime.now(),
        kmConUnPieno: Int? = impostazioni().kmConUnPieno,
    ): Briefing {
        val punti = punti(slug)
        val tappe = tappe(slug)
        return Briefings.componi(
            tappe = tappe,
            oggi = oggi,
            autonomia = StimaAutonomia.calcola(
                kmConUnPieno = kmConUnPieno,
                rifornimenti = rifornimenti(slug),
                punti = punti,
            ),
            da = punti.lastOrNull()?.let { Coordinate(it.lat, it.lon) }
                ?: Tappe.corrente(tappe)?.let { Coordinate(it.lat, it.lon) },
            tratte = tratte(slug).takeUnless { it.vuoto },
            meteo = meteo(slug),
            adesso = adesso,
        )
    }

    /**
     * Il briefing del viaggio piu' recente, o `null` se non ce n'e' nessuno.
     *
     * Il viaggio in corso e' l'ultimo creato: non c'e' un flag "attivo" da
     * tenere aggiornato, e non serve — chi apre un viaggio nuovo sta partendo.
     */
    fun briefingCorrente(
        oggi: LocalDate = LocalDate.now(),
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Briefing? {
        val viaggio = viaggi().firstOrNull() ?: return null
        return briefing(viaggio.slug, oggi, adesso)
    }

    /** Lo slug del viaggio in corso: l'ultimo creato. */
    fun slugCorrente(): String? = viaggi().firstOrNull()?.slug

    // --- impostazioni --------------------------------------------------------

    fun impostazioni(): Impostazioni {
        val file = File(radice, NOME_IMPOSTAZIONI)
        if (!file.exists()) return Impostazioni()
        return runCatching { json.decodeFromString<Impostazioni>(file.readText(Charsets.UTF_8)) }
            .getOrDefault(Impostazioni())
    }

    /**
     * Le impostazioni scritte in un testo JSON, o `null` se non si leggono.
     *
     * Serve alla fusione, che le riceve da un albero SAF come stringa e non come
     * file: il decodificatore sta qui perche' e' qui che vive `json`, e perche'
     * la tolleranza — un file scritto da una versione piu' vecchia si legge
     * comunque — e' una regola dell'archivio.
     */
    fun leggiImpostazioni(testo: String): Impostazioni? =
        runCatching { json.decodeFromString<Impostazioni>(testo) }.getOrNull()

    fun salvaImpostazioni(impostazioni: Impostazioni) {
        radice.mkdirs()
        File(radice, NOME_IMPOSTAZIONI).writeText(json.encodeToString(impostazioni), Charsets.UTF_8)
    }

    // --- diario --------------------------------------------------------------

    /** Tutte le voci del viaggio, in ordine di ora. */
    fun voci(slug: String): List<Voce> = VociDelGiorno.tutte(
        spostamenti = tabellaSpostamenti(slug).vive(),
        note = tabellaNote(slug).vive(),
        foto = tabellaFoto(slug).vive(),
        rifornimenti = tabellaRifornimenti(slug).vive(),
        spese = tabellaSpese(slug).vive(),
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

    /**
     * Sostituisce la sezione di un giorno con la prosa scritta dal modello.
     *
     * **Gli eventi restano nei CSV.** Questo tocca solo `diario.md`, che e' una
     * vista: [rigeneraDiario] riporta tutto a cronaca, ed e' precisamente il
     * motivo per cui quella funzione esiste da prima che servisse. Una prosa che
     * non piace si butta senza perdere niente.
     */
    fun scriviProsa(slug: String, giorno: LocalDate, prosa: String) {
        val tutte = voci(slug)
        diario(slug).scriviProsa(
            giorno = giorno,
            prosa = prosa,
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

    /**
     * Dove sei secondo l'**itinerario**: il nome dell'ultima tappa spuntata.
     *
     * E' il ripiego di [dove], non il primo posto dove guardare: dice dove hai
     * fatto l'ultimo check-in, che non e' necessariamente dove sei.
     */
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
            appendLine("| `istante` | Quando e' **accaduto** il fatto, dove non coincide con `ts`: spese e rifornimenti si possono registrare col giorno che scegli tu. Se la colonna manca vale `ts` |")
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
            appendLine("| `descrizione` | Il testo dell'itinerario. I capoversi si scrivono `\\\\n`, cosi' un paragrafo sta su una riga fisica sola senza perdere la struttura |")
            appendLine("| `altro` | **Tutti i campi che l'itinerario portava e per cui non c'e' una colonna**: orari, telefono, quota, un link. In JSON compatto, nell'ordine in cui erano scritti. Senza questa colonna finirebbero nel nulla |")
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
            appendLine("## rifornimenti.csv")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `km` | Il contachilometri al rifornimento |")
            appendLine("| `euro` | Importo speso: e' il dato primario, quello dello scontrino |")
            appendLine("| `prezzo_litro` | Il prezzo al litro del cartello, tre decimali |")
            appendLine("| `litri` | `euro` diviso `prezzo_litro`. E' una comodita' per il foglio di calcolo: **la verita' sono `euro` e `prezzo_litro`**, e l'app rifa' il conto ogni volta che legge |")
            appendLine("| `pieno` | `si` se il serbatoio e' stato riempito. Solo fra due pieni il consumo e' calcolabile |")
            appendLine("| `luogo` | Dove eri, secondo l'itinerario |")
            appendLine("| `lat`, `lon` | Coordinate, se il GPS le aveva |")
            appendLine()
            appendLine("## spese.csv")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `categoria` | `sosta`, `pedaggi`, `spesa`, `ristorante`, `visite`, `trasporti`, `mezzo`, `altro` |")
            appendLine("| `descrizione` | Testo libero, su una riga sola |")
            appendLine("| `importo` | Quanto c'era scritto sullo scontrino, nella sua valuta |")
            appendLine("| `valuta` | Sigla a tre lettere. `EUR` se non e' scritto niente |")
            appendLine("| `cambio` | Quanti euro vale un'unita' di `valuta`, al momento della spesa. Vuoto per l'euro |")
            appendLine("| `euro` | `importo` per `cambio`. E' una comodita' per il foglio di calcolo: **la verita' sono `importo` e `cambio`**, e l'app rifa' il conto ogni volta che legge |")
            appendLine("| `modalita` | `contanti`, `pos` oppure `carta` |")
            appendLine("| `tappa` | Dove eri, secondo l'itinerario |")
            appendLine("| `lat`, `lon` | Coordinate, se il GPS le aveva |")
            appendLine("| `scontrino` | Nome del file nella sottocartella `scontrini/` |")
            appendLine()
            appendLine("Il carburante non sta qui: sta in `rifornimenti.csv`, che ne chiede gia'")
            appendLine("l'importo. Il conto di fine viaggio somma le due tabelle, e tenerle")
            appendLine("separate e' l'unico modo perche' non conti due volte lo stesso pieno.")
            appendLine()
            appendLine("## scorta/tratte.csv")
            appendLine()
            appendLine("Le distanze **su strada** fra tappe consecutive, prese da OSRM quando")
            appendLine("c'era rete. Non e' un registro di quello che hai fatto: e' una scorta,")
            appendLine("e si puo' cancellare senza perdere niente — l'app ripiega sulla linea")
            appendLine("d'aria dichiarandolo.")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `da`, `a` | Nomi delle tappe, per chi legge il file |")
            appendLine("| `da_lat`, `da_lon`, `a_lat`, `a_lon` | I due capi. Sono questi a identificare la tratta, non i nomi |")
            appendLine("| `km` | Chilometri su strada |")
            appendLine("| `minuti` | Tempo di guida stimato |")
            appendLine()
            appendLine("## scorta/poi.csv")
            appendLine()
            appendLine("I punti di interesse lungo l'itinerario, presi da OpenStreetMap quando")
            appendLine("c'era rete. Sette categorie e niente altro: quello che un camper cerca.")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `id` | L'identificativo OpenStreetMap, `node/123456`: riscaricare aggiorna invece di duplicare |")
            appendLine("| `nome` | Come si chiama, quando ha un nome |")
            appendLine("| `categoria` | `sosta`, `campeggio`, `servizio`, `acqua`, `carburante`, `spesa`, `attrazione` |")
            appendLine("| `lat`, `lon` | Il punto, o il centro se in OSM e' un poligono |")
            appendLine("| `dettaglio` | Quello che cambia la decisione: il gestore, se e' a pagamento, se e' sempre aperto |")
            appendLine()
            appendLine("## scorta/luoghi.csv")
            appendLine()
            appendLine("I nomi dei posti abitati lungo l'itinerario. Servono a dire dove sei")
            appendLine("**senza rete**: e' con questi che una foto si chiama `_Bolsena` invece di")
            appendLine("portare il nome dell'ultimo check-in.")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `nome` | Il toponimo |")
            appendLine("| `lat`, `lon` | Il centro abitato |")
            appendLine("| `abitanti` | Serve a scegliere fra due nomi ugualmente vicini: il paese vince sulla frazione |")
            appendLine()
            appendLine("## dossier.csv")
            appendLine()
            appendLine("L'indice delle risposte del modello. Il testo sta nei `.md` dentro")
            appendLine("`dossier/`: una risposta e' mezza pagina di prosa, e dentro una cella di")
            appendLine("foglio di calcolo non ci si legge. Sono pagine da rileggere, non un")
            appendLine("registro: cancellarle non perde nessun fatto del viaggio.")
            appendLine()
            appendLine("| Colonna | Significato |")
            appendLine("|---|---|")
            appendLine("| `domanda` | Quello che hai chiesto |")
            appendLine("| `tappa` | Dove eri quando l'hai chiesto |")
            appendLine("| `modello` | Chi ha risposto: `gemini` oppure `grok` |")
            appendLine("| `file` | Il nome del file in `dossier/`. Dentro ci sono anche le fonti e il contesto che l'app aveva passato al modello |")
            appendLine()
            appendLine("## scorta/meteo.json")
            appendLine()
            appendLine("Le previsioni scaricate da Open-Meteo, con `scaricatoIl` che ne dice")
            appendLine("l'eta'. Oltre tre giorni non si mostrano piu': una previsione vecchia")
            appendLine("non e' un dato vecchio, e' un dato sbagliato.")
            appendLine()
            appendLine("## impostazioni.json")
            appendLine()
            appendLine("| Chiave | Significato |")
            appendLine("|---|---|")
            appendLine("| `kmConUnPieno` | Quanti chilometri fa il mezzo con un serbatoio pieno. Serve alla stima dell'autonomia |")
            appendLine("| `briefingAttivo` | Se il riepilogo della sera deve arrivare |")
            appendLine("| `oraBriefing` | L'ora del riepilogo, 0-23. Di riposo le 19 |")
            appendLine("| `cartellaSpecchio` | L'Uri della cartella in cui l'app ricopia l'archivio. Il permesso su quella cartella non e' qui: vive nell'installazione, e dopo una reinstallazione la cartella va riscelta |")
            appendLine("| `principale` | Quale modello si prova per primo: `gemini` oppure `grok`. L'altro fa da riserva |")
            appendLine("| `modelloGemini`, `modelloGrok` | Gli identificativi dei modelli. Sono qui e non compilati dentro perche' i nomi vengono ritirati ogni pochi mesi: si correggono leggendo l'errore che il servizio ha restituito |")
            appendLine("| `promptEsplora` | Il prompt di sistema di Esplora. Vuoto vuol dire «usa quello di serie» |")
            appendLine()
            appendLine("Le chiavi API non stanno qui: vivono nell'archivio cifrato dell'app.")
            appendLine()
            appendLine("## diario.md")
            appendLine()
            appendLine("Non e' una tabella: e' il diario del viaggio, una sezione per giorno.")
            appendLine("L'intestazione porta la data in forma ISO, cosi' la sezione di un giorno")
            appendLine("si ritrova per riscriverla. E' una vista degli eventi delle tabelle: se")
            appendLine("si perde, l'app la rigenera.")
            appendLine()
            appendLine("## Dove sono questi file")
            appendLine()
            appendLine("La copia di lavoro sta nell'area privata dell'app, dove funziona sempre e")
            appendLine("nessun gestore file arriva. Se dalle impostazioni scegli una cartella,")
            appendLine("l'app ci **ricopia** tutto: quella e' la copia che puoi aprire, spostare")
            appendLine("e sincronizzare su un cloud.")
            appendLine()
            appendLine("Normalmente la copia va in un verso solo, da dentro a fuori: modificare")
            appendLine("questi file non cambia niente dentro l'app. C'e' **una** eccezione, e si")
            appendLine("chiede a mano: assegnare la cartella, o \"Sincronizza\" nelle impostazioni,")
            appendLine("legge quello che c'e' qui e lo fa entrare. Serve dopo una reinstallazione o")
            appendLine("venendo da un altro telefono, e segue le regole del formato: per ogni `id`")
            appendLine("vince la riga col `ts` piu' recente, le righe cancellate restano")
            appendLine("cancellate, e le foto che l'app ha gia' non vengono sovrascritte.")
        }
        File(radice, "FORMATI.md").writeText(testo, Charsets.UTF_8)
    }

    companion object {
        const val NOME_CARTELLA = "MyaCamperLife"
        private const val NOME_VIAGGIO = "viaggio.json"
        private const val NOME_IMPOSTAZIONI = "impostazioni.json"

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
