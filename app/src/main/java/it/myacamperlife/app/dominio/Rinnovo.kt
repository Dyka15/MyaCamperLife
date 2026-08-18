package it.myacamperlife.app.dominio

/**
 * Cosa cambia quando un itinerario nuovo sostituisce la parte di viaggio che
 * resta da fare.
 *
 * @param tenute le tappe che restano, col numero d'ordine che avranno. Sono
 *   quelle su cui hai gia' deciso qualcosa: le fatte e le saltate.
 * @param rinumerate il sottoinsieme di [tenute] che ha cambiato numero d'ordine
 *   e quindi va riscritto. Le altre non si toccano: riscrivere una riga
 *   identica gonfia il file e non aggiunge niente.
 * @param sostituite le tappe che spariscono dall'itinerario, da coprire con una
 *   lapide. **Non si cancellano dal file**: la riga di prima resta scritta.
 * @param nuove le tappe del documento nuovo, numerate dopo le tenute.
 */
data class Rinnovo(
    val tenute: List<Tappa>,
    val rinumerate: List<Tappa>,
    val sostituite: List<Tappa>,
    val nuove: List<Tappa>,
) {
    /** Vero quando non c'e' niente da fare: nessuna tappa nuova da mettere. */
    val vuoto: Boolean get() = nuove.isEmpty()
}

/**
 * Riscrivere la parte di viaggio che non hai ancora fatto.
 *
 * **Il caso vero**: sei al 13 agosto, i piani per i dieci giorni che restano
 * sono cambiati, e hai un file nuovo che copre dal 13 al 23. Non e' un viaggio
 * nuovo — la spesa di ieri, le foto, i chilometri e il diario sono di questo — e
 * non e' un ritocco a mano, perche' sono dieci tappe.
 *
 * **La regola sta in una frase: le tappe fatte sono storia, quelle da fare sono
 * ipotesi.** Le prime restano, le seconde si sostituiscono. Le saltate restano
 * anche loro: saltare e' una decisione che hai preso, non un'ipotesi, e
 * cancellarla vorrebbe dire dimenticare una scelta.
 *
 * Niente di registrato viene toccato: gli eventi — check-in, note, foto,
 * rifornimenti, spese, posizioni — vivono in altre tabelle e non hanno nulla da
 * cambiare. Un check-in dice «sono arrivato a Rothenburg il 12 agosto» e resta
 * vero qualunque cosa dica l'itinerario di domani.
 *
 * Funzione pura: prende le tappe e i waypoint, restituisce cosa scrivere.
 */
object Rinnovi {

    /**
     * @param id come si generano gli identificativi delle tappe nuove. Lo passa
     *   chi chiama perche' un identificativo casuale non e' una funzione pura, e
     *   una prova deve poter chiedere id prevedibili.
     */
    fun componi(tappe: List<Tappa>, punti: List<Waypoint>, id: () -> String): Rinnovo {
        val ordinate = tappe.sortedBy { it.ordine }
        val restano = ordinate.filter { it.stato != StatoTappa.DA_FARE }
        val vanno = ordinate.filter { it.stato == StatoTappa.DA_FARE }

        // I numeri si rifanno densi da 1: tolte le tappe da fare in mezzo alle
        // altre restano dei buchi, e sull'ordine si legge l'itinerario.
        val tenute = restano.mapIndexed { indice, tappa -> tappa.copy(ordine = indice + 1) }

        val nuove = punti.mapIndexed { indice, punto ->
            Tappa(
                id = id(),
                ordine = tenute.size + indice + 1,
                nome = punto.nome,
                lat = punto.lat,
                lon = punto.lon,
                tipo = punto.tipo,
                giorno = punto.giorno,
                descrizione = punto.descrizione,
                altro = punto.altro,
                origine = OrigineTappa.ITINERARIO,
            )
        }

        return Rinnovo(
            tenute = tenute,
            // Solo quelle che hanno davvero cambiato numero: la riga di una
            // tappa che resta dov'era non ha motivo di essere riscritta.
            rinumerate = tenute.filter { tenuta ->
                ordinate.first { it.id == tenuta.id }.ordine != tenuta.ordine
            },
            sostituite = vanno,
            nuove = nuove,
        )
    }
}
