# CamperLife — App Android offline

Analisi di fattibilità per sostituire i workflow n8n + bot Telegram con un'app Android
che funzioni anche senza rete, salvando su file locali.

Versione 2, 6 agosto 2026.

Per la descrizione del prodotto — input, output, funzionalità, schermate — vedi
[PROGETTO.md](PROGETTO.md). Questo documento risponde a *si può fare*, quello a
*cos'è e cosa fa*.

---

## 1. Cosa stiamo sostituendo

Il sistema attuale è un bot Telegram guidato da cinque workflow n8n, con lo stato su
n8n Data Tables, lo storico su Google Sheets e le foto su Google Drive
(`MyaCamperLife/Foto`). L'intelligenza conversazionale è Gemini con ricerca web,
il meteo è Open-Meteo, le distanze OSRM.

Ogni pezzo di quella catena richiede rete. **Nessuna funzione del sistema attuale
funziona oggi senza connessione**: il messaggio non parte nemmeno. È questo il problema
che l'app risolve.

L'obiettivo è un'app **autonoma**: interfaccia propria, nessun Telegram, nessun n8n,
nessun server da tenere in piedi. Il che comporta una conseguenza precisa, ed è la
parte meno ovvia di tutta l'analisi: anche le due funzioni intrinsecamente online —
Esplora e il diario in prosa — vanno reimplementate **come chiamate dirette dall'app a
un modello**, non delegate a un bot. Sono fattibili, costano poco, e la sezione 6.2
spiega come.

Il punto di partenza dell'analisi è quindi una domanda sola, ripetuta per ogni
funzione: *questa cosa la sa fare il telefono da solo, o serve qualcuno all'altro capo?*

---

## 2. Verdetto in tre righe

| | Funzioni |
|---|---|
| ✅ **Offline pieno** | Itinerario (import, elenco, check-in, salta, aggiungi), diario (posizione, note, foto, rifornimenti), consumi e autonomia, spese, raggruppamento tappe per giorno, notifica serale, tutto lo storico e la sua consultazione |
| 🔶 **Offline con scorta** | Meteo, distanze e tempi di guida, geocoding inverso, prezzi carburante, POI nei dintorni. Non si calcolano sul posto, ma si possono **scaricare in anticipo** e usare offline: le tappe sono note prima di partire |
| ❌ **Solo online** | Esplora con AI e ricerca web, pagina di diario in prosa, avvisi stradali ragionati (ZTL, limiti). Restano **funzioni dell'app** — schermate come le altre — ma richiedono connessione nel momento in cui le si usa. Fuori portata del tutto: sincronizzazione Drive/Sheets e l'accesso da un secondo dispositivo |

La categoria interessante è la seconda. Quasi tutto ciò che sembra richiedere rete
*al momento dell'uso* in realtà la richiede **prima**: un itinerario si carica a casa,
e in quel momento si possono precaricare meteo, distanze fra le tappe e punti di
interesse lungo il percorso. In viaggio si consulta una scorta, non un servizio.

Resta fuori solo ciò che è genuinamente generativo o enciclopedico: il ragionamento di
un modello linguistico, la prosa del diario, la conoscenza del web. Su questo il
telefono da solo non può nulla — nessuna app offline ci arriva. Sono funzioni che l'app
offre chiamando un modello via rete, con degradazione dichiarata quando la rete non c'è.
Vedi sezione 6.

---

## 3. Principio guida: i file sono il prodotto

L'app non è un contenitore da cui i dati vanno estratti: **scrive file leggibili, e
quei file sono il formato ufficiale**. Tre ragioni concrete, non ideologiche:

1. **Il diario in prosa continuerà a nascere da un modello linguistico.** Se i dati
   della giornata stanno in un Markdown o in un CSV, si danno in pasto a Claude o a
   Gemini quando c'è rete, senza alcuna integrazione. L'app non sostituisce l'AI:
   **la rifornisce**.
2. **Interoperabilità con quello che già esiste.** Gli itinerari arrivano oggi come
   `.md` con un blocco JSON `waypoints`: l'app legge quel formato identico. I file dei
   dati riproducono le colonne delle schede del foglio Sheets, così i due mondi si
   travasano nei due sensi. Le foto mantengono la convenzione
   `foto_AAAAMMGG_HHMMSS[_localita].jpg`.
3. **Un archivio che sopravvive all'app.** Fra cinque anni i file si aprono comunque.

### Formato: CSV append-only, un file per tipo

File di testo in cui si accodano righe, uno per tipo di dato. Niente Google Sheets,
niente database: **`append` su un `.csv`**.

La scelta di un file per tipo invece di un registro unico non è un dettaglio: ricalca
esattamente le schede del foglio Sheets di oggi — "Diario di viaggio", "Spostamenti" —
e risolve da sola il problema che un registro unico avrebbe, cioè righe con colonne
diverse a seconda dell'evento. Ogni file ha le sue colonne e basta. La migrazione
diventa banale: si scarica ogni scheda come CSV e si mette nella cartella.

```
rifornimenti.csv
id;ts;km;litri;euro;pieno;luogo;lat;lon
b7c2;2026-08-06T18:05:00+02:00;48210;62,3;107,16;si;Orvieto;42,7185;12,1112

spese.csv
id;ts;categoria;euro;valuta;cambio;tappa;scontrino
c1d4;2026-08-06T20:11:00+02:00;sosta;18,00;EUR;;Orvieto;
```

**Punto e virgola come separatore, virgola come decimale.** È la sola combinazione che
non corrompe i dati: in Italia `1,72` è un prezzo, e un CSV separato da virgole lo
spezzerebbe in due campi. La strada opposta — punto decimale e separatore virgola — è
peggiore, perché un foglio di calcolo in locale italiano legge `1.72` come `172`. Con
`;` più virgola decimale il file si apre corretto in Sheets, in Excel e in LibreOffice
senza toccare nulla, e l'app parsifica il proprio formato senza ambiguità.

Quattro regole che rendono i file robusti, tutte a costo zero:

| Regola | Perché |
|---|---|
| **Riga d'intestazione, e si legge per nome** | Aggiungere una colonna non rompe i file vecchi: le righe di prima semplicemente non l'hanno. Nessuna migrazione, mai |
| **Nessun ritorno a capo dentro un campo** | Una riga fisica resta un record: si può leggere, contare e concatenare senza un parser di stato. Le note lunghe vanno in un `.md` a parte, che è già come funziona il diario |
| **Colonne `id` e `ts` in tutti i file** | La modifica è una riga nuova con lo stesso `id`, vince quella con `ts` più recente; la cancellazione è una riga lapide. Si resta in sola aggiunta anche correggendo |
| **Comando "compatta"** | Riscrive il file tenendo solo le righe valide, quando le correzioni si accumulano e la vista da foglio di calcolo si fa confusa |

Tre proprietà che si ottengono gratis:

- **A prova di crash.** Una scrittura è un `append` + `fsync`: non esiste lo stato
  intermedio in cui il file è corrotto. In camper il telefono si spegne per mille motivi.
- **Fondibile.** Due copie dello stesso file si uniscono concatenandole, saltando
  l'intestazione ripetuta e tenendo per ogni `id` la riga più recente. Se un domani si
  vuole sincronizzare fra telefono e tablet con Syncthing, funziona senza codice di merge.
- **Apribile in un foglio di calcolo senza conversioni**, che è il motivo per cui questa
  è la scelta giusta: non c'è un passo di esportazione da ricordarsi, perché l'archivio
  *è già* il file che si apre in Sheets. Se un giorno serve un grafico che l'app non fa,
  si apre il file e si fa lì.

In memoria si carica tutto: un anno di viaggi intensi sono qualche migliaio di righe,
dieci anni qualche decina di migliaia. Filtri e totali si fanno in Kotlin su liste,
senza query.

**JSON resta solo dove il CSV non c'entra**: la configurazione del mezzo e la scorta
meteo, che è annidata (previsioni orarie) e nessuno aprirà mai in un foglio. La regola:
**CSV per i dati che potresti voler guardare, JSON per la cache tecnica.**

Una cosa il CSV non la fa, e va detta: non descrive sé stesso. Un file con una colonna
in più e nessuno che ricordi cosa significhi è un problema che JSON non avrebbe. Si
paga con un `FORMATI.md` nella cartella, mezza pagina che elenca le colonne di ciascun
file. Vale la pena.

### Perché non Room

Room è la scelta giusta quando servono query su dati che non stanno in memoria,
o quando più scrittori concorrono. Qui non è il caso: i volumi sono minuscoli,
lo scrittore è uno. In cambio Room imporrebbe migrazioni a ogni cambio di modello,
DAO e KSP, e soprattutto un database opaco da cui i dati vanno *esportati* — cioè
esattamente il contrario del punto 3.

Si rivaluta se e quando i dati crescono di due ordini di grandezza, o se serve
ricerca full-text su migliaia di note. Non prima.

### Dove vivono i file

Su Android 13+ le opzioni sono tre, e nessuna basta da sola:

| Posizione | Serve permesso | Visibile all'utente | Sopravvive alla disinstallazione |
|---|---|---|---|
| `filesDir` (privata interna) | no | no | no |
| `getExternalFilesDir()` → `Android/data/<pkg>/` | no | male: da Android 11 i gestori file non entrano più in `Android/data`, e quello di HyperOS non fa eccezione | no |
| Cartella scelta dall'utente via SAF (`ACTION_OPEN_DOCUMENT_TREE`) | permesso persistente, chiesto una volta | sì | sì |

**Scelta: copia di lavoro in `filesDir`, specchio nella cartella SAF.** L'app scrive
sempre e comunque nella sua area privata, quindi funziona anche se il permesso SAF non
è stato ancora concesso o viene revocato; a ogni scrittura ricopia (in modo differito)
sulla cartella che l'utente ha indicato — tipicamente `Documenti/CamperLife`, o una
cartella già sincronizzata da Drive o Syncthing.

Lo specchio serve perché `DocumentFile` è lento e fragile per scritture frequenti: usarlo
come archivio primario significherebbe far dipendere ogni salvataggio da un `Uri` che
l'utente può invalidare.

`MANAGE_EXTERNAL_STORAGE` non è un'opzione: le policy del Play Store lo concedono solo
a gestori file, e non serve. `WRITE_EXTERNAL_STORAGE` su API 33+ non fa più nulla.

### Alberatura

```
CamperLife/
├── FORMATI.md                     le colonne di ogni file, mezza pagina
├── mezzo.json                     serbatoi, consumi medi, scadenze
├── viaggi/
│   └── 2026-08-toscana/
│       ├── viaggio.json           nome, date, equipaggio
│       ├── tappe.csv              waypoint, stato, data di check-in
│       ├── spostamenti.csv        posizioni e check-in — come la scheda di oggi
│       ├── note.csv               note di viaggio
│       ├── rifornimenti.csv       km, litri, importo, pieno sì/no
│       ├── spese.csv              categoria, importo, valuta
│       ├── foto.csv               nome file, didascalia, coordinate
│       ├── foto/
│       │   └── foto_20260806_143012_Orvieto.jpg
│       └── diario/
│           └── 2026-08-06.md      cronaca, poi prosa (sez. 6.2)
├── scorta/                        dati scaricati in anticipo (sez. 5)
│   ├── meteo.json                 annidato: resta JSON
│   └── tratte.csv                 da, a, km, minuti
├── poi/europa.sqlite              estratto OSM, sola lettura
└── digest-2026.md                 riepilogo compatto da dare a un modello
```

Non c'è più una cartella `esporta/`: **l'esportazione non esiste come passo separato**,
perché ogni file è già nel formato con cui si aprirebbe. Resta solo il digest, che non è
un export dei dati ma un riassunto scritto per essere letto da un modello.

---

## 4. Analisi workflow per workflow

### 4.1 Router → l'interfaccia

Il Router fa autorizzazione del chat ID, pre-filtro dei messaggi, menu, tastiere rapide
e il wizard conversazionale "aggiungi tappa". **Sparisce interamente**, e non viene
sostituito: diventa navigazione Compose.

Non è una perdita, è un guadagno su tre fronti. Un wizard che chiede
nome → giorno → posizione → ordine in quattro messaggi è una form con quattro campi,
compilabili in qualsiasi ordine e correggibili senza ricominciare. Lo stato
`wizard_step` / `wizard_data` su Data Table non esiste più. E l'autorizzazione del chat
ID non serve: l'app è sul telefono di chi la usa.

| Funzione | Verdetto |
|---|---|
| Menu, tastiere, comandi `/…` | ✅ schermate native |
| Wizard aggiungi tappa | ✅ form, migliore dell'originale |
| Intercettazione foto | ✅ fotocamera in-app o condivisione dalla galleria |
| `/dove` — posizione attuale | ✅ GPS / 🔶 il nome della località: vedi 5.3 |
| Domande libere in linguaggio naturale | ❌ diventa una schermata che chiama un modello: vedi 6.2 |

**Cosa si perde uscendo da Telegram.** Tre cose, e vale nominarle perché nessuna ha un
sostituto dentro l'app:

- **L'accesso da qualsiasi dispositivo.** Oggi il bot risponde dal telefono, dal tablet,
  dal browser del portatile. L'app sta su un telefono. Attenuazione parziale: la cartella
  dei file può essere sincronizzata e letta altrove.
- **La notifica push senza configurazione.** Diventa una notifica di sistema, che su
  HyperOS richiede i passaggi di 4.5.
- **La chat come storico sfogliabile.** Al suo posto elenchi filtrabili e file di testo:
  più strutturati, meno scorrevoli.

In compenso spariscono il token del bot, l'autorizzazione del chat ID, il server n8n, le
Data Table, il webhook e le quote delle API Google. **Zero infrastruttura**: restano il
telefono e, per le funzioni online, due chiamate HTTP.

### 4.2 Itinerario

L'unico pezzo con una dipendenza di rete vera è OSRM.

| Funzione | Verdetto | Note |
|---|---|---|
| Import `.md` con blocco `waypoints` | ✅ | Stesso formato di oggi: `name`, `lat`, `lng`, `type`, `giorno`, `description`. Si riceve via condivisione da Drive, Telegram o file manager |
| Elenco tappe con stato | ✅ | `da_fare` / `fatta` / `saltata` come oggi |
| Check-in su tappa | ✅ | Marca fatta, aggiunge la riga a `spostamenti.csv`, annuncia la prossima |
| Salta / ripristina | ✅ | Comando a doppio stato, identico |
| Aggiungi tappa con posizione GPS o `lat,lng` | ✅ | |
| Prossima tappa: distanza e tempo di guida | 🔶 | OSRM è online. Vedi sotto |

**Le distanze si precalcolano.** Quando si importa un itinerario e c'è rete, si
interrogano in blocco tutte le tratte fra tappe consecutive e si salvano in
`scorta/tratte.json`. Da quel momento distanza e tempo di guida sono un dato locale.
Serve una sola finestra di connettività per l'intero viaggio, e l'itinerario si carica
comunque prima di partire.

Se rete non c'è mai stata, il ripiego è la distanza in linea d'aria (formula
dell'emisenoverso, aritmetica pura) con l'avvertenza che è una sottostima: su strada
italiana il rapporto reale sta di solito fra 1,2 e 1,4. Meglio un numero dichiarato
approssimativo che nessun numero.

**La navigazione vera non la facciamo.** Un motore di instradamento offline
(GraphHopper, Valhalla) su Android è tecnicamente possibile ma vuole un grafo stradale
da uno o due gigabyte per l'Italia, più il lavoro di costruirlo e aggiornarlo.
Organic Maps e OsmAnd fanno già esattamente questo, meglio di quanto potremmo fare noi,
e sono probabilmente già installati. La tappa si apre in quelle app con un intent
`geo:` — due righe di codice invece di un modulo.

### 4.3 Esplora

È il workflow che **non si può portare offline**, ed è giusto dirlo subito: un modello
linguistico con ricerca web non ha un equivalente locale.

Diventa comunque una schermata dell'app, costruita **a due strati**: sotto, la ricerca
locale nel dataset POI, che risponde sempre; sopra, la risposta ragionata del modello,
che compare quando c'è rete. La schermata non è mai vuota e non mostra mai un errore di
connessione: mostra quello che sa, e dice che il resto arriverà. Il modello si chiama
direttamente dall'app — vedi 6.2 per il client, il costo e la chiave.

Le otto cose che Esplora fa non sono tutte dello stesso tipo, e sei su otto hanno un
sostituto offline utile.

| Cosa chiede l'utente | Offline | Come |
|---|---|---|
| Aree di sosta camper e campeggi vicine | ✅ | Estratto OpenStreetMap: `tourism=caravan_site`, `tourism=camp_site` |
| Punti di carico e scarico | ✅ | `amenity=sanitary_dump_station`, `waste_disposal` — copertura italiana disomogenea ma reale |
| Distributori di carburante | ✅ | `amenity=fuel`, circa 25.000 in Italia |
| Prezzi del carburante | 🔶 | Open data Osservaprezzi MIMIT: anagrafica impianti e prezzi praticati, pubblicati ogni giorno in CSV compresso (i prezzi sono quelli in vigore alle 8:00 del giorno prima). Si scarica quando c'è rete, si consulta offline con la data del dato in chiaro |
| Attrazioni, ristoranti | 🔶 | `tourism=*`, `amenity=restaurant`: nomi e coordinate sì, recensioni e descrizioni no. Utile per "cosa c'è qui", inutile per "dove mangio bene" |
| Supermercati con parcheggio adatto ai camper | 🔶 | I supermercati sì, il giudizio "adatto ai camper" non è un dato che OSM contenga. Al massimo si stima dalla superficie del parcheggio |
| Meteo puntuale | 🔶 | Vedi 5.1 |
| Avvisi stradali: ZTL, limiti di altezza e peso | 🔶 | `maxheight` e `maxweight` esistono in OSM e si possono controllare lungo il percorso; le ZTL sono mappate a macchia di leopardo. La sintesi ragionata di Gemini non è riproducibile |

**Il dataset POI si costruisce a monte, non sul telefono.** Una query Overpass per i tag
che ci interessano, convertita in SQLite con indice spaziale, allegata all'app o
scaricabile per regione. Solo le categorie elencate, non tutta OSM: si resta
nell'ordine di pochi megabyte per l'Italia, qualche decina per l'Europa occidentale.
Va rigenerato periodicamente (un'azione GitHub mensile) e va citata la licenza ODbL.

**La mappa disegnata è una questione separata dai POI**, e la risposta per la v1 è: non
la disegniamo. Le tessere di Google non sono utilizzabili offline per licenza; le
tessere OSM non si possono scaricare in massa dai server pubblici per policy; un pacchetto
vettoriale offline con MapLibre è fattibile ma è il singolo pezzo di lavoro più grosso di
tutto il progetto. La v1 mostra elenchi ordinati per distanza e delega la mappa all'app
di mappe offline già installata. Se poi la mappa in-app servirà davvero, si aggiunge:
niente in questa architettura la preclude.

### 4.4 Diario

Il cuore del sistema, e la parte che offline funziona meglio di oggi.

| Funzione | Verdetto | Note |
|---|---|---|
| Registrazione posizione | ✅ | Il GPS non ha bisogno di rete. Vedi 5.3 per il primo agganciamento |
| Nome della località (geocoding inverso) | 🔶 | Vedi 5.3 |
| Note testuali | ✅ | |
| Rifornimenti: litri e importo | ✅ | E in più il calcolo dei consumi, che oggi non c'è. Vedi 4.6 |
| Foto con la convenzione di nome attuale | ✅ | `foto_AAAAMMGG_HHMMSS[_localita].jpg`, stessa regola, generato in locale |
| Caricamento foto su Google Drive | ❌ offline | Vedi sotto |
| Riga sul foglio "Spostamenti" | ✅ | Diventa una riga in `spostamenti.csv`, con le stesse colonne della scheda di oggi: il foglio Sheets diventa un file locale, senza cambiare forma |
| Pagina di diario generata dall'AI | ❌ | Vedi sezione 6 |
| Storico consultabile | ✅ | E consultabile senza rete, che è il punto |

**Le foto restano locali.** Implementare OAuth Google e l'API Drive nell'app è
fattibile, ma aggiunge una dipendenza pesante per replicare un caricamento che offline
non avviene comunque. L'alternativa offline-first: la foto si salva subito nella
cartella del viaggio con il nome giusto, e la cartella specchio (sez. 3) può essere una
cartella già sincronizzata da Drive o Syncthing. Il caricamento diventa un problema del
sistema di sincronizzazione, non dell'app.

Opzione da valutare: registrare le foto anche in `MediaStore` sotto un album
`Pictures/CamperLife`, così Google Foto le include nel suo backup. Costa poco, ma
significa che la foto vive fuori dall'archivio autoconsistente. Da decidere (sez. 9).

Nota utile: se il GPS non ha ancora agganciato, l'EXIF della foto scattata poco prima
può fornire le coordinate. Fonte di posizione a costo zero.

### 4.5 Meteo serale (19:00)

Qui il progetto ha un vantaggio inaspettato: **la parte difficile è già stata risolta in
questo repository**. Far scattare qualcosa a un'ora precisa su HyperOS, sopravvivere al
riavvio, resistere al congelamento — è esattamente il problema di Cicala, la sveglia
parlante nell'altro repository, risolto lì con
`AlarmManager`, `BootReceiver`, un watchdog `WorkManager` ogni sei ore e una schermata di
onboarding sulle tre impostazioni Xiaomi. Quel lavoro si trasporta.

| Funzione | Verdetto |
|---|---|
| Scatto alle 19:00, anche a schermo spento e app chiusa da giorni | ✅ |
| Raggruppamento delle tappe `da_fare` per data, fino a tre giorni | ✅ logica pura |
| Notifica formattata | ✅ |
| Flag di attivazione (il `briefing` di oggi) | ✅ |
| Le previsioni meteo | 🔶 vedi 5.1 |

Differenza rispetto a Cicala: **non serve l'allarme esatto**. Una notifica di riepilogo
alle 19:00 tollera qualche minuto di scarto, quindi si usa
`setAndAllowWhileIdle` senza `SCHEDULE_EXACT_ALARM` né `USE_EXACT_ALARM` — che tra
l'altro le policy del Play Store riservano alle app di sveglia, e un'app per camper non
è titolata a chiederlo.

Idea a costo quasi nullo, dato che il motore vocale esiste già in Cicala: far
**leggere** il riepilogo serale ad alta voce. Fuori scope per la v1, ma vale ricordarlo.

### 4.6 Consumi e autonomia — funzione nuova

Oggi il diario registra litri e importo, e si fermano lì. Sono dati che chiedono di
essere calcolati, e il calcolo è aritmetica offline.

**Consumo pieno-a-pieno.** Solo i segmenti fra due rifornimenti entrambi marcati
"pieno" danno un consumo valido: i litri di tutti i rifornimenti nell'intervallo,
esclusi quelli del primo pieno, divisi per i chilometri percorsi. I riempimenti parziali
si accumulano nel segmento invece di produrre numeri fantasiosi. Da qui km/l,
l/100 km, €/100 km e €/km.

**Autonomia.** Il consumo medio giornaliero di acqua e gas si ricava dallo storico dei
rimbocchi: litri caricati diviso giorni fra un carico e il successivo. Con la capacità
dei serbatoi in `mezzo.json` si stima quanto resta. È una **stima da storico, non una
misura**: va presentata come tale.

**Sensori di bordo: no.** Leggere davvero i livelli richiede hardware. I pannelli CBE e
Schaudt non espongono nulla di pubblico; Truma passa dal suo cloud. Qualche dispositivo
BLE ha protocolli documentati dalla comunità (shunt Victron, alcuni BMS) e funzionerebbe
offline via Bluetooth, ma dipende interamente da cosa è installato sul mezzo. Fuori
scope finché non si sa (sez. 9).

**OBD-II: no.** Il PID del contachilometri non è standard e la resa varia da veicolo a
veicolo. Il chilometraggio si digita: sono tre secondi al rifornimento.

### 4.7 Spese — funzione nuova

Non esiste nel sistema attuale: è una funzione da progettare, non da portare.
Tutto offline tranne un dettaglio.

| Funzione | Verdetto |
|---|---|
| Voci con categoria, importo, tappa, metodo di pagamento | ✅ |
| Totali per viaggio, per giorno, per categoria; spesa media giornaliera | ✅ |
| Divisione per persona | ✅ |
| Foto dello scontrino | ✅ |
| Lettura automatica dell'importo dallo scontrino | 🔶 ML Kit riconosce il testo **interamente sul dispositivo**. Il modello va incluso nell'APK (pochi MB) e non nella variante consegnata da Play Services, che vuole un download iniziale |
| Valuta estera | 🔶 Il cambio è un dato di rete: si salva il tasso *sul momento della registrazione*, modificabile a mano, così la voce resta corretta per sempre senza riconnettersi |
| Import CSV dalla banca | ✅ |
| Pedaggi automatici | ❌ manuali |

---

## 5. La scorta: rete in anticipo, non al momento

Tre dati sembrano richiedere connettività e non la richiedono, se ci si organizza.

### 5.1 Meteo

Open-Meteo è gratuito, non richiede chiave e restituisce fino a sedici giorni. Le tappe
future sono note. Quindi: **ogni volta che c'è rete, si scaricano le previsioni per
tutte le tappe programmate e si salvano in `scorta/meteo.json`**. Alle 19:00 la notifica
usa la scorta e dichiara sempre l'età del dato ("previsione di stamattina alle 9").

Il workflow `MyMeteo Custom` con il suo webhook non serve più: la formattazione del
messaggio diventa codice nell'app.

Degradazione: previsione recente → si usa; vecchia → si usa dicendolo; assente → la
notifica esce comunque con l'elenco delle tappe del giorno, che è già la metà del suo
valore.

### 5.2 Tratte

Come in 4.2: precalcolo OSRM all'import dell'itinerario, ripiego sulla linea d'aria.

### 5.3 Posizione e nome della località

Il GPS **non ha bisogno di rete**: i satelliti si ricevono in mezzo al nulla. Serve rete
solo per due cose, e per nessuna delle due è indispensabile:

- **Primo agganciamento veloce.** Senza assistenza A-GNSS il primo fix a freddo può
  richiedere da mezzo minuto a un paio. I dati di assistenza restano in cache per
  giorni, quindi in pratica il problema si presenta raramente. Mitigazioni: accettare
  l'ultima posizione nota, permettere l'inserimento manuale di `lat,lng`, leggere l'EXIF
  di una foto appena scattata.
- **Nome della località.** Il `Geocoder` di Android passa dalla rete. Offline si risolve
  con un dataset di toponimi allegato: GeoNames filtrato sui centri abitati d'Europa sta
  in pochi megabyte, e il paese più vicino a una coppia di coordinate si trova con una
  ricerca su indice a griglia. Il risultato è "3 km da Orvieto" invece dell'indirizzo
  civico esatto — che per un diario di viaggio è quello che serve davvero.

Con quel dataset a bordo, `/dove` e la denominazione delle foto diventano **✅ offline
pieno**, non 🔶.

---

## 6. Le funzioni generative

Qui sta l'unica rinuncia vera *offline*, e conviene guardarla in faccia invece di
promettere sostituti che non esistono. La sezione ha due metà: **6.1** cosa si può fare
senza rete, **6.2** come l'app chiama un modello quando la rete c'è.

**Un modello sul telefono non è la risposta.** Gemini Nano gira solo su una lista
ristretta di dispositivi tramite AICore, e il Poco F7 non è fra quelli — va verificato,
ma è quasi certo. Gemma 1B quantizzato via MediaPipe girerebbe sui 12 GB di RAM del
dispositivo, ma parliamo di centinaia di megabyte da scaricare, secondi di latenza per
risposta e una qualità che con un modello di frontiera più ricerca web non ha rapporto.
Non per la v1, e probabilmente mai per questo scopo.

### 6.1 Cosa regge senza rete

Le soluzioni per ciascuna funzione sono diverse fra loro:

| Funzione | Sostituto offline |
|---|---|
| **Ingresso in linguaggio naturale** ("ieri 60 litri a 1,72 a Orvieto") | Form strutturate, più un **parser deterministico** per una grammatica ristretta: numeri con unità, date relative, nomi di località conosciute. Copre le frasi che si usano davvero, che sono poche e ripetitive. Più il **dettato vocale offline** di Android (`createOnDeviceSpeechRecognizer`, API 33+), che richiede il pacchetto lingua italiana installato: si parla, il parser interpreta, la form si presenta precompilata da confermare |
| **Pagina di diario in prosa** | Un template deterministico che compone gli eventi della giornata in Markdown: tappe, posizioni, note, foto, rifornimenti, spese. Non è prosa, è una cronaca ordinata — che è precisamente l'input ideale da dare a Claude quando c'è rete. **L'app produce la giornata strutturata, il modello ci scrive sopra.** Il file `diario/2026-08-06.md` nasce come cronaca e viene sostituito dalla versione in prosa quando e se si passa dal modello |
| **Ricerca nello storico a domande** | Filtri strutturati e ricerca testuale sulle note. In memoria, su questi volumi, è istantaneo |
| **Suggerimenti e ragionamento sui dintorni** | Il dataset POI di 4.3 risponde a "cosa c'è nel raggio di 5 km". Non risponde a "vale la pena". Quella domanda resta al modello, online — vedi 6.2 |

### 6.2 Il client AI dentro l'app

Le due funzioni generative non si delegano a un bot: l'app le chiama da sé. È una
schermata con un campo di testo e una chiamata HTTPS, non un'integrazione complicata.

**Un solo client, due usi.** Lo stesso codice serve entrambe le funzioni; cambia solo il
prompt e cosa gli si dà in pasto.

| Uso | Cosa si manda | Cosa torna |
|---|---|---|
| **Esplora** | La domanda, la posizione, il meteo in cache, i POI locali già trovati | La risposta ragionata, salvata come `dossier` della tappa così resta leggibile offline |
| **Diario in prosa** | La cronaca strutturata della giornata, generata in locale | Il testo che sostituisce `diario/2026-08-06.md`; l'originale resta come sorgente |

**La ricerca web è compresa nel modello, non è un pezzo in più.** È il dettaglio che
rende la cosa semplice: sia le API di Claude sia quelle di Gemini espongono uno strumento
di ricerca eseguito lato server. L'app manda una domanda, il modello cerca da sé e
risponde con le fonti. Non serve integrare un motore di ricerca, né riprodurre la catena
che oggi vive in n8n.

**Quale modello.** Continuità da un lato, integrazione più semplice dall'altro:

| | Nota |
|---|---|
| **Gemini** | È quello che il sistema usa già: il prompt di Esplora si trasporta senza riscritture. La ricerca è il *grounding* con Google Search — sui modelli Gemini 3 include 5.000 richieste al mese gratuite, poi 14 $ ogni 1.000 ricerche |
| **Claude** | Strumento `web_search` eseguito lato server, 10 $ ogni 1.000 ricerche più i token. Prosa migliore, ed è già il posto dove nascono gli itinerari `.md`: un motivo concreto di coerenza |

Consiglio: **Claude Sonnet per il diario in prosa, e la scelta è aperta su Esplora.**
Sonnet costa 3 $/15 $ per milione di token (in offerta 2 $/10 $ fino al 31 agosto 2026);
Opus 5 sta a 5 $/25 $ e non serve per scrivere una pagina di diario. Su Esplora la
continuità con il prompt esistente ha un valore reale, quindi vale provare entrambi.

**Il costo è trascurabile, e conviene dirlo con i numeri.** Una richiesta di Esplora sono
qualche migliaio di token in ingresso e un migliaio in uscita: **fra due e tre centesimi**,
più una ricerca a un centesimo. Una pagina di diario, senza ricerca, sta sotto il
centesimo. Anche con dieci interrogazioni al giorno per due settimane di viaggio si parla
di **pochi euro per vacanza** — meno di una notte in area di sosta. Il tetto di spesa si
imposta comunque sul pannello del fornitore.

**La chiave API.** Va inserita dall'utente una volta nelle impostazioni e conservata in
`EncryptedSharedPreferences`. Su questo serve una precisazione onesta: **una chiave
compilata dentro l'APK è estraibile da chiunque abbia il file**, quindi non si fa. Con
l'utente che inserisce la propria, il problema non esiste: la chiave sta sul dispositivo
di chi la possiede. Se un domani l'app andasse distribuita a estranei servirebbe un
piccolo backend a fare da tramite — cioè esattamente l'infrastruttura che stiamo togliendo.
Per un'app personale, chiave inserita a mano.

**Il prompt diventa codice.** Oggi vive dentro un nodo n8n; nell'app sta nel repository,
versionato assieme al resto, e modificabile dalle impostazioni per poterlo correggere sul
campo senza ricompilare.

**Degradazione.** Nessuna schermata dipende dal modello per esistere. Senza rete: Esplora
mostra i POI locali e i dossier già scaricati; il diario resta la cronaca strutturata, e
una coda segna le giornate ancora da narrare — si smaltiscono in blocco quando si ritrova
il wifi. **Questa coda è più comoda dell'originale**: oggi la pagina di diario si genera
un giorno per volta, quando il bot risponde.

### Il rapporto fra app e modello

Vale metterlo per iscritto perché condiziona il progetto: **l'app non compete con il
modello, gli fa da organo di senso e da memoria.** Raccoglie sul campo, senza rete, in un
formato che il modello digerisce; il modello interviene quando c'è connessione e quando
serve giudizio. `digest-2026.md` resta utile anche così: è il riepilogo compatto
da dare in pasto a una conversazione, quando si vuole ragionare su più viaggi insieme
invece che su una giornata.

---

## 7. Architettura runtime

```
        ┌──────────────────────────────────────────────┐
        │   UI Compose — Viaggio, Diario, Consumi,     │
        │   Spese, Impostazioni                        │
        └───────────────────┬──────────────────────────┘
                            │
                ┌───────────▼────────────┐
                │      Registro          │  stato in memoria (StateFlow)
                │  eventi + tappe        │
                └───────┬────────────┬───┘
                append  │            │  specchio differito
                        ▼            ▼
        ┌───────────────────┐   ┌──────────────────────┐
        │ filesDir/         │   │ cartella SAF scelta  │
        │ *.csv    (verità) │   │ dall'utente          │
        └───────────────────┘   └──────────────────────┘

        ┌──────────────────────────────────────────────┐
        │  Rifornitore di scorta (quando c'è rete)     │
        │  Open-Meteo · OSRM · Osservaprezzi           │
        └───────────────────┬──────────────────────────┘
                            ▼
                    scorta/*.json   ← letta sempre, mai indispensabile

        ┌──────────────────────────────────────────────┐
        │  Client AI (quando c'è rete e su richiesta)  │
        │  domanda → modello + ricerca web → risposta  │
        └───────────────────┬──────────────────────────┘
                            ▼
             dossier di tappa · diario/*.md in prosa
             (scritti su file: restano leggibili offline)

        ┌──────────────────────────────────────────────┐
        │  AlarmManager 19:00 → Receiver → notifica    │
        │  BootReceiver · watchdog WorkManager 6h      │
        │  (schema trasportato da Cicala)              │
        └──────────────────────────────────────────────┘
```

Due invarianti da rispettare in tutto il codice:

1. **Nessun percorso di lettura dipende dalla rete.** Le funzioni online scrivono nella
   scorta; la UI legge solo file locali. Un errore di rete non produce mai una schermata
   vuota, produce un dato più vecchio con l'età dichiarata.
2. **La scrittura non aspetta niente.** Salvare un evento è un append locale che riesce
   sempre. Specchio SAF, eventuali upload e rifornimento della scorta sono differiti e
   possono fallire senza conseguenze.

### Dominio testabile

Come `TimePhrase` in Cicala, la logica che conta va in funzioni pure senza dipendenze
Android, coperte da JUnit: calcolo pieno-a-pieno, autonomia dei serbatoi, divisione
delle spese, raggruppamento delle tappe per data, emisenoverso, parser dei `waypoints`,
lettura e fusione dei CSV. Sono i punti dove un errore silenzioso è peggio di un crash.

### Permessi

```
ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
CAMERA
POST_NOTIFICATIONS
INTERNET / ACCESS_NETWORK_STATE
RECEIVE_BOOT_COMPLETED
RECORD_AUDIO                        solo se si fa il dettato vocale
FOREGROUND_SERVICE(_LOCATION)       solo se si registra la traccia GPS
```

Niente `SCHEDULE_EXACT_ALARM`, niente `USE_EXACT_ALARM` (vedi 4.5), niente permessi di
archiviazione (vedi 3). `ACCESS_BACKGROUND_LOCATION` si evita: la posizione si registra
quando l'app è in primo piano, o con un servizio in primo piano visibile se si vuole la
traccia.

### HyperOS

Valgono le stesse tre impostazioni documentate per Cicala — avvio automatico, nessuna
restrizione batteria, blocco nelle recenti — con lo stesso onboarding a pulsanti. Qui
sono meno critiche: se salta la notifica delle 19:00 si perde un riepilogo, non una
sveglia. Diventano critiche se si aggiunge la registrazione della traccia GPS, che
HyperOS ucciderebbe volentieri.

---

## 8. Roadmap

Come per Cicala, ogni fase produce un APK che fa qualcosa di verificabile.

| Fase | Contenuto | Verificabile con |
|---|---|---|
| **1** | Scaffolding, lettura e scrittura dei CSV, import `.md` con `waypoints`, elenco tappe | apri sul telefono un itinerario che hai già |
| **2** | Diario: posizione GPS, note, foto con la convenzione di nome, check-in, salta/ripristina, aggiungi tappa | una giornata di viaggio registrata senza rete |
| **3** | Import dello storico: le schede del foglio Sheets scaricate in CSV | i dati di oggi entrano nell'app. L'export non serve: i file sono già CSV |
| **4** | Rifornimenti e consumi pieno-a-pieno, autonomia serbatoi | km/l del viaggio scorso |
| **5** | Spese: voci, categorie, totali, divisione, foto scontrino | conto di fine viaggio |
| **6** | Notifica serale 19:00 offline-first: raggruppamento tappe, `BootReceiver`, watchdog, onboarding HyperOS | riavvii il telefono, alle 19:00 arriva |
| **7** | Rifornimento scorta: Open-Meteo, precalcolo tratte OSRM | carichi l'itinerario a casa, in viaggio i dati ci sono |
| **8** | Geocoding inverso offline (GeoNames) + POI offline da OSM | "cosa c'è vicino" in mezzo al nulla |
| **9** | Client AI: Esplora a due strati, diario in prosa, coda delle giornate da narrare | l'ultima cosa che restava su Telegram |
| **10** | Rifiniture: specchio SAF, digest, dettato vocale, parser deterministico | |

Le fasi 1–6 non toccano la rete: **si può arrivare a un'app utile senza scrivere una riga
di codice di networking.** È un buon ordine anche per questo — e mette il client AI in
fondo, dove è un'aggiunta e non un prerequisito.

Nota di scoping: **n8n si può spegnere alla fine della fase 9**, non prima. Fino a quel
momento il bot resta la via per Esplora e per la prosa del diario, e non dà fastidio a
nessuno: legge e scrive su Sheets, l'app sui suoi file.

Sul repository, la scelta è già fatta: questo. CamperLife e Cicala non condividono
dominio né ciclo di rilascio, quindi non condividono codice; condividono conoscenze — lo
schema `AlarmManager` + `BootReceiver` + watchdog, l'onboarding HyperOS, il setup Gradle
e la CI — che si ricopiano in poche centinaia di righe. Accoppiare due app in un unico
repository per riusarle costerebbe più di quanto rende.

---

## 9. Cosa non faremo, e perché

| | Motivo |
|---|---|
| **Motore di instradamento offline** | Uno o due GB di grafo per rifare peggio quello che Organic Maps già fa. Si delega con un intent `geo:` |
| **Mappa disegnata offline nella v1** | Fattibile con MapLibre, ma è il pezzo di lavoro più grande del progetto per un guadagno che elenchi ordinati per distanza coprono in buona parte |
| **Google Sheets e Drive** | Contraddicono l'offline-first e aggiungono OAuth. I file locali sono già CSV: se serve un foglio, si apre quello. La cartella si sincronizza con gli strumenti che lo fanno di mestiere |
| **Modello linguistico sul dispositivo** | Non disponibile o non paragonabile. Il modello si chiama via rete quando serve: sez. 6.2 |
| **Backend proprio per nascondere la chiave API** | Sarebbe l'unico modo per distribuire l'app a estranei, e rimetterebbe in piedi il server che stiamo togliendo. Chiave inserita dall'utente: sez. 6.2 |
| **Lettura dei sensori di bordo** | Dipende da hardware che non sappiamo esserci. Da riaprire con l'informazione in mano |
| **Multiutente** | Il sistema attuale autorizza un solo chat ID. L'app è personale per costruzione |

---

## 10. Da decidere

- **Quale modello per il client AI**, e con quale chiave: Claude o Gemini (sez. 6.2). Su
  Esplora la continuità del prompt esistente è un argomento per Gemini; sul diario in
  prosa la qualità della scrittura è un argomento per Claude. Si può anche usarne uno per
  funzione — il client è lo stesso, cambia l'endpoint.
- **Il prompt di Esplora va trasportato o riscritto?** Trasportarlo è quasi gratis se si
  resta su Gemini. Cambiando modello va comunque riprovato sul campo: è il pezzo del
  sistema con più messa a punto accumulata.
- **Migrazione dello storico**: quanti viaggi ci sono sul foglio Sheets, e vanno importati
  tutti o si parte dal viaggio in corso?
- **Foto anche in galleria?** Album `Pictures/CamperLife` visibile a Google Foto e quindi
  salvato nel cloud, contro archivio autoconsistente in un'unica cartella. Si può fare
  entrambe le cose a costo di spazio doppio.
- **Serve la registrazione della traccia GPS?** Non è nel sistema attuale. È l'unica
  funzione che porterebbe con sé servizio in primo piano, consumo di batteria e battaglia
  con HyperOS: se non serve, meglio non averla.
- **Cosa è installato sul camper** (shunt batteria, pannello di controllo, sensori
  Bluetooth): condiziona il verdetto di 4.6.
- **Copertura del dataset POI**: solo Italia (pochi MB, allegabile all'APK) o Europa
  occidentale (decine di MB, meglio scaricabile per regione)?
- **Nome e `applicationId`**: si tiene *CamperLife*, che è già il nome del sistema (e con
  il bot spento non c'è più niente con cui confondersi). Il dominio `it.camperlife.app`
  collide con l'esistente
  `live.camperlife.app` sul Play Store — irrilevante se l'APK non ci va, da cambiare se
  un giorno ci va.
