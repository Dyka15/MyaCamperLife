# MyaCamperLife

App Android per chi viaggia in camper. Tiene l'itinerario, registra la giornata di
viaggio, calcola consumi e spese, e la sera dice cosa aspettarsi domani.

**Funziona senza rete.** È il motivo per cui esiste.

## Cosa sostituisce

Un bot Telegram appoggiato a workflow n8n, Google Sheets e Google Drive. Quel sistema
funziona bene, ma non fa nulla offline — e un camper passa buona parte del suo tempo
dove la connessione non c'è.

## I tre principi

- **Registrare non richiede rete, mai.** Foto, nota, rifornimento, spesa, check-in sono
  righe accodate a un file locale. Nessuna di queste azioni può fallire perché il
  telefono è offline.
- **Quello che serve dalla rete si prende in anticipo.** Le distanze fra le tappe
  arrivano quando si carica l'itinerario, il meteo ogni sera alle 19:00. In viaggio si
  consulta una scorta, non un servizio.
- **I file sono il prodotto.** L'app scrive CSV e Markdown in una cartella del telefono:
  si aprono in un foglio di calcolo, si leggono fra dieci anni. Non c'è un dentro da cui
  esportare.

## Stato

**Tutte e nove le fasi realizzate.** L'app registra una giornata di viaggio, calcola i
consumi del mezzo, tiene il conto delle spese e la sera dice cosa aspettarsi domani — meteo
e chilometri compresi. **Nessuna schermata aspetta la rete:** quello che serve si prende in
anticipo.

Itinerario:

- importa un file `.md` con dentro il blocco `waypoints`, quello che usi oggi
- check-in su una tappa, con l'ora e la posizione
- **e si annulla**, se l'hai dato per sbaglio: la tappa torna fra quelle da fare e l'arrivo
  esce dal diario, in un gesto solo. Era l'unico comando dell'app senza ritorno — «salta e
  ripristina» non tocca una tappa fatta, e ha ragione — e finché restava lì portava con sé
  dove sei, la prossima tappa, il riepilogo della sera e il nome delle foto
- salta una tappa e ripristinala: lo stesso comando fa le due cose
- aggiungi una tappa scegliendo dove inserirla, con le coordinate dal GPS o digitate
- **riscrivi il seguito del viaggio caricando un itinerario nuovo.** Sei al 13 agosto, i
  piani per i dieci giorni che restano sono cambiati: carichi un file che copre dal 13 al 23
  e le tappe da fare vengono sostituite. Le tappe **già fatte** e quelle saltate restano
  dove sono, e diario, spese, rifornimenti, foto e chilometri non si toccano — sono di questo
  viaggio. Nei file non si cancella niente: le tappe che escono restano scritte con una marca
  di annullamento, e il vecchio `itinerario.md` resta accanto al nuovo, perché il programma
  dei giorni che hai vissuto sta scritto solo lì. Sui giorni di cui parlano entrambi vince
  l'ultimo caricato
- **ritardi e anticipi**: se fai check-in in un giorno diverso da quello previsto, l'app te
  lo dice e propone di spostare di conseguenza le tappe che restano. Chiede, non fa: magari
  il giorno perso lo recuperi domani. Le tappe già fatte non si toccano
- **e le date si spostano anche a mano**, da una tappa in avanti, un giorno per volta: serve
  quando il ritardo lo sai la sera prima, e serve a disfare uno slittamento accettato per
  sbaglio — che prima non aveva nessun gesto inverso
- **un giorno è un giorno anche se non ti sposti**: il riepilogo della sera non salta i
  giorni fermi, dice «si resta a Bolsena». E all'import, se l'itinerario salta dei giorni,
  te lo segnala — quasi sempre è una dimenticanza nel file
- **e l'app chiede cos'è il file che carichi**, perché un itinerario può voler dire due cose:
  un viaggio nuovo, o il seguito di uno che hai già. Prima ne assumeva una — viaggio nuovo — e
  chi voleva l'altra si ritrovava un doppione col vecchio piano intatto, senza nessun errore da
  nessuna parte. La domanda porta i numeri veri: «escono 12 tappe da fare, ne entrano 13, le 9
  già fatte restano»
- tiene più viaggi e li elenca dal più recente

**La scheda di una tappa** — un tocco sulla tappa e c'è tutto quello che l'app sa di quel
posto, **senza rete**:

- **il programma della giornata per intero**, come sta scritto nell'itinerario: orari, durate,
  cosa vedere e perché, dove si dorme. Il blocco `waypoints` porta nomi e coordinate, ma il
  viaggio sta nel testo intorno — ottocento parole sul 10 agosto a Monaco contro un
  `"description": "Marienplatz, Residenz"` — e fino a ieri l'app leggeva solo il secondo. È il
  programma **della giornata**, quindi più tappe dello stesso giorno mostrano lo stesso testo:
  l'itinerario è scritto così, e così si legge
- **la descrizione della tappa** per intero, con i suoi capoversi, **più ogni campo che il
  lettore non riconosce** — orari, telefono, quota, un link
- il **meteo del suo giorno**, non di oggi: una tappa di giovedì porta la previsione di
  giovedì, con l'età del dato dichiarata come sempre
- **cosa c'è nei dintorni di quella tappa**, una riga per categoria con quante ce ne sono e
  la più vicina con la sua distanza. «Aree di sosta: 3» non dice se sono a due chilometri o
  a diciotto, e su un camper la differenza decide la giornata
- da dove ci si arriva e quanti chilometri di strada, quando le tratte ce l'hanno
- **«Cerca nei dintorni»**: chiede a un modello di *quella* tappa — il suo giorno, il suo
  meteo, i suoi dintorni — e non di dove sei adesso. La risposta si salva e resta lì, così
  arrivando fra tre giorni la si rilegge senza campo
- **si scorre di lato** per passare alla tappa successiva: un itinerario si legge in fila, e
  tornare all'elenco per aprire la tappa dopo sarebbero due tocchi per quello che fa un dito

Diario:

- registra posizione, note, foto e rifornimenti con quattro tocchi dalla schermata
  d'apertura
- le foto si chiamano `foto_AAAAMMGG_HHMMSS_Localita.jpg`, come già oggi
- scrive `diario.md`: un file per viaggio, una sezione per giorno, aggiornata a ogni evento
- **un tocco su una voce la corregge o la cancella.** Il chilometraggio di un pieno, la data
  di una spesa, il testo di una nota: si aggiustano dall'app, non aprendo il CSV. Correggere
  accoda una riga con lo stesso identificativo, cancellare accoda una lapide — **l'originale
  resta scritto nel file**, che è il motivo per cui offrire questa funzione non fa paura
- **le foto si vedono**: una miniatura accanto alla voce, e la foto a schermo intero
  toccandola. Vale anche per lo scontrino di una spesa, e dopo uno scatto la didascalia
  arriva con l'anteprima — «è venuta?» è la prima domanda, e prima non c'era risposta.
  Nessuna libreria di immagini: due megabyte di dipendenza per aprire un JPEG sono la stessa
  cifra per cui è stato tolto il riconoscimento del testo

Numeri:

- il rifornimento si registra come lo si legge alla colonnina: **importo e prezzo al
  litro**, e i litri li calcola l'app
- i chilometri sono **quelli dal pieno precedente**, non il contachilometri: alla colonnina si
  azzera il parziale e alla successiva si legge quello. Tre cifre invece di sei, che si copiano
  senza sbagliare mentre si tiene la pompa, e misurano esattamente il tratto che serve al
  consumo. Fra due pieni si sommano, come i litri; le righe vecchie scritte col contachilometri
  continuano a contare
- consumo **pieno-a-pieno**: km/l, litri e euro per 100 km, tratto per tratto e in media
  pesata sui chilometri
- **autonomia residua stimata** dai km con un pieno, meno i chilometri dedotti da tutti i
  punti registrati — check-in, posizioni, foto, rifornimenti
- **conto del viaggio**: totali per categoria, per modalità di pagamento e per giorno,
  spesa media giornaliera
- ogni numero dichiara com'è nato: una stima si chiama stima

Spese:

- categoria, importo e **modalità di pagamento** — contanti, POS, carta — in una form
  che si compila con tre tocchi e una cifra
- **la data la scegli tu**, su spese e rifornimenti: uno scontrino si ritrova in tasca due
  giorni dopo, e la voce va nel giorno in cui hai speso, non in quello in cui la scrivi
- **valuta estera**: si salva quello che c'era sullo scontrino più il cambio del momento,
  modificabile; il totale in euro si ricalcola sempre da quei due
- **foto dello scontrino**, allegata alla spesa, con un nome che porta data, ora e luogo
  come le foto del diario
- il carburante resta nei rifornimenti e non fra le spese: il conto somma le due tabelle
  tenendole distinte, così un pieno non viene contato due volte

Riepilogo della sera:

- alle **19:00** una notifica con le tappe di domani, i chilometri, e i giorni successivi.
  L'ora si cambia, il riepilogo si spegne
- **il meteo di ogni giornata, non solo di domani**: la scorta ha tre giorni di previsioni, e
  la sera prima si decide con quelle quale giorno mettere all'aperto e quale al coperto. Anche
  un giorno fermo ha il suo, preso dove si resta — è il giorno in cui il tempo conta di più,
  perché non c'è la guida a occupare le ore. L'età del dato si dice una volta, perché è la
  stessa per tutte
- **avviso di rifornimento** quando l'autonomia stimata non copre la strada di domani con
  un margine, o quando sei sotto gli 80 km comunque
- legge il campo `giorno` dell'itinerario in tutte le forme che arriva davvero
  (`2026-08-06`, `06/08/2026`, `6 agosto`, `mer 6`); quello che non riconosce lo lascia
  senza data invece di metterlo nel giorno sbagliato
- **sopravvive al riavvio** del telefono e all'aggiornamento dell'app, e un controllo ogni
  sei ore rimette la sveglia se HyperOS se l'è portata via
- nelle impostazioni si vede **il riepilogo di stasera senza aspettare le 19:00**, e ci
  sono i pulsanti che portano alle tre schermate di sistema da sistemare

La scorta — l'unica parte che tocca la rete, e la tocca **prima**:

- **meteo** da Open-Meteo, scaricato alle 19:00 per tutte le tappe in una richiesta sola,
  e mostrato con l'età dichiarata: «meteo di ieri». Oltre tre giorni non si mostra più —
  una previsione vecchia non è un dato vecchio, è un dato sbagliato
- **distanze su strada** da OSRM, chieste una volta quando importi l'itinerario. Da lì in
  poi sono un dato locale: la testata dice «34 km · 45 min» alla prossima tappa, e i
  chilometri di domani nel riepilogo sono quelli veri
- senza tratte si ripiega sulla linea d'aria **dicendolo**, e l'avviso di rifornimento usa
  un margine più largo perché sa di stare guardando un numero più incerto
- nelle impostazioni, «Aggiorna meteo e distanze» per quando sai di stare per entrare in una
  zona senza campo. I dintorni no: si cercano **una tappa per volta**, dalla schermata della
  tappa, perché una ricerca su tutto l'itinerario è una richiesta che il server pubblico non
  serve — e non lo dice con un errore, risponde a vuoto
- nelle impostazioni si vede **quando** meteo e dintorni sono stati scaricati, e quando la
  cartella è stata sincronizzata l'ultima volta: l'età di una scorta è metà del suo valore, e
  «ha davvero preso tutto?» è la domanda che uno si fa cambiando telefono
- **quando la ricerca dei dintorni fallisce, dice perché** — e lo **scrive**. È l'unica
  scorta senza ripiego: senza meteo esce la previsione vecchia, senza tratte la linea d'aria,
  senza dintorni resta una schermata vuota. Quindi 429 diventa «aspetta un minuto, è un
  servizio gratuito», 504 «richiesta troppo grande», e il caso «ha risposto ma non ho saputo
  leggerlo» si chiama così: è un difetto dell'app, non mancanza di campo. L'esito dell'ultima
  ricerca resta scritto nelle impostazioni, perché una notifica dura tre secondi e la domanda
  «perché non carica niente?» arriva il giorno dopo, in mezzo al nulla

Esplora — cosa c'è nei dintorni, **senza rete**:

- sette categorie che servono in camper: aree di sosta, campeggi, carico e scarico, acqua
  potabile, distributori, supermercati, cose da vedere
- ordinate per distanza da dove sei, con quante ce ne sono per categoria. Le categorie
  vuote non compaiono: una lista bianca fa sembrare rotta l'app
- un tocco apre il posto in Organic Maps o OsmAnd con un intent `geo:`. La navigazione la
  fanno loro, meglio di quanto potremmo farla noi e già offline
- i dintorni si cercano su OpenStreetMap **un punto per volta**, in un cerchio di dieci
  chilometri, e da quel momento si consultano senza rete: la scorta si riempie con le
  ricerche che fai, e quello che hai cercato resta cercato per tutto il viaggio
- **tre server provati in fila**, fermandosi al primo che risponde: il primo tentativo vero
  da un telefono ha ricevuto un 504 col dispatcher di Overpass congestionato, e a un server
  che non risponde non si rimedia correggendo la richiesta. Un «qui non c'è niente» invece
  non fa passare al server dopo: è una risposta, e chiederne conferma ad altri due sarebbe
  strapazzarli per niente

E l'app sa **dire dove sei senza rete**: insieme ai punti di interesse arrivano i nomi dei
paesi lungo il percorso, e da quel momento una foto si chiama `foto_..._Bolsena.jpg` anche
se l'ultimo check-in era a Orvieto. Fra due nomi ugualmente vicini vince il paese più
grande — «3 km da Orvieto» dice qualcosa, «3 km da Sugano» no.

Sotto: `tappe.csv`, `spostamenti.csv`, `note.csv`, `foto.csv`, `rifornimenti.csv`,
`spese.csv`, `dossier.csv` con i `.md` in `dossier/`, `scorta/tratte.csv`,
`scorta/poi.csv`, `scorta/luoghi.csv`, `scorta/meteo.json`, `impostazioni.json` e
`FORMATI.md` che spiega le colonne. I permessi sono la posizione, le notifiche e la rete, e
nessuna lettura dipende dall'ultimo.

**La cartella dei file** — il pezzo che rende vero il terzo principio:

- **è la prima cosa che l'app chiede**, e se in quella cartella c'è già un archivio di
  questa app — da un'installazione precedente o da un altro telefono — **i suoi viaggi
  entrano subito**. Reinstalli e ritrovi tutto: era il buco più serio, perché finora
  assegnare una cartella copiava solo verso fuori e ci scriveva sopra
- la fusione è la promessa che il formato fa dal primo giorno: le tabelle si uniscono
  concatenando le righe e tenendo l'ultima versione di ogni identificativo. Una riga
  cancellata **resta cancellata** — le lapidi si conservano, altrimenti tornerebbe in vita a
  ogni sincronizzazione — e una foto che c'è già non viene mai sovrascritta
- dalle impostazioni scegli una cartella con il selettore di sistema: `Documenti/Mya`, o
  una cartella già sincronizzata su un cloud, e da lì il backup lo ottieni gratis
- ogni scrittura ne innesca una **copia differita**: la registrazione resta un append
  locale che riesce sempre, la copia fuori può fallire senza conseguenze e si rifà da sola
- «Copia tutto adesso» per l'archivio che hai già, e per essere certo che fuori ci sia
  tutto prima di cambiare telefono
- **niente permessi di archiviazione**: l'app può scrivere solo nella cartella che le
  indichi

Aggiungere una tappa:

- **cerca un indirizzo**: prima fra i paesi già scaricati (senza rete), poi su
  OpenStreetMap. Un tocco sul risultato riempie le coordinate
- le **coordinate stanno in un campo solo**, `42.7185, 12.1112`: si incollano da una mappa
  e si incollano insieme. Virgola o punto decimale, separate da virgola, spazio o punto e
  virgola, con le lettere del quadrante se ci sono. Sotto compare quello che l'app ha capito

**Chiedere a un modello** — l'unica parte che ha bisogno di rete, e l'unica che non finge
di poterne fare a meno:

- una domanda libera in Esplora («dove dormiamo stanotte?»), con davanti il contesto che
  l'app ha già misurato: dove sei, che tempo farà, cosa c'è nei dintorni, la prossima tappa
- la risposta si salva come **dossier** in `dossier/`: un file Markdown con la risposta, le
  fonti, la domanda e il contesto. Da lì si rilegge **senza rete**, che è tutto il punto —
  si chiede dove c'è campo e si rilegge dove arrivi
- le **giornate di diario si riscrivono in prosa**, dalla cronaca registrata e solo da
  quella. Sotto resta una riga che dice da dove viene il testo: un diario è un documento, e
  quello che ci ha scritto un modello deve restare distinguibile
- **due modelli, principale e riserva**: Gemini e Grok. Se il principale rifiuta — quota
  finita, chiave scaduta, nome del modello ritirato — si prova l'altro, e l'app dice che ha
  risposto la riserva
- **gli identificativi dei modelli sono impostazioni**, non costanti compilate: i nomi
  vengono ritirati ogni pochi mesi, e un ritiro non deve zittire l'app fino al prossimo APK.
  Anche **il prompt** è un'impostazione: quello di serie è un punto di partenza, si riscrive
  viaggiando
- le **chiavi non stanno in `impostazioni.json`**, che viene copiato in una cartella magari
  sincronizzata su un cloud. Stanno in `EncryptedSharedPreferences`, restano sul telefono, e
  nelle impostazioni si vedono solo le ultime quattro cifre
- quando l'errore viene dal servizio si mostra **quello che ha detto il servizio**: una
  chiave sbagliata, una quota finita e un modello inesistente hanno tre rimedi diversi

**Tutte e nove le fasi sono realizzate.**

## Documenti

| | |
|---|---|
| [PROGETTO.md](PROGETTO.md) | Cos'è e cosa fa: input, output, funzionalità, schermate, confini |
| [ANALISI.md](ANALISI.md) | Si può fare: cosa regge offline e cosa no, workflow per workflow, con le scelte tecniche e la tabella di marcia |

## Provarla

L'APK viene compilato da GitHub Actions a ogni push e pubblicato come artifact
scaricabile nella tab **Actions** del repository, anche dal browser del telefono.

In fondo alle impostazioni c'è **la versione**, col numero di build e il commit da cui l'APK
è stato costruito: `versionName` cambia una volta ogni tante fasi mentre gli APK si
susseguono a ogni push, e «che build ho installato?» è la prima domanda davanti a un
difetto.

In `esempi/` ci sono due itinerari con cui provare l'importazione: uno breve e uno vero da
diciotto giorni, con il programma giorno per giorno.

## Installarla per davvero

Oltre all'APK di debug, ogni push compila anche **l'APK di release**: minificato da R8, con
le risorse non usate rimosse, e firmato se il repository ha la chiave nei segreti. È quello
da portare in viaggio — il debug ha i controlli interni accesi e pesa di più.

**La chiave si crea una volta e si conserva.** Android accetta un aggiornamento solo se è
firmato con la stessa chiave di quello installato: perderla vuol dire non poter più
aggiornare l'app sul telefono, solo disinstallarla. Il comando è in
[keystore.properties.esempio](keystore.properties.esempio), che spiega anche i quattro
valori. In locale li legge da `keystore.properties` nella radice (non versionato); in CI
dalle variabili d'ambiente, che arrivano da quattro segreti del repository:

| Segreto | Cos'è |
|---|---|
| `MYA_KEYSTORE_BASE64` | l'archivio `.jks` in base64 — `base64 -w0 mya.jks` |
| `MYA_KEYSTORE_PASSWORD` | la password dell'archivio |
| `MYA_KEY_ALIAS` | il nome della chiave dentro l'archivio |
| `MYA_KEY_PASSWORD` | la password della chiave |

Senza i segreti la compilazione **non** fallisce: esce un `app-release-unsigned.apk`, che
non si installa ma dimostra che R8 regge. È di proposito — così un fork compila.

⚠️ **Passando dal debug alla release, la prima volta va disinstallata l'app.** Le due sono
firmate con chiavi diverse e Android rifiuta la sostituzione; disinstallare porta via l'area
privata, cioè i CSV. Prima di farlo: assegna una cartella nelle impostazioni e attendi lo
specchio, poi reinstalla e riassegna la stessa cartella — la sincronizzazione fonde i file
trovati e l'archivio torna. È lo stesso percorso di un cambio di telefono, e per questo la
fusione esiste.

Se qualcosa nella release si comporta diversamente dal debug, il sospetto numero uno è una
regola di R8 mancante: le regole stanno in [app/proguard-rules.pro](app/proguard-rules.pro),
ognuna col motivo per cui esiste, e `isMinifyEnabled = false` in `app/build.gradle.kts` è la
via di fuga in una riga. La mappa dei nomi offuscati viene pubblicata come artifact
`mappa-r8` assieme all'APK: serve a rileggere una traccia di crash, e vale solo per quel
build.

## Tecnologie

Kotlin, Jetpack Compose (Material 3), file CSV e Markdown su archiviazione locale.
**Nessun database:** i volumi sono migliaia di righe e un archivio opaco contraddirebbe
il terzo principio. **Nessuna libreria HTTP e nessuna libreria di immagini:** le tre
richieste di rete stanno in ottanta righe di `HttpURLConnection`, e aprire un JPEG
sottocampionato in centoventi di `BitmapFactory` — un paio di megabyte di dipendenza per
ciascuna sarebbero la stessa cifra per cui è stato tolto il riconoscimento del testo.
`minSdk` 33 (Android 13). Dispositivo di riferimento: Poco F7 (HyperOS, Android 16).
