package it.myacamperlife.app.dominio

/**
 * Una coppia di coordinate, senza l'ora: qui l'ora non serve.
 */
data class Coordinate(val lat: Double, val lon: Double) {

    val valide: Boolean get() = lat in -90.0..90.0 && lon in -180.0..180.0

    /** Come si scrivono in un campo: `42.718500, 12.111200`. */
    override fun toString(): String =
        String.format(java.util.Locale.ROOT, "%.6f, %.6f", lat, lon)

    companion object {

        /**
         * Legge una coppia di coordinate da **un campo solo**.
         *
         * Due campi separati erano un errore di comodo: le coordinate si
         * incollano, e si incollano insieme — da Google Maps, da un messaggio,
         * da un annuncio di un'area di sosta. Spezzarle a mano per infilarle in
         * due caselle e' lavoro che l'app puo' fare da se'.
         *
         * **La virgola e' il problema.** In `42.7185, 12.1112` separa; in
         * `42,7185 12,1112` e' il decimale, ed e' cosi' che la digita una
         * tastiera italiana. Le due forme si distinguono contando: due virgole
         * sono due decimali, una sola separa se i due pezzi restano numeri.
         *
         * Si accettano anche il punto e virgola, i gradi con la lettera del
         * quadrante (`42.7185 N, 12.1112 E`) e il segno meno.
         *
         * Funzione pura. `null` quando non si e' capito: meglio un campo che
         * resta rosso che una tappa in mezzo all'oceano.
         */
        fun leggi(testo: String?): Coordinate? {
            val pulito = testo?.trim()?.takeUnless { it.isEmpty() } ?: return null

            val (senzaQuadranti, segni) = quadranti(pulito)
            val pezzi = dividi(senzaQuadranti) ?: return null
            if (pezzi.size != 2) return null

            val lat = numero(pezzi[0]) ?: return null
            val lon = numero(pezzi[1]) ?: return null

            return Coordinate(lat * segni.first, lon * segni.second)
                .takeIf { it.valide }
        }

        /**
         * Toglie `N`, `S`, `E`, `W`, `O` e i simboli di grado, restituendo i
         * segni che implicano. `S` e `W` fanno negativo; `O` sta per ovest, che
         * in italiano si scrive cosi'.
         */
        private fun quadranti(testo: String): Pair<String, Pair<Double, Double>> {
            val alto = testo.uppercase()
            val latNegativa = alto.contains('S')
            val lonNegativa = alto.contains('W') || alto.contains('O')
            val ripulito = alto.replace(LETTERE_E_GRADI, " ").trim()
            return ripulito to ((if (latNegativa) -1.0 else 1.0) to (if (lonNegativa) -1.0 else 1.0))
        }

        /** I due pezzi, decidendo cosa separa e cosa e' decimale. */
        private fun dividi(testo: String): List<String>? {
            if (testo.contains(';')) return testo.split(';').map { it.trim() }

            val virgole = testo.count { it == ',' }
            return when {
                // Due virgole: sono entrambe decimali, e separa lo spazio.
                virgole >= 2 -> testo.split(SPAZI).filter { it.isNotEmpty() }

                virgole == 1 -> {
                    val allaVirgola = testo.split(',').map { it.trim() }
                    // La virgola separa se i due pezzi sono numeri per conto
                    // loro: "42.7185, 12.1112" si', "42,7185" no.
                    if (allaVirgola.size == 2 && allaVirgola.all { numero(it) != null }) {
                        allaVirgola
                    } else {
                        testo.split(SPAZI).filter { it.isNotEmpty() }
                    }
                }

                else -> testo.split(SPAZI).filter { it.isNotEmpty() }
            }
        }

        /** Un numero con la virgola o col punto, col segno. */
        private fun numero(pezzo: String): Double? {
            val ripulito = pezzo.trim().replace(',', '.')
            if (!NUMERO.matches(ripulito)) return null
            return ripulito.toDoubleOrNull()
        }

        private val LETTERE_E_GRADI = "[NSEWO°'\"]".toRegex()
        private val SPAZI = "\\s+".toRegex()
        private val NUMERO = "[+-]?\\d{1,3}(\\.\\d+)?".toRegex()
    }
}
