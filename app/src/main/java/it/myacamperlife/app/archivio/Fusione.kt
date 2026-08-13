package it.myacamperlife.app.archivio

import java.io.File

/** Com'e' andata una fusione: cosa e' entrato, e cosa non si e' potuto leggere. */
data class EsitoFusione(
    /** Viaggi che nell'app non c'erano affatto. */
    val viaggiNuovi: Int = 0,
    /** Viaggi presenti in entrambe le copie, le cui righe sono state unite. */
    val viaggiFusi: Int = 0,
    /** Righe entrate dalla cartella e non presenti nell'app. */
    val righeNuove: Int = 0,
    /** Foto, scontrini e dossier copiati dentro. */
    val allegati: Int = 0,
    /** Vero se le impostazioni sono state prese dalla cartella. */
    val impostazioni: Boolean = false,
    /** File che non si e' riusciti a leggere. */
    val falliti: Int = 0,
) {
    val qualcosa: Boolean
        get() = viaggiNuovi > 0 || righeNuove > 0 || allegati > 0 || impostazioni
}

/**
 * Unisce l'archivio di una cartella con quello dell'app.
 *
 * **Perche' esiste.** Fino a ieri assegnare una cartella copiava solo verso
 * fuori: dopo una reinstallazione l'app ripartiva vuota e il proprio archivio,
 * ancora tutto nella cartella, restava invisibile — e le impostazioni nella
 * cartella venivano perfino sovrascritte con quelle di riposo. Questo e' il
 * verso mancante, e si percorre **una volta**, quando si assegna la cartella o
 * quando lo si chiede.
 *
 * **Non e' codice di sincronizzazione.** E' la promessa che il formato fa dal
 * primo giorno: le tabelle sono fondibili per costruzione, quindi unire due
 * copie e' concatenare le righe e tenere l'ultima versione di ogni `id`. Il
 * lavoro vero e' tutto in [Tabella.fondi], che sono quindici righe; qui c'e'
 * l'inventario di cosa fondere e con quale regola, che e' diversa per genere di
 * file:
 *
 * | Cosa | Regola | Perche' |
 * |---|---|---|
 * | Tabelle CSV | si fondono per `id` | e' quello per cui il formato e' stato scelto |
 * | Foto, scontrini, dossier | si copiano **solo se mancano** | non cambiano mai, e sovrascriverli e' solo un rischio |
 * | `viaggio.json` | si copia se manca | e' l'anagrafica del viaggio, non cambia |
 * | `impostazioni.json` | si prende **solo se l'app ha ancora quelle di riposo** | l'unico caso in cui e' certo che quelle di fuori valgono di piu' |
 * | `diario.md` | si ignora e si rigenera | e' una vista: fonderla non avrebbe senso |
 * | `FORMATI.md` | si ignora | e' generato |
 * | i CSV sotto `scorta` | si fondono come le altre tabelle | male che vada si riscarica |
 *
 * **L'app resta l'autorita'.** Si legge da fuori, si scrive dentro, e poi lo
 * specchio riporta fuori il risultato: dopo questa passata il verso torna quello
 * di sempre.
 */
class Fusione(private val archivio: Archivio) {

    /**
     * Fonde [da] nell'archivio.
     *
     * Non cancella niente, mai: nell'app come nella cartella. Una fusione che
     * cancella e' una fusione che, puntata sulla cartella sbagliata, perde
     * quello che non si puo' rifare.
     */
    fun fondi(da: Albero): EsitoFusione {
        val percorsi = da.elenca()
        if (percorsi.isEmpty()) return EsitoFusione()

        var esito = fondiImpostazioni(da, percorsi)

        // Gli slug si ricavano dai percorsi e non da un elenco dichiarato: quello
        // che c'e' nella cartella e' l'unica cosa che sappiamo di lei.
        val slug = percorsi
            .mapNotNull { slugDi(it) }
            .distinct()
            .sorted()

        slug.forEach { s ->
            val nuovo = archivio.leggiViaggio(s) == null
            val fatto = fondiViaggio(da, percorsi, s)
            esito = esito.copy(
                viaggiNuovi = esito.viaggiNuovi + if (nuovo) 1 else 0,
                viaggiFusi = esito.viaggiFusi + if (nuovo) 0 else 1,
                righeNuove = esito.righeNuove + fatto.righeNuove,
                allegati = esito.allegati + fatto.allegati,
                falliti = esito.falliti + fatto.falliti,
            )
            // Il diario si rigenera dalle tabelle fuse: e' una vista, e dopo una
            // fusione la vista di prima e' quella sbagliata.
            archivio.rigeneraDiario(s)
        }

        return esito
    }

    // --- un viaggio -----------------------------------------------------------

    private fun fondiViaggio(da: Albero, percorsi: List<String>, slug: String): EsitoFusione {
        var righeNuove = 0
        var allegati = 0
        var falliti = 0

        val dentro = "${CARTELLA_VIAGGI}${Albero.SEPARATORE}$slug${Albero.SEPARATORE}"
        val suoi = percorsi.filter { it.startsWith(dentro) }

        suoi.forEach { percorso ->
            val relativo = percorso.removePrefix(dentro)
            when {
                // Un CSV si fonde, dovunque stia: vale per quelli del viaggio e
                // per quelli sotto `scorta/`, che hanno la stessa forma.
                relativo.endsWith(".csv") -> {
                    val tabella = tabellaPer(slug, relativo)
                    if (tabella == null) {
                        falliti++
                    } else {
                        val letto = da.testo(percorso)
                        if (letto == null) falliti++ else righeNuove += fondiTabella(tabella, letto)
                    }
                }

                relativo == NOME_VIAGGIO -> {
                    val file = File(archivio.cartellaViaggio(slug), NOME_VIAGGIO)
                    if (!file.exists()) {
                        file.parentFile?.mkdirs()
                        if (da.copia(percorso, file)) allegati++ else falliti++
                    }
                }

                // Le viste e i generati non si fondono.
                relativo == NOME_DIARIO -> Unit

                // Tutto il resto e' un allegato: foto, scontrini, dossier, la
                // scorta meteo. Si copia **solo se manca**, mai sopra.
                else -> {
                    val file = File(archivio.cartellaViaggio(slug), relativo)
                    if (!file.exists()) {
                        file.parentFile?.mkdirs()
                        if (da.copia(percorso, file)) allegati++ else falliti++
                    }
                }
            }
        }

        return EsitoFusione(righeNuove = righeNuove, allegati = allegati, falliti = falliti)
    }

    /**
     * Fonde una tabella e dice **quante righe sono entrate**.
     *
     * Il conteggio e' la differenza fra le righe risolte prima e dopo: e' quello
     * che si puo' dire con onesta' all'utente, e non "ho unito due file" che non
     * significa niente per chi legge.
     *
     * La copia dell'app si passa **per seconda**: a pari `ts` vince chi arriva
     * dopo, e fra due righe scritte nello stesso istante ha piu' senso fidarsi di
     * quella che l'app ha davanti.
     */
    private fun fondiTabella(tabella: Tabella, testoDiFuori: String): Int {
        val nostre = tabella.righe()
        val loro = Tabella.righeDa(testoDiFuori)
        if (loro.isEmpty()) return 0

        val fuse = Tabella.fondi(loro, nostre)
        // Niente da scrivere se la fusione non aggiunge e non cambia niente:
        // riscrivere per nulla vorrebbe dire una copia in piu' sul cloud.
        val prima = nostre.mapNotNull { it.id }.toSet()
        val entrate = fuse.count { it.id != null && it.id !in prima }
        if (fuse.size == nostre.size && entrate == 0 && !cambiate(nostre, fuse)) return 0

        tabella.riscrivi(fuse)
        return entrate
    }

    /** Se qualche riga risulta sostituita da una versione piu' recente. */
    private fun cambiate(nostre: List<Riga>, fuse: List<Riga>): Boolean {
        val nostreVive = Tabella.risolvi(nostre).associateBy { it.id }
        return fuse.any { riga ->
            val id = riga.id ?: return@any false
            val nostra = nostreVive[id] ?: return@any true
            nostra.mappa() != riga.mappa()
        }
    }

    private fun tabellaPer(slug: String, relativo: String): Tabella? = when (relativo) {
        TappeTabella.NOME_FILE -> archivio.tabellaTappe(slug)
        SpostamentiTabella.NOME_FILE -> archivio.tabellaSpostamenti(slug)
        NoteTabella.NOME_FILE -> archivio.tabellaNote(slug)
        FotoTabella.NOME_FILE -> archivio.tabellaFoto(slug)
        RifornimentiTabella.NOME_FILE -> archivio.tabellaRifornimenti(slug)
        SpeseTabella.NOME_FILE -> archivio.tabellaSpese(slug)
        DossierTabella.NOME_FILE -> archivio.tabellaDossier(slug)
        scorta(TratteTabella.NOME_FILE) -> archivio.tabellaTratte(slug)
        scorta(PoiTabella.NOME_FILE) -> archivio.tabellaPoi(slug)
        scorta(LuoghiTabella.NOME_FILE) -> archivio.tabellaLuoghi(slug)
        // Un CSV che questa versione non conosce: **non lo si tocca**. Potrebbe
        // venire da una versione piu' nuova dell'app, e fonderlo senza sapere
        // quali colonne abbia significherebbe rovinarlo.
        else -> null
    }

    private fun scorta(nome: String): String = "${TratteTabella.CARTELLA}${Albero.SEPARATORE}$nome"

    // --- le impostazioni ------------------------------------------------------

    /**
     * Prende le impostazioni dalla cartella **solo se l'app ha ancora quelle di
     * riposo**.
     *
     * E' l'unico caso in cui si puo' esserne certi: se l'utente non ha ancora
     * toccato niente, qualunque cosa ci sia fuori vale di piu' del nulla. Se
     * invece ha gia' impostato i km con un pieno, sovrascriverli con quelli di
     * un'altra copia sarebbe decidere al posto suo — e sceglierebbe male tanto
     * quanto sceglieva male il codice di prima, che li cancellava.
     *
     * La cartella scelta **non** si prende da quelle di fuori: quell'Uri e' di
     * un'installazione che non c'e' piu', e il permesso su di esso e' perduto.
     */
    private fun fondiImpostazioni(da: Albero, percorsi: List<String>): EsitoFusione {
        if (NOME_IMPOSTAZIONI !in percorsi) return EsitoFusione()
        val nostre = archivio.impostazioni()

        // "Intatte" si giudica **ignorando i campi che non sono preferenze**: la
        // cartella l'utente l'ha appena scelta, la data di sincronizzazione la
        // scrive questa stessa operazione, e l'esito dell'ultima ricerca dei
        // dintorni e' una traccia di diagnostica. Contarli renderebbe l'archivio
        // "gia' toccato" sempre, e la fusione delle impostazioni non scatterebbe
        // mai — che e' il difetto peggiore, perche' silenzioso.
        val intatte = nostre.copy(
            cartellaSpecchio = null,
            sincronizzatoIl = null,
            dintorniEsito = null,
            dintorniProvatoIl = null,
            importEsito = null,
            importProvatoIl = null,
        )
        if (intatte != Impostazioni()) return EsitoFusione()

        val testo = da.testo(NOME_IMPOSTAZIONI) ?: return EsitoFusione(falliti = 1)
        val loro = archivio.leggiImpostazioni(testo) ?: return EsitoFusione(falliti = 1)

        archivio.salvaImpostazioni(
            loro.copy(
                cartellaSpecchio = nostre.cartellaSpecchio,
                sincronizzatoIl = nostre.sincronizzatoIl,
                dintorniEsito = nostre.dintorniEsito,
                dintorniProvatoIl = nostre.dintorniProvatoIl,
                importEsito = nostre.importEsito,
                importProvatoIl = nostre.importProvatoIl,
            ),
        )
        return EsitoFusione(impostazioni = true)
    }

    /** Lo slug del viaggio a cui un percorso appartiene, se ne appartiene a uno. */
    private fun slugDi(percorso: String): String? {
        val pezzi = percorso.split(Albero.SEPARATORE)
        if (pezzi.size < 3 || pezzi[0] != CARTELLA_VIAGGI) return null
        return pezzi[1].takeUnless { it.isEmpty() }
    }

    private companion object {
        const val CARTELLA_VIAGGI = "viaggi"
        const val NOME_VIAGGIO = "viaggio.json"
        const val NOME_DIARIO = "diario.md"
        const val NOME_IMPOSTAZIONI = "impostazioni.json"
    }
}
