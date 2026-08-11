# MyaCamperLife — App Android offline

Analisi di fattibilità per sostituire i workflow n8n + bot Telegram con un'app Android
che funzioni anche senza rete, salvando su file locali.

Versione 3, 6 agosto 2026.

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
| ✅ **Offline pieno** | Itinerario (import, elenco, check-in, salta, aggiungi), diario (posizione, note, foto), rifornimenti, consumi, autonomia residua e avviso di rifornimento, spese, raggruppamento tappe per giorno, briefing serale, tutto lo storico e la sua consultazione |
| 🔶 **Offline con scorta** | Meteo, distanze e tempi di guida, geocoding inverso, POI nei dintorni. Non si calcolano sul posto, ma si possono **scaricare in anticipo** e usare offline: le tappe sono note prima di partire |
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
   della giornata stanno in un Markdown o in un CSV, si danno in pasto al modello quando
   c'è rete, senza alcuna integrazione. L'app non sostituisce l'AI: **la rifornisce**.
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
diverse a seconda dell'evento. Ogni file ha le sue colonne e basta. E se un giorno si
volesse ripescare qualcosa dal foglio Sheets, le colonne coincidono.

```
rifornimenti.csv
id;ts;km;litri;euro;pieno;luogo;lat;lon
b7c2;2026-08-06T18:05:00+02:00;48210;62,3;107,16;si;Orvieto;42,7185;12,1112

spese.csv
id;ts;categoria;descrizione;importo;valuta;cambio;euro;modalita;tappa;lat;lon;scontrino
c1d4;2026-08-06T20:11:00+02:00;sosta;area Il Cipresso;18,00;EUR;;18,00;contanti;Orvieto;;;
e9f1;2026-08-09T20:40:00+02:00;ristorante;;45,00;CHF;1,0600;47,70;carta;Lugano;;;
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

**JSON resta solo dove il CSV non c'entra**: le impostazioni e la scorta meteo, che è
annidata (previsioni orarie) e nessuno aprirà mai in un foglio. La regola: **CSV per i
dati che potresti voler guardare, JSON per la configurazione e la cache tecnica.**

Le chiavi API non stanno in nessuno dei due: vanno nell'archivio cifrato dell'app. La
cartella può finire dentro una cartella sincronizzata su un cloud, e una chiave in chiaro
lì dentro sarebbe un errore difficile da accorgersi.

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
MyaCamperLife/
├── FORMATI.md                     le colonne di ogni file, mezza pagina
├── impostazioni.json              km con un pieno, briefing, modello principale
├── viaggi/
│   └── 2026-08-toscana/
│       ├── viaggio.json           nome, date
│       ├── tappe.csv              waypoint, stato, data di check-in
│       ├── spostamenti.csv        posizioni e check-in
│       ├── note.csv               note di viaggio
│       ├── rifornimenti.csv       km, litri, importo, pieno sì/no
│       ├── spese.csv              categoria, importo, modalità, valuta
│       ├── foto.csv               nome file, didascalia, coordinate
│       ├── foto/
│       │   └── foto_20260806_143012_Orvieto.jpg
│       ├── scontrini/
│       │   └── scontrino_20260806_201100_Orvieto.jpg
│       └── diario.md              un file per viaggio, una sezione per giorno
├── scorta/                        dati scaricati in anticipo (sez. 5)
│   ├── meteo.json                 annidato: resta JSON
│   └── tratte.csv                 da, a, km, minuti
└── poi/europa.sqlite              estratto OSM, sola lettura
```

Non c'è nessuna cartella `esporta/`: **l'esportazione non esiste come passo separato**,
perché ogni file è già nel formato con cui si aprirebbe.

**Il diario è l'unico file che non si accoda soltanto.** Ha una sezione per giorno; una
giornata nuova si aggiunge in fondo, ma rigenerarne una riscrive la sua sezione in mezzo
al file. Si fa scrivendo una copia e rinominandola sopra l'originale — atomico, quindi
nessun file a metà — e non è il percorso frequente, quindi il costo non conta. Gli eventi
grezzi restano nei CSV: una sezione di diario si può sempre ricostruire.

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
| Attrazioni, ristoranti | 🔶 | `tourism=*`, `amenity=restaurant`: nomi e coordinate sì, recensioni e descrizioni no. Utile per "cosa c'è qui", inutile per "dove mangio bene" |
| Supermercati con parcheggio adatto ai camper | 🔶 | I supermercati sì, il giudizio "adatto ai camper" non è un dato che OSM contenga. Al massimo si stima dalla superficie del parcheggio |
| Meteo puntuale | 🔶 | Vedi 5.1 |
| Avvisi stradali: ZTL, limiti di altezza e peso | 🔶 | `maxheight` e `maxweight` esistono in OSM e si possono controllare lungo il percorso; le ZTL sono mappate a macchia di leopardo. La sintesi ragionata di Gemini non è riproducibile |

**Il dataset POI si costruisce a monte, non sul telefono.** Una query Overpass per i tag
che ci interessano, convertita in SQLite con indice spaziale. Solo le categorie elencate,
non tutta OSM: si resta nell'ordine di pochi megabyte per l'Italia.

**L'Italia viaggia dentro l'APK, il resto si scarica per regione.** Così la ricerca locale
funziona sempre e senza preparativi sul territorio dove si viaggia di più, e per un viaggio
all'estero si scarica la regione da casa, prima di partire — che è esattamente il momento
in cui si carica anche l'itinerario. Un pacchetto per regione, non un unico file europeo da
decine di megabyte.

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
| Pagina di diario generata dall'AI | ❌ | La cronaca si genera in locale; la prosa no. Vedi sezione 6 |
| Storico consultabile | ✅ | E consultabile senza rete, che è il punto |

**Le foto restano locali.** Implementare OAuth Google e l'API Drive nell'app è
fattibile, ma aggiunge una dipendenza pesante per replicare un caricamento che offline
non avviene comunque. L'alternativa offline-first: la foto si salva subito nella
cartella del viaggio con il nome giusto, e la cartella specchio (sez. 3) può essere una
cartella già sincronizzata da Drive o Syncthing. Il caricamento diventa un problema del
sistema di sincronizzazione, non dell'app.

**Le foto non vanno in galleria.** Si potrebbero registrare anche in `MediaStore` sotto
un album visibile a Google Foto, che le salverebbe nel cloud da sé; costa poco, ma
raddoppia lo spazio occupato e sparpaglia l'archivio in due posti. Scelto un posto solo.

Ne segue un avvertimento da mettere in interfaccia, non da dare per sottinteso: **così le
foto vivono solo sul telefono.** Per avere una copia altrove, la cartella d'archivio va
scelta dentro una cartella già sincronizzata da Drive o Syncthing. Chi non lo fa non ha
backup, e deve saperlo prima di perdere il telefono, non dopo.

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

**Il campo `giorno` è testo libero, e va letto come tale.** L'itinerario lo scrive chi lo
scrive: arrivano `2026-08-06`, `06/08/2026`, `6 agosto`, `mer 6`. Il lettore riconosce
tutte queste forme, risolve le parziali *in avanti* — un itinerario parla del viaggio che
devi fare — e su quello che non riconosce **restituisce niente**: la tappa finisce fra
quelle senza data e viene comunque nominata nel riepilogo. Metterla nel giorno sbagliato
sarebbe molto peggio che lasciarla senza. Per la stessa ragione `giorno 2` non si
converte: è il secondo giorno di viaggio, e senza sapere quando parti indovinare
significherebbe sbagliare.

**Un riepilogo vuoto non si notifica.** Nessuna tappa in vista e nessun avviso: la
notifica non parte. Una notifica che non porta informazione insegna a ignorare le
notifiche, e la prossima volta che ce n'è una vera nessuno la legge.

**La sveglia si riarma prima di comporre il riepilogo, non dopo.** Se comporre andasse
storto, riarmando in fondo si perderebbe anche il riepilogo di domani, e quello di
dopodomani, per sempre. Una notifica saltata è un guaio di una sera; una catena spezzata
è una funzione che smette di esistere in silenzio.

### 4.6 Consumi e autonomia — funzione nuova

Oggi il diario registra litri e importo, e si fermano lì. Sono dati che chiedono di
essere calcolati, e il calcolo è aritmetica offline.

**Consumo pieno-a-pieno.** Solo i segmenti fra due rifornimenti entrambi marcati
"pieno" danno un consumo valido: i litri di tutti i rifornimenti nell'intervallo,
esclusi quelli del primo pieno, divisi per i chilometri percorsi. I riempimenti parziali
si accumulano nel segmento invece di produrre numeri fantasiosi. Da qui km/l,
l/100 km, €/100 km e €/km.

**Autonomia residua.** Un solo parametro impostato a mano — i **km con un pieno** — meno
i chilometri stimati dall'ultimo pieno. Quei chilometri non vengono da un contachilometri
letto in continuo, che l'app non ha: si ricavano dai punti che l'app ha già.

**Tutti i punti, non solo i check-in.** Ogni posizione registrata e ogni foto portano con
sé delle coordinate. Mettendo in fila in ordine di ora tutto ciò che è stato registrato
dopo l'ultimo pieno — check-in, posizioni salvate, foto — e sommando le distanze fra punti
consecutivi, si cattura anche il movimento fuori itinerario: una gita di 40 km andata e
ritorno viene vista, purché al punto lontano si sia scattata una foto o salvata la
posizione. Che è quello che si fa naturalmente quando si va da qualche parte.

Due accortezze necessarie:

- **Soglia minima fra punti.** Sotto qualche centinaio di metri lo spostamento si ignora,
  altrimenti il rumore del GPS accumula chilometri stando fermi in piazzola.
- **Distanze in linea d'aria fra punti liberi.** Solo le tratte fra tappe hanno il dato
  stradale (sez. 5.2); fra una foto e la successiva si usa l'emisenoverso.

Ne segue una proprietà da dichiarare in interfaccia: la stima resta **ottimista**, perché
se si guida senza registrare nulla quei chilometri sono invisibili. L'avviso "domani serve
rifornire" confronta i chilometri previsti per il giorno dopo con l'autonomia residua, e va
letto come un promemoria, non come una misura.

**Niente livelli di bordo.** Acqua, grigie, gas e batteria non si registrano: fuori scope
per decisione, non per difficoltà tecnica. Cade con essi ogni ragione di leggere sensori
via Bluetooth, che era l'unico pezzo del progetto dipendente da hardware ignoto.

**OBD-II: no.** Il PID del contachilometri non è standard e la resa varia da veicolo a
veicolo. Il chilometraggio si digita: sono tre secondi al rifornimento.

### 4.7 Spese — funzione nuova

Non esiste nel sistema attuale: è una funzione da progettare, non da portare.
Tutto offline tranne un dettaglio.

| Funzione | Verdetto |
|---|---|
| Voci con categoria, importo, tappa | ✅ |
| Modalità di pagamento: contanti, POS, carta di credito | ✅ |
| Totali per viaggio, per giorno, per categoria e per modalità | ✅ |
| Foto dello scontrino | ✅ |
| Lettura automatica dell'importo dallo scontrino | ❌ **Scartata.** Tecnicamente si fa e funziona offline, ma il modello di riconoscimento dentro l'APK lo porta da 9 a 29 MB. Venti megabyte per risparmiare di digitare quattro cifre non valgono il prezzo; l'alternativa leggera scarica il modello al primo uso, e in un'area di sosta senza campo non lo scarica |
| Valuta estera | 🔶 Il cambio è un dato di rete: si salva il tasso *sul momento della registrazione*, modificabile a mano, così la voce resta corretta per sempre senza riconnettersi |
| Pedaggi automatici | ❌ manuali |

**Il carburante non è una spesa.** Sta in `rifornimenti.csv`, che ne chiede già l'importo.
Se stesse anche in `spese.csv` il conto lo conterebbe due volte, e nessuna regola
automatica potrebbe accorgersene — due tabelle scritte a mano non si riconciliano. Il
conto di fine viaggio somma le due, tenendole a vista come due righe distinte: è anche
l'unico modo perché si capisca da dove viene il totale.

**Quello che c'era sullo scontrino non si riscrive mai.** La riga porta `importo` nella
sua valuta e il `cambio` applicato in quel momento; la colonna `euro` è il loro prodotto,
scritta per chi apre il file in un foglio di calcolo. L'app in lettura **rifà il conto**
da `importo` e `cambio`: correggere un cambio sbagliato in un foglio di calcolo aggiorna
il totale, mentre la cifra dello scontrino — l'unica verificabile — resta intatta.

---

## 5. La scorta: rete in anticipo, non al momento

Tre dati sembrano richiedere connettività e non la richiedono, se ci si organizza.

### 5.1 Meteo

Open-Meteo è gratuito, non richiede chiave e restituisce fino a sedici giorni. Le tappe
future sono note. Quindi: **alle 19:00, se c'è connessione, si scaricano le previsioni
per le tappe da domani in avanti e si salvano in `scorta/meteo.json`**; poi il briefing
si compone con quello che c'è, dichiarando sempre l'età del dato ("meteo di ieri sera
alle 19").

Scarico e notifica sono lo stesso lavoro serale, in quest'ordine, e il primo non può far
fallire il secondo: se la richiesta va in errore o in timeout, il briefing esce comunque.
Nelle impostazioni c'è anche un "aggiorna adesso", per quando si sa di stare per entrare
in una zona senza campo.

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

**Come è andata a finire, in fase 7.** I toponimi e i punti di interesse non arrivano da un
dataset dentro l'APK ma dalla stessa scorta della fase 6: **una richiesta a Overpass per
viaggio**, in un corridoio di quindici chilometri intorno alla polilinea delle tappe, che
riporta sia le sette categorie di POI sia i nomi dei paesi. Da quel momento tutto è locale
e offline, esattamente come previsto.

Tre ragioni, in ordine di peso:

1. **Un estratto d'Italia costa dai dieci ai venti megabyte di APK** per servire un viaggio
   che copre trecento chilometri. Avendo appena tolto il riconoscimento del testo per
   tenere l'applicazione a nove megabyte, aggiungerne venti per lo stesso genere di
   comodità sarebbe incoerente.
2. **Il meccanismo esiste già ed è provato.** L'itinerario si importa a casa, dove il campo
   c'è; è la stessa finestra in cui si prendono meteo e tratte. Un solo modo di fare le
   cose invece di due.
3. **Non ho potuto costruire l'estratto.** Geofabrik, GeoNames e Overpass sono tutti fuori
   portata dall'ambiente in cui questo codice è stato scritto, quindi un dataset da
   allegare non esisteva. È una limitazione mia, non un argomento tecnico, e va detta
   insieme alle altre due.

**Cosa si perde.** Se per un viaggio non c'è mai stata rete, Esplora non ha niente da
mostrare e le foto tornano a portare il nome della tappa. La registrazione non peggiora di
un millimetro — quella non ha mai dipeso dalla rete — ma il «✅ offline pieno» di questa
sezione va letto come «offline pieno dopo il primo scarico».

Il formato dei file è CSV come tutto il resto, quindi la variante con l'estratto allegato
resta possibile in seguito **senza cambiare una riga del codice che legge**: basterebbe
riempire `scorta/poi.csv` e `scorta/luoghi.csv` da un'altra parte.

### Il difetto che è vissuto quattro fasi

La richiesta dei dintorni **non ha mai funzionato**, dalla fase 7 alla 10. Chiudeva con
`out center tags;`, e in Overpass `tags` non è un'aggiunta ma un **livello di verbosità** che
*toglie* la geometria. Ogni nodo tornava senza `lat`/`lon` — quindi tutti i distributori, le
fontane, i supermercati e **tutti i toponimi** — e il lettore li scartava, giustamente: un
risultato senza posizione non si può né ordinare per distanza né aprire in una mappa. Zero
punti, zero luoghi, e la sola faccia di un «scorta non aggiornata».

Tre cose lo hanno tenuto in piedi, e vanno scritte perché sono ripetibili:

- **il test asseriva la stringa sbagliata.**
  `assertTrue(query.contains("out center tags;"))` non verificava niente: ricopiava l'errore
  e lo faceva sembrare controllato. È il quarto test difettoso di questo progetto, ed è il
  primo che è costato una funzione invece di mezz'ora
- **l'ambiente in cui il codice è stato scritto non raggiunge `overpass-api.de`**, quindi la
  query non è mai partita per davvero nemmeno una volta. Vale per Overpass come per i
  modelli: quello che non parte non è verificato, e va scritto
- **il fallimento si riduceva a `false`.** Rete assente, servizio occupato, query sbagliata
  e zona deserta finivano tutti nello stesso messaggio, che non suggerisce nessun rimedio e
  soprattutto non distingue **un difetto nostro** da **un problema di rete**

Il rimedio sta su tutti e tre i piani. La query chiude con `out center;`: la verbosità di
riposo, `body`, porta le coordinate dei nodi e i tag di tutto, e `center` aggiunge il centro
a vie e relazioni — esattamente quello che il lettore cerca. Il test adesso **vieta**
`out tags` invece di prescriverlo. E la richiesta riferisce il motivo: 429 «aspetta un
minuto, è un servizio gratuito», 504 «richiesta troppo grande», e il caso nuovo — *ha
risposto con trecento elementi e ne abbiamo salvati zero* — si chiama per nome, perché è la
firma esatta di questo difetto e non deve più poter passare per mancanza di campo.

È l'unica scorta che riporta l'errore, e non per simmetria: meteo e tratte hanno un ripiego
— la previsione vecchia, la linea d'aria — i dintorni no. Se non arrivano, Esplora e le
schede delle tappe restano vuote, e chi guarda deve poter sapere perché.

**E nelle impostazioni il pulsante non c'era affatto.** «Aggiorna adesso» chiedeva meteo e
tratte, mai i dintorni: da lì la funzione non era rotta, era assente. Ora sono due pulsanti
distinti, e restano distinti di proposito — rifare la richiesta più pesante ogni volta che si
aggiorna il meteo vorrebbe dire strapazzare un servizio di cortesia per niente, dato che i
punti di interesse non cambiano di sera in sera.

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
| **Pagina di diario in prosa** | Un template deterministico che compone gli eventi della giornata in Markdown: tappe, posizioni, note, foto, rifornimenti, spese. Non è prosa, è una cronaca ordinata — che è precisamente l'input ideale da dare al modello quando c'è rete. **L'app produce la giornata strutturata, il modello ci scrive sopra.** La sezione del giorno in `diario.md` nasce come cronaca e viene sostituita dalla prosa quando e se si passa dal modello |
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
| **Diario in prosa** | La cronaca strutturata della giornata, generata in locale | Il testo che sostituisce la sezione di quel giorno in `diario.md`; gli eventi restano nei CSV |

**La ricerca web è compresa nel modello, non è un pezzo in più.** È il dettaglio che
rende la cosa semplice: sia Gemini sia Grok espongono uno strumento di ricerca eseguito
lato server. L'app manda una domanda, il modello cerca da sé e risponde con le fonti. Non
serve integrare un motore di ricerca, né riprodurre la catena che oggi vive in n8n.

### Due modelli, uno di riserva

**Gemini principale, Grok di riserva.** Gemini perché è quello che il sistema usa già: il
prompt di Esplora si trasporta senza riscritture. Grok dietro, perché una funzione che
dipende da un solo fornitore è una funzione che sparisce quando quel fornitore ha una
brutta giornata.

| | Ricerca | Token |
|---|---|---|
| **Gemini** (principale) | *Grounding* con Google Search: sui modelli Gemini 3, 5.000 richieste al mese gratuite, poi 14 $ ogni 1.000 ricerche | secondo il modello scelto |
| **Grok** (riserva) | Live Search: 5 $ ogni 1.000 chiamate | Grok 4.5 a 2 $/6 $ per milione di token |

**Si parte sul piano gratuito.** Un modello Gemini di fascia Flash, che ha una quota
giornaliera gratuita, e le 5.000 richieste al mese di ricerca incluse. Per un'app personale
usata durante le vacanze quel tetto non lo si vede. La versione esatta si fissa al momento
di scrivere il client — fase 8 — perché le sigle dei modelli cambiano ogni pochi mesi e
fissarne una oggi significa scrivere una cosa vecchia.

Conseguenza sul secondo modello: **xAI non ha un equivalente gratuito stabile**, quindi la
riserva Grok resta configurabile ma non configurata. Finché la sua chiave manca, l'app dice
che non c'è riserva e lavora con un modello solo — comportamento già previsto, non un caso
speciale. Si accende quando ci sarà un motivo per pagarla.

**Quando scatta la riserva.** Da sola, senza chiedere: errore HTTP, timeout, quota
esaurita. Non su una risposta che semplicemente non piace — quello sarebbe un giudizio, e
l'app non è in grado di darlo. La risposta porta scritto quale dei due l'ha prodotta: se
il tono di una pagina di diario cambia da un giorno all'altro, si deve poter capire
perché.

Nota su chi paga cosa: fra i due, la riserva costa **meno del principale** sulla ricerca.
Quando si passerà a pagare, il ripiego non sarà quello scomodo da evitare.

**Un solo client per entrambi.** Cambiano indirizzo, forma della richiesta e chiave; la
logica — costruisci il prompt, manda, leggi la risposta, salva su file — è la stessa. Il
fallback è un `try` sul secondo indirizzo, non un secondo sottosistema.

Se è configurata una chiave sola, non c'è riserva e l'app lo dice. Se non ce n'è nessuna,
le funzioni AI non compaiono e tutto il resto funziona come sempre.

**Il costo è trascurabile, e conviene dirlo con i numeri.** Una richiesta di Esplora sono
qualche migliaio di token in ingresso e un migliaio in uscita: **pochi centesimi**, ricerca
compresa. Una pagina di diario, senza ricerca, sta sotto il centesimo. Anche con dieci
interrogazioni al giorno per due settimane di viaggio si parla di **pochi euro per
vacanza** — meno di una notte in area di sosta. Il tetto di spesa si imposta comunque sul
pannello di ciascun fornitore.

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

### Com'è andata (fase 8, realizzata)

Il client è quello previsto: un solo `Assistente`, due usi, il fallback come un secondo
tentativo e non come un secondo sottosistema. Quello che è cambiato rispetto al piano, e
perché:

- **Il prompt non è stato trasportato da n8n.** Al momento di scriverlo l'accesso a n8n non
  c'era, quindi il prompt di serie è stato scritto da zero (`Esplora.PROMPT_DI_RIPOSO`)
  partendo dai difetti concreti di questo genere di risposta: elenchi di dieci cose, posti
  senza distanza, aree di sosta inventate, indicazioni stradali che le mappe fanno meglio.
  Resta un valore di riposo: `promptEsplora` nelle impostazioni lo sostituisce, quindi il
  testo originale si può incollare quando torna disponibile, senza ricompilare.
- **Anche gli identificativi dei modelli sono impostazioni**, non costanti. Il piano diceva
  di fissare la versione «al momento di scrivere il client»; fissarla in una costante
  compilata avrebbe solo spostato il problema al primo ritiro. Di riposo
  `gemini-flash-latest` e `grok-4-fast`, correggibili in dieci secondi leggendo l'errore.
- **L'errore del servizio si mostra così com'è.** È l'unica chiamata dell'app che non riduce
  ogni guaio a `null`: una chiave sbagliata, una quota finita e un modello ritirato hanno
  tre rimedi diversi, e sceglierlo richiede leggere quello che il servizio ha risposto. Da
  qui `Rete.postaConEsito` e `EsitoHttp`, accanto alle chiamate mute che bastano al meteo.
- **La coda delle giornate da narrare non è stata fatta.** Senza rete il pulsante dice
  «manca la rete» e la giornata resta cronaca; la si riscrive quando c'è campo. Nessun dato
  si perde — la cronaca è già completa nei CSV, la prosa è una vista — ma è meno di quanto
  il piano promettesse, e vale scriverlo invece di lasciarlo credere.
- **Le chiavi non stanno in `impostazioni.json`.** Quel file viene ricopiato nella cartella
  scelta dall'utente, che può essere sincronizzata su un cloud: una chiave in chiaro lì
  dentro sarebbe un errore difficile da accorgersi. Stanno in `EncryptedSharedPreferences`
  (`archivio/Chiavi.kt`), e nelle impostazioni si vedono solo le ultime quattro cifre. Per
  la stessa ragione la chiave Gemini viaggia nell'intestazione `x-goog-api-key` e non
  nell'indirizzo: un Uri finisce nei log e nella cronologia di un proxy.

**Quello che non è stato verificato, e conviene dirlo.** Le due chiamate HTTPS non sono mai
partite: l'ambiente in cui l'app è stata scritta ha tutti gli host esterni bloccati, e
nessuna chiave era disponibile. Sono verificati per test la costruzione delle richieste e
la lettura di risposte registrate — comprese le due forme in cui Grok restituisce le
citazioni — ma **la prima chiamata vera la farà l'utente**. È anche il motivo per cui
l'errore del servizio si mostra alla lettera: se il nome di un modello è sbagliato o una
chiave è scaduta, la schermata lo dice invece di limitarsi a non funzionare.

### Il rapporto fra app e modello

Vale metterlo per iscritto perché condiziona il progetto: **l'app non compete con il
modello, gli fa da organo di senso e da memoria.** Raccoglie sul campo, senza rete, in un
formato che il modello digerisce; il modello interviene quando c'è connessione e quando
serve giudizio. E se un giorno si vuole ragionare su più viaggi insieme invece che su una
giornata, i file sono già lì da dare in pasto a una conversazione: non serve che l'app
prepari un riassunto in anticipo.

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
        ┌──────────────────────────────────────────────┐
        │  Rifornitore di scorta                       │
        │  Open-Meteo alle 19:00 · OSRM all'import     │
        └───────────────────┬──────────────────────────┘
                            ▼
                    scorta/*   ← letta sempre, mai indispensabile

        ┌──────────────────────────────────────────────┐
        │  Client AI (quando c'è rete e su richiesta)  │
        │  Gemini → se cade → Grok, ricerca compresa   │
        └───────────────────┬──────────────────────────┘
                            ▼
             dossier di tappa · sezioni di diario.md in prosa
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
Android, coperte da JUnit: calcolo pieno-a-pieno, autonomia residua e soglia dell'avviso
di rifornimento, totali delle spese, raggruppamento delle tappe per data, emisenoverso,
parser dei `waypoints`, lettura e fusione dei CSV, sostituzione di una sezione in
`diario.md`. Sono i punti dove un errore silenzioso è peggio di un crash.

### Permessi

```
ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
CAMERA
POST_NOTIFICATIONS
INTERNET / ACCESS_NETWORK_STATE
RECEIVE_BOOT_COMPLETED
RECORD_AUDIO                        solo se si fa il dettato vocale
```

Sette permessi, e l'elenco è notevole per quello che **non** contiene. Niente
`SCHEDULE_EXACT_ALARM` né `USE_EXACT_ALARM` (vedi 4.5), niente permessi di archiviazione
(vedi 3), niente `ACCESS_BACKGROUND_LOCATION` e **nessun servizio in primo piano**: la
posizione si registra solo mentre l'app è aperta, perché la traccia GPS continua è fuori
scope per decisione. È la scelta che tiene l'app fuori dalla categoria di software con cui
HyperOS se la prende.

### HyperOS

Valgono le stesse tre impostazioni documentate per Cicala — avvio automatico, nessuna
restrizione batteria, blocco nelle recenti — con lo stesso onboarding a pulsanti. Qui
sono meno critiche: se salta la notifica delle 19:00 si perde un riepilogo, non una
sveglia. E restano tali, perché senza traccia GPS non c'è niente che debba stare vivo in
background: l'unico appuntamento con il sistema è un allarme serale.

---

## 8. Roadmap

Come per Cicala, ogni fase produce un APK che fa qualcosa di verificabile.

| Fase | Contenuto | Verificabile con |
|---|---|---|
| **1** | Scaffolding, lettura e scrittura dei CSV, import `.md` con `waypoints`, elenco tappe | apri sul telefono un itinerario che hai già |
| **2** | Diario: posizione GPS, note, foto con la convenzione di nome, check-in, salta/ripristina, aggiungi tappa, `diario.md` | una giornata di viaggio registrata senza rete |
| **3** | Rifornimenti, consumi pieno-a-pieno, km con un pieno, autonomia residua | km/l e autonomia del viaggio scorso |
| **4** | Spese: voci, categorie, modalità di pagamento, valuta estera, totali, foto scontrino | conto di fine viaggio |
| **5** | Briefing serale 19:00 offline: raggruppamento tappe, avviso rifornimento, `BootReceiver`, watchdog, onboarding HyperOS | riavvii il telefono, alle 19:00 arriva |
| **6** | Rete: scarico meteo alle 19:00, precalcolo tratte OSRM | il briefing porta il meteo, e i km di domani sono quelli veri |
| **7** | Esplora offline: POI e toponimi da Overpass, scaricati per viaggio | "cosa c'è vicino" in mezzo al nulla |
| **8** | Client AI: Gemini con Grok di riserva, Esplora a due strati, giornate in prosa | l'ultima cosa che restava su Telegram |
| **9** | Specchio nella cartella scelta (SAF), coordinate in un campo, ricerca di un indirizzo | apri `spese.csv` in un foglio di calcolo |
| **10** | Scheda di una tappa: descrizione, meteo del suo giorno, dintorni di quel posto, domanda al modello su quella tappa, scorrimento laterale | tocchi Bolsena tre giorni prima e sai cosa ti aspetta |
| **11** | Correggere e cancellare una voce registrata, dal diario | sbagli il chilometraggio di un pieno e lo aggiusti senza aprire il CSV |
| **12** | Vedere le foto e gli scontrini dentro l'app: miniature nel diario, foto a schermo intero | scorri il diario e riconosci la giornata dalle immagini |
| **13** | Assegnare la cartella **legge** quello che c'è dentro e lo fonde. Invito all'avvio quando manca | reinstalli l'app, indichi la cartella, ritrovi i viaggi |
| **14** | Ritardi e anticipi, giorni senza buchi, contenuto integrale della tappa, date di aggiornamento | arrivi due giorni tardi e l'itinerario si sposta con te |
| **15** | Il programma della giornata dal corpo Markdown dell'itinerario; nota di versione | apri Monaco e leggi le ottocento parole che il file gli dedica |

### Il blocco `waypoints` non è l'itinerario

Un itinerario vero — quello di diciotto giorni fra Baviera, Bratislava e Istria che sta in
`esempi/` — è fatto così: in fondo un blocco JSON con nomi e coordinate, e sopra
**trentatremila caratteri** di programma giorno per giorno. Il 10 agosto a Monaco ha
ottocento parole: orari, durate, cosa vedere e perché, dove si dorme. Nel JSON la stessa
tappa ha `"description": "Bici Englischer Garten, Marienplatz, Residenz, 2 notti"`.

Per quattordici fasi l'app ha letto **solo il JSON**. Il resto del file veniva scartato senza
che nessuno lo dicesse, e la scheda di una tappa mostrava sette parole al posto di ottocento.

Tre decisioni:

- **il legame è il giorno, non la tappa.** Le sezioni sono giornate — `## 6/8 — Giovedì` — e
  in una giornata ci stanno più tappe: il 6 agosto si passa da Lonigo, Garmisch e l'Eibsee,
  e il programma è lo stesso per tutte tre. Non è un'approssimazione: quel testo racconta la
  giornata, non il singolo punto sulla mappa, ed è come il file è scritto.
- **le sezioni che non sono giorni si scartano da sé.** `## RIEPILOGO KM GIORNALIERI`,
  `## DOCUMENTI E CONSIGLI PRATICI`, `## BLOCCO MAPPA` non hanno una data nell'intestazione, e
  `GiornoTappa` restituisce `null`. Nessun elenco di titoli da ignorare, che invecchierebbe al
  primo itinerario scritto diversamente. Sul file vero: diciotto giornate riconosciute,
  quattro sezioni di servizio ignorate, zero falsi positivi.
- **il documento si conserva per intero** in `viaggi/<slug>/itinerario.md`, e il programma si
  rilegge da lì a ogni apertura. Non si copia nelle righe delle tappe: è lo stesso testo per
  tutte le tappe di una giornata, e duplicarlo gonfierebbe `tappe.csv` di qualche kilobyte per
  tappa senza aggiungere niente. E conservare il sorgente significa che se domani la regola
  per capirlo migliora, si applica a quello che c'è già.

### Il difetto dei dintorni, secondo atto

La fase 13 aveva corretto `out center tags`, ma i dintorni **continuavano a non caricare**, e
la ragione era la seconda metà dello stesso errore di lettura.

**Overpass segnala i propri guasti dentro una risposta riuscita.** Quando una query esaurisce
il tempo o la memoria non risponde con un codice d'errore: risponde **200**, con `elements`
vuoto e un campo `remark` che dice cosa è andato storto. Il codice leggeva l'array vuoto e
concludeva «in quella zona non c'è niente segnato» — una spiegazione plausibile, e quindi
credibile, e quindi la peggiore possibile.

E la query *era* troppo cara. Venti tappe, un corridoio di quindici chilometri, e **dieci
statement `nwr` che rivalutavano ognuno da zero il filtro `around` su una polilinea di venti
vertici**: è fra le richieste più costose che si possano fare a un server di cortesia. Due
rimedi, entrambi strutturali:

- **quattro statement invece di dieci.** Le sette categorie stanno su tre chiavi OSM —
  `tourism`, `amenity`, `shop` — e i valori si raggruppano in un'espressione regolare. Un
  test verifica che nessuna categoria si perda nel raggruppamento, perché quello sarebbe il
  modo elegante di rompere tutto.
- **l'itinerario si spezza in fette di sei punti**, come la fase 6 aveva già fatto con OSRM e
  per la stessa ragione: **mezzi dintorni sono meglio di nessun dintorno**. Una fetta che
  fallisce non ferma le altre.

La lezione è la stessa della fase 8 e della 13, e a questo punto vale come regola del
progetto: **un servizio che può fallire deve poter dire come**, e i suoi modi di fallire non
sono sempre dove uno li cerca. Qui non erano nel codice HTTP ma nel corpo della risposta.

### Il difetto dei dintorni, terzo atto: smettere di fare scorta

Dopo i due rimedi strutturali, i dintorni **continuavano a non caricare**. A quel punto la
domanda giusta non era più «cosa c'è di sbagliato nella richiesta» ma **perché quella
richiesta esiste**.

Faceva scorta di tutto l'itinerario in anticipo: un corridoio di quindici chilometri lungo
venti tappe, migliaia di chilometri quadrati, su un server pubblico che è una cortesia. Ogni
rimedio la rendeva meno cara senza cambiarne la natura — restava la richiesta più grande che
l'app potesse formulare, chiesta nel momento in cui nessuno stava guardando il risultato.

**La proposta di chi la usa era di salvare le ricerche invece di anticiparle**, ed è
giusta. Un cerchio di dieci chilometri intorno a *una* tappa è una query che Overpass serve
in un secondo, e la si chiede stando sulla tappa, guardando il risultato. La scorta si
riempie con le ricerche fatte davvero, e resta: quello che hai cercato una volta si rilegge
offline per tutto il viaggio. Non è meno offline-first del prefetch — è più onesto, perché
non promette una copertura che non c'era.

Due cose sono cambiate con lei, e nessuna delle due era «ottimizzazione»:

- **la forma della POST.** La query andava come corpo grezzo `text/plain`; la forma
  documentata dell'API è `data=<query codificata>` con `application/x-www-form-urlencoded`.
  Fra due forme di cui una sola è documentata non c'è ragione di preferire l'altra — e le
  installazioni che non accettano la prima non lo dicono con un errore: rispondono a una
  query vuota, che dal lato dell'app somiglia a «qui non c'è niente».
- **l'esito resta scritto.** `dintorniEsito` e `dintorniProvatoIl` nelle impostazioni: il
  codice HTTP, la frase del server, la data. Perché la diagnostica di un guasto che accade
  in viaggio non può vivere in una notifica di tre secondi né in un logcat — chi viaggia ha
  soltanto il telefono, e la domanda «perché non carica niente?» arriva il giorno dopo.

E una schermata ha smesso di mentire: «niente di segnato nel raggio di venti chilometri»
aveva senso finché una ricerca sola copriva tutto, e non ne ha più adesso che una tappa può
semplicemente non essere stata cercata. Ora dice quello che sa — *niente salvato qui
intorno* — e offre la ricerca.

**La lezione, che è diversa dalle due precedenti:** quando un rimedio dopo l'altro non
guarisce una funzione, il difetto può non stare nell'implementazione ma nel gesto che le si
è chiesto di fare. Tre atti su questa richiesta, e i primi due erano correzioni giuste a una
domanda sbagliata.

### Quarto atto: il server, non la richiesta

La riga di diagnostica ha risposto al primo tentativo vero, e ha chiuso la questione:

> `rifiutata con 504: Error: runtime error: open64: 0 Success /osm3s_osm_base
> Dispatcher_Client::request_read_and_idx::timeout`

Non è un rifiuto della query. Una query troppo cara suona diverso — «Query timed out», «run
out of memory» — mentre questo è il *dispatcher*, il processo che distribuisce i turni di
lettura sul database, che non aveva uno slot libero: congestione, o base dati in
aggiornamento. **Nessuna correzione alla richiesta rimedia a un server che in quel momento
non risponde**, e continuare a limare la query sarebbe stato il quarto rimedio giusto al
problema sbagliato — se non ci fosse stata quella riga a dirlo.

Due conseguenze:

- **tre server invece di uno**, provati in fila e uno per volta, fermandosi al primo che
  risponde: quello ufficiale e due specchi pubblici. Una ricerca che riesce costa una
  richiesta come prima. Un `Vuoto` **non** fa passare al server dopo: «qui non c'è niente» è
  una risposta, e ripeterla su tre server sarebbe strapazzarli per farsi confermare quello
  che il primo ha già detto. Si insiste solo su chi non ha risposto.
- **l'esito scritto dice anche chi ha risposto**, fra parentesi quadre. Con tre server la
  domanda «ha funzionato?» diventa «ha funzionato dove?», e la risposta serve tanto quanto
  l'errore.

E il taglio del corpo d'errore è passato nel dominio: le due righe di licenza ODbL che
Overpass mette in testa a ogni errore si mangiavano i duecento caratteri disponibili, e la
frase che spiega il guasto arrivava tagliata a metà. Ora si taglia da `Error` in poi — e se
quella parola non c'è si tiene tutto, perché un messaggio inatteso è proprio quello che non
va buttato.

### Un giorno è un giorno anche se non ci si sposta

Il riepilogo elencava solo i giorni che avevano una tappa, e un giorno fermo spariva: chi
leggeva vedeva «domani Bolsena, dopodomani Roma» senza sapere che in mezzo c'era un giorno
intero. Ora la finestra si percorre giorno per giorno e un giorno senza tappe dice **dove si
resta** — e «dove sei» viene dall'ultimo check-in, che è un fatto misurato, non dall'ultima
tappa in programma, che è un'ipotesi su cosa è successo. È un test a stabilirlo, dopo che il
primo tentativo aveva scelto l'ipotesi.

All'import, i giorni che l'itinerario salta si segnalano. Non è un errore — il file non è
nostro, e restare fermi un giorno è legittimo — ma se è una dimenticanza vale più scoprirla a
casa che la sera del giorno che manca.

### Ritardi e anticipi

Un check-in in un giorno diverso da quello previsto rende sbagliate **tutte** le date
successive, e con esse il riepilogo della sera e la previsione di ogni tappa. L'app lo misura
e propone di spostare; la soglia è il giorno intero, perché un ritardo di ore si recupera
guidando e proporre di riscrivere l'itinerario per quello sarebbe fastidioso.

Tre scelte deliberate: **si spostano solo le tappe da fare** — quelle spuntate sono storia, e
riscriverne la data falsificherebbe quello che è successo; **si riscrive in forma ISO**,
perdendo il `mer 6` originale, perché l'alternativa sarebbe indovinare come riscrivere ogni
formato; **una tappa senza data leggibile non si tocca**, perché non si sa da dove partire.

E si **chiede**, non si fa: magari il giorno perso lo si recupera domani, magari si salta una
tappa. La proposta porta il numero di tappe interessate, perché «sposto le prossime tre» è una
domanda a cui si può rispondere e «sposto l'itinerario» no.

### Quello che il file diceva e l'app buttava

Il lettore dell'itinerario riconosceva sei campi e **scartava in silenzio tutti gli altri**:
gli orari di un'area, un telefono, una quota, un link. Sono esattamente le cose che servono
arrivando. Ora si conservano tutti, con il nome che avevano nel file e nell'ordine in cui
erano scritti — l'ordine è un'informazione, la prima riga è probabilmente la più importante.

Stanno in una colonna `altro` come JSON compatto. Brutto da guardare in un foglio di calcolo,
ma con due proprietà che qui contano più della bellezza: è senza ambiguità — nessun separatore
da indovinare, nessun valore che contiene il separatore — e **sta su una riga sola**, perché i
ritorni a capo sono già escapati. L'invariante «una riga fisica è un record» resta intatta.

Per la stessa ragione la descrizione ora si scrive con i capoversi escapati (`\n`) invece di
essere schiacciata su una riga: la struttura di un paragrafo è metà di quello che si vuole
leggere arrivando.

### La fusione: la promessa del formato, riscossa

Per dodici fasi lo specchio è andato in una direzione sola, e il costo si vedeva solo nello
scenario peggiore: **reinstalli l'app, indichi la cartella dove stanno già tutti i tuoi
file, e riparti da zero** — con l'aggravante che la prima passata di specchio sovrascriveva
`impostazioni.json` con i valori di riposo. I viaggi non venivano distrutti (nessuna logica
cancella) ma restavano invisibili, che in pratica è lo stesso.

Il rimedio non ha richiesto nessun meccanismo nuovo, ed è il senso di aver scelto questo
formato: `Tabella` dichiara *fondibile* dal primo giorno — «due copie si uniscono
concatenandole e riapplicando la stessa regola» — e la realizzazione sono quindici righe.
Quella promessa non era mai stata messa alla prova; adesso la reggono venticinque test.

Le decisioni che contano, tutte casi in cui sbagliare si scoprirebbe solo dopo aver perso
qualcosa:

- **le lapidi si tengono.** `risolvi` le butta, perché serve a chi legge; `fondi` le
  conserva, perché serve alla prossima fusione. Buttandole, una riga cancellata su un
  telefono e ancora viva nell'altra copia tornerebbe in vita — e tornerebbe **a ogni
  fusione successiva**. La lapide è un fatto quanto la riga che nega.
- **gli allegati si copiano solo se mancano.** Foto, scontrini e dossier non cambiano mai, e
  sovrascrivere un file che non si può rifare è l'unico errore davvero irreparabile.
- **le impostazioni si adottano solo se qui sono intatte.** È l'unico caso in cui si può
  esserne certi: se l'utente non ha ancora toccato niente, qualunque cosa ci sia fuori vale
  più del nulla. Se invece ha già impostato i km con un pieno, sovrascriverli sceglierebbe
  male tanto quanto il codice di prima che li cancellava. L'Uri della cartella **non** si
  prende: appartiene a un'installazione che non c'è più, e il permesso su di esso è perduto.
- **un CSV che questa versione non conosce non si tocca.** Potrebbe venire da una versione
  più nuova dell'app, e fonderlo senza sapere quali colonne abbia significherebbe rovinarlo.
  Le colonne sconosciute delle tabelle note, invece, **sopravvivono**: l'intestazione si
  allarga, come sempre in questo formato.
- **niente si cancella, mai**, né dentro né nella cartella. Una fusione che cancella,
  puntata sulla cartella sbagliata, perde quello che non si può rifare.
- **è idempotente.** Fondere due volte non aggiunge una riga e non fa crescere un file.

**L'app resta l'autorità.** Si legge da fuori, si scrive dentro, poi lo specchio riporta
fuori il risultato: dopo quella singola passata il verso torna quello di sempre, e vale
ancora il secondo invariante — *la scrittura non aspetta niente*.

Una nota su come questo è stato reso verificabile, perché è la ragione per cui `Albero`
esiste: **la fusione è la parte più delicata dell'app** — tocca dati che l'utente non può
ricostruire — e sopra un albero SAF non si può provare, perché SAF non esiste fuori da un
telefono. Dietro quell'interfaccia i test le danno una cartella vera e l'app le dà l'albero
SAF, ed è **la stessa logica**.

**La fase 9 è stata anticipata prima della 8.** Finché i file stanno solo nell'area privata
dell'app, il terzo principio — *i file sono il prodotto* — è vero nel codice e falso in
pratica: si scrivono CSV curati che nessuno può aprire, e disinstallando l'app spariscono.
Il client AI è un'aggiunta; questo era un difetto.

**Lo specchio è una copia, non l'originale.** Su un albero SAF non esiste `append`: ogni
scrittura riapre, rilegge e riscrive il documento intero, senza `fsync` garantito e senza
atomicità. Scrivere direttamente nella cartella scelta farebbe perdere tutte le proprietà
del formato — a prova di crash, correggere non distrugge, fondibile. La copia di lavoro
locale resta l'autorità, e la copia esce differita con `WorkManager`: è esattamente il
secondo invariante dell'architettura, *la scrittura non aspetta niente*.

Le fasi 1–5 non toccano la rete: **si può arrivare a un'app utile senza scrivere una riga
di codice di networking.** È un buon ordine anche per questo — e mette il client AI in
fondo, dove è un'aggiunta e non un prerequisito.

Una dipendenza da tenere presente: l'avviso di rifornimento della fase 5 ha bisogno delle
distanze fra le tappe, che in fase 5 sono ancora la linea d'aria. Funziona, ed è
volutamente ottimista; con la fase 6 diventa preciso. Meglio così che aspettare la rete
per avere l'avviso.

**Come è andata a finire, in fase 6.** Il margine dell'avviso non è una costante sola ma
due: 1,4 sulla linea d'aria, dove le incertezze sono due — distanza sottostimata e
chilometri guidati senza registrare niente — e 1,15 sulle tratte precalcolate, dove ne
resta una. Gonfiare comunque del 40% un numero già vero farebbe suonare l'avviso quando
non serve, ed è così che un avviso smette di essere ascoltato.

**Niente libreria HTTP.** Le due richieste dell'app — meteo una volta al giorno, tratte
una volta per itinerario — non giustificano un paio di megabyte di dipendenza, e sarebbe
in contraddizione con l'aver tolto il riconoscimento del testo per tenere l'APK leggero.
`HttpURLConnection` con tre tetti — connessione, lettura, dimensione della risposta — fa
esattamente quello che serve in ottanta righe.

Nota di scoping: **n8n si può spegnere alla fine della fase 8**, non prima. Fino a quel
momento il bot resta la via per Esplora e per la prosa del diario, e non dà fastidio a
nessuno: legge e scrive su Sheets, l'app sui suoi file.

**Aggiornamento a fase 8 chiusa.** Tutte e nove le fasi sono realizzate, quindi n8n si può
spegnere — con un'avvertenza sull'ordine: le due chiamate ai modelli non sono mai partite
per davvero (ambiente senza rete verso l'esterno, nessuna chiave), quindi conviene provare
una domanda in Esplora e una giornata in prosa **prima** di spegnere i workflow, non dopo.
Tutto il resto dell'app non dipende da quelle chiamate.

**La fase 10 non era in programma, e viene dall'uso.** Le nove fasi hanno riempito l'app di
dati che poi restavano ciascuno nella sua schermata: la descrizione di una tappa era una
riga troncata nell'elenco, il meteo si vedeva solo per domani e solo nel riepilogo delle
19:00, i dintorni solo intorno a dove sei. Nessuna delle tre schermate rispondeva alla
domanda che si fa davvero — *com'è la tappa di dopodomani* — e la risposta **era già tutta
in locale**: bastava metterla insieme. Non c'è nessuna funzione nuova qui dentro, solo una
schermata che non c'era; l'unica riga di rete è il pulsante che chiede al modello, e anche
quello salva la risposta su file perché la si rilegga arrivando sul posto.

Due conseguenze meno ovvie:

- **La previsione di una tappa è quella del suo giorno.** Mostrare su una tappa di giovedì
  il meteo di oggi sarebbe un numero sbagliato messo dove sembra giusto, che è peggio di un
  numero assente. Se la scorta non arriva a quel giorno, la sezione non compare.
- **«Non c'è niente» e «non lo so» si dicono diversamente.** Con la scorta scaricata e zero
  risultati intorno alla tappa si dice che non c'è niente di segnato — è una risposta, e
  anche un avvertimento sul fatto che OpenStreetMap non copre tutto. Senza scorta si dice
  che non è scaricata e si offre di farlo: quello ha un rimedio.

Sul repository, la scelta è già fatta: questo. MyaCamperLife e Cicala non condividono
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
| **Traccia GPS continua** | Sarebbe l'unica funzione a richiedere un servizio in primo piano, notifica permanente, batteria e guerra con HyperOS. La posizione si registra quando la si registra |
| **Foto in galleria** | Un posto solo. Il backup si ottiene scegliendo come cartella d'archivio una cartella già sincronizzata |
| **Livelli di bordo e sensori** | Acqua, grigie, gas e batteria non si registrano, per decisione. Cade con essi ogni ragione di leggere sensori Bluetooth, che era l'unico pezzo dipendente da hardware ignoto |
| **Scheda del mezzo** | Niente libretto, scadenze, tagliandi. Un solo parametro: i km con un pieno |
| **Prezzi dei distributori** | Gli open data ci sarebbero e funzionerebbero offline, ma la funzione non serve |
| **Import del passato** | Nessuna migrazione dallo storico su Sheets, e nessun import di estratti conto. Si parte dal viaggio in corso |
| **Divisione delle spese fra le persone** | L'app registra chi paga come, non chi deve a chi |
| **Multiutente** | Il sistema attuale autorizza un solo chat ID. L'app è personale per costruzione |

---

## 10. Decisioni prese, e cosa resta

I punti aperti della versione precedente sono stati chiusi uno per uno. Restano qui per
memoria: fra sei mesi la domanda "perché non fa X" avrà una risposta scritta.

| Punto | Deciso |
|---|---|
| **Traccia GPS** | **No.** Con essa cadono il servizio in primo piano, la notifica permanente, il permesso di posizione in background e la parte più fastidiosa del problema HyperOS |
| **Copertura POI** | ~~Italia dentro l'APK, altre regioni scaricabili.~~ **Una richiesta a Overpass per viaggio**, in un corridoio intorno all'itinerario: funziona uguale in Italia e all'estero, e non costa venti megabyte di APK. Si prepara da casa insieme a meteo e tratte. Vedi 5.3 |
| **Foto in galleria** | **No, un posto solo.** In cambio va detto in interfaccia che senza una cartella sincronizzata non c'è backup |
| **Km fuori itinerario** | **Si usano tutti i punti registrati**, non solo i check-in: posizioni salvate e coordinate delle foto entrano nella somma. Vedi 4.6 |
| **Prompt di Esplora** | ~~Si trasporta da n8n.~~ **Scritto da zero e reso impostazione.** Alla fase 8 l'accesso a n8n non c'era; il testo originale si incolla nelle impostazioni quando torna disponibile. Vedi 6.2 |
| **Taglia dei modelli** | **Piano gratuito, Gemini di fascia Flash**, `gemini-flash-latest`, riserva `grok-4-fast`. Ma l'identificativo è **un'impostazione e non una costante**: i nomi vengono ritirati, e un ritiro non deve zittire l'app fino al prossimo APK. Vedi 6.2 |
| **`applicationId`** | `it.myacamperlife.app` — non collide con nulla. Rilevante solo se un giorno l'APK va sul Play Store |

### Quello che si decide usando, non progettando

Due cose non si possono sapere a tavolino, e forzarle adesso sarebbe indovinare:

- **Se la stima dell'autonomia basta.** Dipende da quanto spesso si registra qualcosa
  guidando. Se si rivela troppo ottimista, il rimedio è pronto e piccolo: il briefing
  serale chiede il contachilometri quando l'autonomia si avvicina al limite. Da fare
  dopo un viaggio vero, non prima.
- **Se la ricerca locale dei POI regge senza mappa disegnata.** Un elenco ordinato per
  distanza potrebbe bastare, o potrebbe risultare cieco. La mappa con MapLibre resta il
  pezzo di lavoro più grande del progetto e non si affronta per un sospetto.
