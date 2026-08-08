package it.myacamperlife.app.dominio

/**
 * L'aritmetica del sottocampionamento di un'immagine. Funzione pura.
 *
 * Sta nel dominio e non accanto al decodificatore per la ragione di sempre: e'
 * l'unica parte di quel lavoro che si puo' sbagliare in silenzio, ed e' anche
 * quella con le conseguenze peggiori. Un fattore troppo piccolo carica in
 * memoria uno scatto da dodici megapixel — quarantotto megabyte come
 * ARGB_8888 — e una lista con trenta foto chiude l'app; un fattore troppo grande
 * da' miniature sfocate. Qui si verifica con dei numeri, senza un telefono.
 */
object Miniature {

    /**
     * Di quanto ridurre un'immagine perche' i suoi lati stiano entro
     * [latoMassimo].
     *
     * **Potenza di due**, perche' e' l'unica cosa che `inSampleSize` rispetta
     * davvero: valori diversi vengono arrotondati in giu' alla potenza piu'
     * vicina, quindi tanto vale calcolarla.
     *
     * Si guardano **entrambi** i lati e non il piu' lungo: fermarsi al lato lungo
     * lascerebbe passare una panoramica larghissima e bassa con un numero di
     * pixel enorme, che e' proprio il caso che si vuole evitare.
     */
    fun quantoRidurre(larghezza: Int, altezza: Int, latoMassimo: Int): Int {
        if (larghezza <= 0 || altezza <= 0 || latoMassimo <= 0) return 1
        var fattore = 1
        // **Entro, non oltre.** L'esempio di Android si ferma un passo prima, per
        // non ingrandire mai l'immagine quando la si disegna; qui si preferisce
        // scendere, perche' il rischio che conta e' la memoria e non un pelo di
        // morbidezza su un quadratino da sessantaquattro punti. Su un'immagine
        // grande la differenza e' quattro volte i byte.
        while (larghezza / fattore > latoMassimo || altezza / fattore > latoMassimo) {
            fattore *= 2
        }
        return fattore
    }
}
