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

**Fasi 1–7 di 9 realizzate.** L'app registra una giornata di viaggio, calcola i consumi
del mezzo, tiene il conto delle spese e la sera dice cosa aspettarsi domani — meteo e
chilometri compresi. **Nessuna schermata aspetta la rete:** quello che serve si prende in
anticipo.

Itinerario:

- importa un file `.md` con dentro il blocco `waypoints`, quello che usi oggi
- check-in su una tappa, con l'ora e la posizione
- salta una tappa e ripristinala: lo stesso comando fa le due cose
- aggiungi una tappa scegliendo dove inserirla, con le coordinate dal GPS o digitate
- tiene più viaggi e li elenca dal più recente

Diario:

- registra posizione, note, foto e rifornimenti con quattro tocchi dalla schermata
  d'apertura
- le foto si chiamano `foto_AAAAMMGG_HHMMSS_Localita.jpg`, come già oggi
- scrive `diario.md`: un file per viaggio, una sezione per giorno, aggiornata a ogni evento

Numeri:

- il rifornimento si registra come lo si legge alla colonnina: **importo e prezzo al
  litro**, e i litri li calcola l'app
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

- alle **19:00** una notifica con le tappe di domani, i chilometri, il **meteo** e i
  giorni successivi. L'ora si cambia, il riepilogo si spegne
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
- «Aggiorna adesso» nelle impostazioni, per quando sai di stare per entrare in una zona
  senza campo

Esplora — cosa c'è nei dintorni, **senza rete**:

- sette categorie che servono in camper: aree di sosta, campeggi, carico e scarico, acqua
  potabile, distributori, supermercati, cose da vedere
- ordinate per distanza da dove sei, con quante ce ne sono per categoria. Le categorie
  vuote non compaiono: una lista bianca fa sembrare rotta l'app
- un tocco apre il posto in Organic Maps o OsmAnd con un intent `geo:`. La navigazione la
  fanno loro, meglio di quanto potremmo farla noi e già offline
- i dintorni si scaricano da OpenStreetMap **una volta per viaggio**, in un corridoio di
  quindici chilometri intorno all'itinerario, e poi si consultano senza rete

E l'app sa **dire dove sei senza rete**: insieme ai punti di interesse arrivano i nomi dei
paesi lungo il percorso, e da quel momento una foto si chiama `foto_..._Bolsena.jpg` anche
se l'ultimo check-in era a Orvieto. Fra due nomi ugualmente vicini vince il paese più
grande — «3 km da Orvieto» dice qualcosa, «3 km da Sugano» no.

Sotto: `tappe.csv`, `spostamenti.csv`, `note.csv`, `foto.csv`, `rifornimenti.csv`,
`spese.csv`, `scorta/tratte.csv`, `scorta/poi.csv`, `scorta/luoghi.csv`,
`scorta/meteo.json`, `impostazioni.json` e `FORMATI.md` che spiega le colonne. I permessi
sono la posizione, le notifiche e la rete, e nessuna lettura dipende dall'ultimo.

Restano le fasi 8–9: client AI, rifiniture.

## Documenti

| | |
|---|---|
| [PROGETTO.md](PROGETTO.md) | Cos'è e cosa fa: input, output, funzionalità, schermate, confini |
| [ANALISI.md](ANALISI.md) | Si può fare: cosa regge offline e cosa no, workflow per workflow, con le scelte tecniche e la tabella di marcia |

## Provarla

L'APK viene compilato da GitHub Actions a ogni push e pubblicato come artifact
scaricabile nella tab **Actions** del repository, anche dal browser del telefono.

In `esempi/` c'è un itinerario con cui provare l'importazione.

## Tecnologie

Kotlin, Jetpack Compose (Material 3), file CSV e Markdown su archiviazione locale.
**Nessun database:** i volumi sono migliaia di righe e un archivio opaco contraddirebbe
il terzo principio. `minSdk` 33 (Android 13). Dispositivo di riferimento: Poco F7
(HyperOS, Android 16).
