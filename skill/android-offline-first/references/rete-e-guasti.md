# Rete e guasti

La rete è la parte che l'utente non vede funzionare: la vede solo fallire. Queste
regole servono a far sì che, quando fallisce, si sappia cosa fare.

## Nessuna libreria HTTP

Tre o quattro richieste in tutta l'app non giustificano un paio di megabyte di
dipendenza. `HttpURLConnection` con **tre tetti** basta:

- timeout di connessione (dieci secondi: sotto un ripetitore stanco ne servono tre)
- timeout di lettura (più lungo per i servizi che elaborano prima di rispondere)
- **tetto sulla dimensione della risposta**, e una risposta troncata vale come
  nessuna risposta: mezzo JSON non si analizza, e restituirlo produrrebbe zero
  risultati facendo credere che il servizio non abbia trovato niente.

Un `User-Agent` che dica chi chiama: alcuni servizi pubblici rifiutano le
richieste senza.

## Un servizio che può fallire deve poter dire come

Le chiamate «mute» (`String?`) vanno bene dove esiste un ripiego: senza meteo il
riepilogo esce comunque con le tappe. Dove **non** c'è ripiego, il tipo di ritorno
deve enumerare i modi di fallire:

```kotlin
sealed interface EsitoRicerca {
    data class Riuscito(val dati: Dintorno) : EsitoRicerca
    data object Vuoto : EsitoRicerca                       // ha risposto: qui non c'è niente
    data class Illeggibile(val elementi: Int) : EsitoRicerca // difetto nostro
    data class Avvertito(val messaggio: String) : EsitoRicerca // il server si lamenta, dentro un 200
    data class Rifiutato(val codice: Int, val messaggio: String?) : EsitoRicerca
    data object SenzaRete : EsitoRicerca
    data object SenzaPunti : EsitoRicerca                  // non c'era niente da chiedere
}
```

Perché la distinzione conta: **«qui non c'è niente» e «non lo so» sono due cose
diverse**, e solo la seconda ha un rimedio. Confonderle produce una schermata
vuota con una spiegazione plausibile — e quindi credibile, e quindi la peggiore
possibile.

## I modi di fallire non sono dove li cerchi

Il caso che è costato quattro fasi: **Overpass segnala i propri guasti dentro una
risposta 200.** Quando una query esaurisce tempo o memoria, il server risponde
`200` con `elements` vuoto e un campo `remark` che dice cosa è andato storto.
Leggere solo l'array significa concludere «in quella zona non c'è niente».

Regola generale: **prima di leggere i dati, leggi se il servizio si è lamentato.**
E quando esiste un modo per distinguere «zero risultati» da «zero risultati
salvati su trecento arrivati», tienilo: il secondo è un difetto tuo, e va detto
come tale.

## I servizi di cortesia si trattano come tali

Overpass, OSRM, Nominatim, Open-Meteo: gratuiti, mantenuti da volontari, e in
diritto di dirti no.

- **una richiesta per gesto**, non una per schermata che si apre
- **mai da un `LaunchedEffect`** che parte all'apertura di una vista
- chiedi **una cosa grande invece di dieci piccole** quando il servizio lo
  consente (raggruppa i filtri per chiave, non uno per categoria)
- ma **non una richiesta enorme**: il confine è dove il server comincia a
  rifiutare, e lo scopri solo provando
- se la richiesta è pesante, la risposta giusta può essere **cambiare il gesto**:
  cercare un punto per volta quando l'utente lo chiede, invece di anticipare tutto
  l'itinerario. Vedi «quando un rimedio dopo l'altro non guarisce» in SKILL.md.

### Più server, provati in fila

Un 504 con `Dispatcher_Client::request_read_and_idx::timeout` non è una query da
correggere: è il processo che distribuisce i turni di lettura che non ha uno slot
libero. **A un server che in quel momento non risponde non si rimedia
correggendo la richiesta.**

```kotlin
val SERVIZI = listOf(ufficiale, specchio1, specchio2)
```

Provati **uno per volta, fermandosi al primo che risponde**: una ricerca che
riesce costa una richiesta come prima. E attenzione a cosa fa passare al
successivo: **un «qui non c'è niente» è una risposta**, e ripeterla su tre server
sarebbe strapazzarli per farsi confermare quello che il primo ha già detto. Si
insiste solo su chi *non ha risposto*.

L'esito scritto dice anche **quale** server ha risposto: con tre server la domanda
«ha funzionato?» diventa «ha funzionato dove?».

## Il corpo d'errore: tieni la parte utile

Gli errori arrivano avvolti in boilerplate (due righe di licenza, HTML). In
duecento caratteri di messaggio quelle si mangiano la frase che spiega il guasto.
Taglia da dove comincia il messaggio vero (`Error`, in Overpass), e **se quel
marcatore non c'è tieni tutto**: un messaggio inatteso è proprio quello che non va
buttato. La funzione che taglia è pura: mettila nel dominio e provala sul corpo
d'errore vero.

## Le chiavi API

- **mai nel file delle impostazioni** dell'archivio: quella cartella può finire
  su un cloud. `EncryptedSharedPreferences` col Keystore.
- **nell'header, non nell'URL**: gli URL finiscono nei log e nella cronologia dei
  proxy.
- **si mostrano per le ultime quattro cifre**, e si possono dimenticare.
- **gli identificativi dei modelli sono impostazioni, non costanti**: i nomi
  vengono ritirati ogni pochi mesi, e un ritiro non deve zittire l'app fino al
  prossimo APK. Anche il prompt è un'impostazione.
- **due fornitori, uno di riserva**: se il principale rifiuta (quota, chiave,
  modello ritirato) si prova l'altro, e l'app dice che ha risposto la riserva.
- quando l'errore viene dal servizio, **mostra quello che ha detto il servizio**:
  chiave sbagliata, quota finita e modello inesistente hanno tre rimedi diversi.

## Verificare quando la rete è bloccata dall'ambiente

Se gli endpoint sono irraggiungibili dall'ambiente di sviluppo (spesso lo sono),
puoi ancora:

- **provare le funzioni pure**: costruzione della richiesta, lettura di una
  risposta vera copiata a mano, estrazione del messaggio d'errore
- **stampare la richiesta generata** e confrontarla con la specifica
- **dirlo all'utente**: «da qui non posso raggiungere quel servizio, la prima
  chiamata vera la fai tu». E preparare l'esito scritto, perché quella chiamata
  te la racconterà lui.
