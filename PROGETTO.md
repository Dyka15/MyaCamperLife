# MyaCamperLife — Progetto

Cosa è l'app, cosa riceve, cosa produce, cosa fa.

Versione 3, 6 agosto 2026.

Per il *si può fare* — cosa regge offline, cosa no, e con quale tecnologia — vedi
[ANALISI.md](ANALISI.md). Questo documento descrive il prodotto, non le sue fattibilità.

---

## 1. Cos'è

Un'app Android per chi viaggia in camper: tiene l'itinerario, registra la giornata di
viaggio, calcola consumi e spese, e la sera dice cosa aspettarsi domani.

Sostituisce un bot Telegram appoggiato a workflow n8n, Google Sheets e Google Drive. La
differenza che conta non è l'interfaccia: **il sistema attuale non fa nulla senza
connessione**, e un camper passa buona parte del suo tempo dove la connessione non c'è.

Tre frasi che definiscono il prodotto meglio di un elenco di funzioni:

1. **Registrare un evento non richiede rete, mai.** Foto, nota, rifornimento, spesa,
   check-in: sono righe accodate a un file locale. Nessuna di queste azioni può fallire
   perché il telefono è offline.
2. **Quello che serve dalla rete si prende in anticipo.** Le distanze fra le tappe
   arrivano quando si carica l'itinerario; il meteo ogni sera alle 19:00, se c'è campo.
   In viaggio si consulta una scorta.
3. **I file sono il prodotto, non il contenuto.** L'app scrive CSV e Markdown in una
   cartella del telefono. Si aprono in un foglio di calcolo, si leggono fra dieci anni.
   Non c'è un dentro da cui esportare.

### Chi lo usa

Una persona, sul proprio telefono. Non c'è registrazione, non c'è account, non ci sono
altri utenti: il sistema attuale autorizza un solo ID Telegram, e l'app eredita quella
natura personale. Dispositivo di riferimento: Poco F7, HyperOS, Android 16.

---

## 2. Input

Cosa entra nell'app, da dove, e con quale gesto.

### Dall'utente

| Input | Come entra | Quando |
|---|---|---|
| **Itinerario** | File `.md` con blocco JSON `waypoints` (`name`, `lat`, `lng`, `type`, `giorno`, `description`), condiviso dal gestore file, da Drive o da una chat | A ogni viaggio nuovo |
| **Tappa singola** | Form: nome, giorno, posizione — indirizzo cercato, GPS, o coordinate incollate in un campo solo — punto di inserimento nell'itinerario | Quando il viaggio cambia in corsa |
| **Check-in** | Un tocco sulla tappa corrente | Arrivando |
| **Foto** | Fotocamera dentro l'app, o condivisione dalla galleria | Sempre |
| **Nota** | Campo di testo, con dettatura vocale offline come alternativa | Sempre |
| **Rifornimento** | Form: chilometri, litri, importo, pieno sì/no | Al distributore |
| **Spesa** | Form: categoria, importo, **modalità di pagamento**, valuta, foto dello scontrino | Quando si paga |
| **Km con un pieno** | Un solo numero, nelle impostazioni | Una volta |
| **Cartella di archivio** | Scelta una volta con il selettore di sistema | Al primo avvio |
| **Chiave del modello** | Incollata nelle impostazioni. Una seconda, per la riserva, solo se la si vuole | Una volta, se si vogliono le funzioni AI |
| **Identificativo del modello** | Un campo di testo nelle impostazioni, uno per fornitore | Quando un modello viene ritirato |
| **Prompt di Esplora** | Un campo di testo nelle impostazioni. Vuoto significa «quello di serie» | Se quello di serie non piace |

### Dal dispositivo

| Input | Nota |
|---|---|
| **Posizione GPS** | Non richiede rete. Se il primo agganciamento è lento si può usare l'ultima posizione nota, l'EXIF di una foto appena scattata, o digitare le coordinate |
| **Data e ora** | Con fuso orario, per i viaggi all'estero |

### Dalla rete, quando c'è

Nessuno di questi input è necessario al funzionamento: sono scorta.

| Input | Fonte | Quando arriva | A cosa serve |
|---|---|---|---|
| **Previsioni meteo** | Open-Meteo, senza chiave. Giorno intero **e** tre fasce (mattino, pomeriggio, sera) aggregate dalle ore | **Ogni sera alle 19:00**, se c'è connessione: da domani in avanti | Briefing serale, dossier di tappa |
| **Distanze e tempi di guida** | OSRM | All'import dell'itinerario, e quando si aggiunge una tappa | "Prossima tappa: 34 km, 45 min", e i km previsti per domani |
| **Punti di interesse** | OpenStreetMap via Overpass, in un corridoio di quindici chilometri intorno all'itinerario | Una volta per viaggio, all'import o col pulsante nelle impostazioni | Aree di sosta, campeggi, carico/scarico, acqua, distributori, supermercati, cose da vedere |
| **Toponimi** | La stessa richiesta: i paesi lungo il percorso arrivano insieme ai punti di interesse | Una volta per viaggio | Dare un nome alla posizione senza rete |
| **Risposte del modello** | API Gemini sul piano gratuito, con Grok e Groq di riserva facoltativa | Su richiesta | Esplora, diario in prosa |

Non c'è import di dati preesistenti: si parte dal viaggio in corso.

---

## 3. Output

### File nella cartella di archivio

Il prodotto principale. Tutti separati da `;` con la virgola decimale, apribili in un
foglio di calcolo senza conversioni.

| File | Contenuto |
|---|---|
| `viaggi/<viaggio>/tappe.csv` | Le tappe con stato (`da_fare`, `fatta`, `saltata`), data di check-in, e **ogni campo che l'itinerario portava** nella colonna `altro` |
| `viaggi/<viaggio>/spostamenti.csv` | Posizioni e check-in |
| `viaggi/<viaggio>/note.csv` | Le note di viaggio |
| `viaggi/<viaggio>/rifornimenti.csv` | Chilometri, litri, importo, pieno sì/no |
| `viaggi/<viaggio>/spese.csv` | Categoria, importo, modalità di pagamento, valuta |
| `viaggi/<viaggio>/foto.csv` | Nome file, didascalia, coordinate |
| `viaggi/<viaggio>/foto/*.jpg` | Le foto, nominate `foto_AAAAMMGG_HHMMSS[_localita].jpg` come oggi |
| `viaggi/<viaggio>/itinerario.md` | **Il file dell'itinerario come è arrivato**, per intero: da qui si rilegge il programma di ogni giornata |
| `viaggi/<viaggio>/itinerario-2.md`, `-3.md`… | Gli itinerari caricati dopo, quando si riscrive il seguito del viaggio. Il primo non si butta: racconta i giorni già vissuti. Sui giorni di cui parlano in due vince il più recente |
| `viaggi/<viaggio>/diario.md` | **Il diario del viaggio, un unico file** |
| `viaggi/<viaggio>/dossier.csv` | L'indice delle risposte del modello: domanda, tappa, chi ha risposto |
| `viaggi/<viaggio>/dossier/*.md` | Le risposte per esteso, con le fonti e il contesto che l'app aveva passato |
| `impostazioni.json` | I km con un pieno, il flag del briefing, i modelli scelti, il prompt di Esplora |
| `FORMATI.md` | Le colonne di ogni file, perché un CSV non si spiega da sé |

Le chiavi API **non stanno qui**: vivono nell'archivio cifrato dell'app. La cartella di
archivio può finire dentro una cartella sincronizzata su un cloud, e una chiave in chiaro
lì dentro sarebbe un errore difficile da accorgersi.

### Il diario

Un file per viaggio, `diario.md`, con una sezione per giorno in ordine di data:

```markdown
## giovedì 6 agosto — Orvieto

Arrivo alle 14:12. Duomo nel pomeriggio, foto al tramonto dalla terrazza.
Pieno a Orvieto Scalo: 62,3 litri, 107,16 €. Sosta pagata 18 €.
```

Una giornata nuova si accoda in fondo. Rigenerare una giornata riscrive **solo la sua
sezione**, lasciando intatte le altre. Non si perde nulla in ogni caso: gli eventi grezzi
stanno nei CSV, quindi la cronaca di un giorno si può sempre ricostruire.

Nasce come cronaca ordinata, generata in locale dagli eventi della giornata. Se si chiede
al modello di riscriverla, la prosa prende il posto della cronaca in quella sezione.

### Sullo schermo

Numeri che nel sistema attuale non esistono, perché un foglio non li calcola da solo:

| Output | Come nasce |
|---|---|
| **Consumo del mezzo** | km/l, l/100 km, €/100 km, calcolati solo fra due pieni consecutivi |
| **Autonomia residua stimata** | I km con un pieno meno i km stimati dall'ultimo pieno, ricavati da tutti i punti registrati. È una stima, e viene presentata come tale |
| **Costo del viaggio** | Totali per categoria, per modalità di pagamento e per giorno, spesa media giornaliera |
| **Prossima tappa** | Distanza e tempo di guida, dal dato precalcolato o in linea d'aria se non c'è |
| **Avanzamento** | Quante tappe fatte, saltate, da fare |

### Notifiche

| Output | Quando |
|---|---|
| **Briefing serale** | Alle 19:00, se attivo. Prima prova a scaricare il meteo, poi manda: le tappe ancora da fare raggruppate per giorno, fino a tre giorni, con il meteo dell'ultima tappa di ciascuno, e **l'avviso di rifornimento** se i km di domani superano l'autonomia residua. Senza connessione esce comunque, con il meteo della sera prima e la sua data in chiaro |

### Prodotti del modello, quando c'è rete

| Output | Dove finisce |
|---|---|
| **Dossier di tappa** | La risposta di Esplora, salvata come file: resta leggibile offline quando si arriva. Dentro ci sono anche le fonti, la domanda per esteso e **il contesto che l'app aveva passato** — fra sei mesi, davanti a una risposta rivelatasi sbagliata, la domanda vera è *cosa sapeva l'app quando l'ha chiesto* |
| **Giornata in prosa** | Sostituisce la sezione del giorno in `diario.md`, con sotto una riga che dice da dove viene il testo. Gli eventi restano nei CSV, quindi «rigenera il diario» riporta la giornata a cronaca |

---

## 4. Funzionalità

La colonna *fase* rimanda alla tabella di marcia dell'analisi, sezione 8.

### 4.1 Viaggi e itinerario

| | Fase |
|---|---|
| Importare un itinerario da file `.md`, nello stesso formato di oggi | 1 |
| Vedere le tappe in ordine, con lo stato di ciascuna | 1 |
| Fare check-in sulla tappa corrente: viene marcata come fatta, registrata negli spostamenti, e viene annunciata la prossima | 2 |
| Saltare una tappa, e ripristinarla: lo stesso comando fa le due cose a seconda di com'è adesso | 2 |
| **Spostare l'itinerario** quando si arriva in ritardo o in anticipo di almeno un giorno: l'app lo misura al check-in e lo propone | 14 |
| Aggiungere una tappa scegliendo dove inserirla nell'itinerario | 2 |
| **Riscrivere il seguito del viaggio** caricando un itinerario nuovo: le tappe da fare vengono sostituite, quelle fatte e saltate restano, e niente di registrato si perde | 15 |
| Vedere distanza e tempo di guida verso la prossima tappa | 6 |
| Aprire una tappa nell'app di mappe installata, per la navigazione vera | 2 |
| Tenere più viaggi, e riaprire quelli passati in sola consultazione | 1 |
| Aprire la **scheda di una tappa**: **tutto** quello che il file dice di quella tappa — descrizione con i capoversi e ogni campo in più — meteo del suo giorno, dintorni di quel posto, e scorrimento laterale alla tappa dopo | 10 / 14 |
| Chiedere a un modello dei dintorni **di quella tappa**, e ritrovare la risposta arrivandoci | 10 |
| Aprire una riga dei dintorni ("Da vedere · 24") e leggere **tutti** i punti di quella categoria: nome, in che paese sono, quanto distano, con la mappa offline sulla riga e la scheda di Google Maps accanto | 15 |

### 4.2 Diario di bordo

| | Fase |
|---|---|
| Registrare la posizione attuale, con il nome della località | 2 |
| Scattare una foto e allegarla alla giornata, con didascalia facoltativa | 2 |
| Aggiungere una nota di testo, dettandola se si preferisce | 2 / 9 |
| Vedere la giornata come sequenza di eventi in ordine di ora | 2 |
| Scrivere la giornata in `diario.md`: cronaca ordinata di tappe, posizioni, note, foto, rifornimenti e spese | 2 |
| Far riscrivere una giornata in prosa dal modello, quando c'è rete | 8 |
| Sfogliare le giornate passate, filtrare, cercare nel testo delle note | 2 |
| **Correggere** una voce già registrata: il testo di una nota, la didascalia di una foto, i numeri e la data di un rifornimento o di una spesa | 11 |
| **Cancellare** una voce: si accoda una lapide, la riga originale resta nel file | 11 |
| **Vedere le foto**: una miniatura accanto a ogni voce che ne ha una, e la foto a schermo intero toccandola. Vale anche per lo scontrino di una spesa | 12 |

### 4.3 Rifornimenti, consumi e autonomia

| | Fase |
|---|---|
| Registrare un rifornimento: chilometri, litri, importo, se è un pieno | 3 |
| Vedere il consumo per segmento e la media del viaggio | 3 |
| Impostare i km con un pieno | 3 |
| Vedere l'autonomia residua stimata | 3 |
| Essere avvisati nel briefing serale quando domani serve rifornire | 5 |

**Come si stima l'autonomia.** L'app conosce i chilometri dell'ultimo pieno, perché li
digiti. Per sapere quanti ne hai fatti da allora mette in fila **tutto quello che hai
registrato dopo quel pieno** — check-in, posizioni salvate, foto — in ordine di ora, e somma
le distanze fra punti consecutivi. L'autonomia residua è la differenza fra i km con un pieno
e quel totale.

Usare anche le foto e le posizioni, e non solo i check-in, cambia molto: una gita di 40 km
andata e ritorno viene contata, purché lassù tu abbia scattato una foto o salvato la
posizione. Che è quello che si fa comunque quando si va da qualche parte.

Resta una stima, e lo dice: se guidi senza registrare niente quei chilometri sono
invisibili, quindi il numero tende a essere **ottimista**. L'avviso di rifornimento va letto
come "probabilmente domani ti serve", non come una misura.

### 4.4 Spese

Funzione nuova: nel sistema attuale non c'è.

| | Fase |
|---|---|
| Registrare una spesa con categoria, importo e tappa | 4 |
| Indicare la modalità di pagamento: contanti, POS, carta di credito | 4 |
| Allegare la foto dello scontrino | 4 |
| Registrare spese in valuta estera, con il cambio del momento, modificabile | 4 |
| Vedere totali per viaggio, per giorno, per categoria e per modalità | 4 |

Le modalità di pagamento non sono un'etichetta decorativa: servono a ritrovare una spesa
sull'estratto conto, e a sapere quanti contanti stanno finendo.

### 4.5 Briefing serale

| | Fase |
|---|---|
| Ricevere alle 19:00 le tappe dei prossimi giorni, **senza saltare i giorni fermi**: un giorno senza spostamenti dice dove si resta | 5 / 14 |
| Essere avvisati se domani serve rifornire | 5 |
| Attivare e disattivare la notifica | 5 |
| Sopravvivere a riavvio del telefono e a HyperOS che congela le app | 5 |
| Scaricare il meteo alle 19:00 e mostrarlo accanto alle tappe | 6 |
| Avere il briefing anche senza rete, con l'età del dato meteo dichiarata | 6 |
| Leggere il meteo **diviso in mattino, pomeriggio e sera**: è quando serve saperlo, non quanto in media | 16 |
| Sapere **com'è finito** l'ultimo riepilogo serale e quando scatterà il prossimo, e poterlo mandare adesso per provare la catena | 16 |

### 4.6 Esplora

Due strati: sotto la ricerca locale, che risponde sempre; sopra il modello, quando c'è
rete.

| | Fase |
|---|---|
| Cercare intorno alla posizione: aree di sosta, campeggi, carico e scarico, acqua potabile, distributori, supermercati, attrazioni | 7 |
| Ordinare per distanza e aprire il risultato nell'app di mappe | 7 |
| Fare una domanda libera e avere una risposta ragionata con le fonti | 8 |
| Ritrovare la risposta salvata quando si arriva sul posto, offline | 8 |

### 4.7 Impostazioni

| | Fase |
|---|---|
| Impostare i km con un pieno | 3 |
| Scegliere la cartella di archivio, e copiarci tutto | 9 |
| **Sincronizzare** con la cartella: quello che c'è lì e qui manca entra, poi si ricopia tutto fuori | 13 |
| Vedere **quando** meteo e dintorni sono stati scaricati, e quando la cartella è stata sincronizzata | 14 |
| Vedere **la versione dell'app**, il numero di build e il commit | 15 |
| Inserire la chiave del modello, e quella delle riserve se le si vuole | 8 / 15 |
| Chiedere al fornitore **quali modelli vede la mia chiave**, e ritrovare l'elenco scritto nelle impostazioni | 15 |
| Modificare il prompt di Esplora | 8 |
| Sistemare i permessi e le impostazioni HyperOS, con pulsanti che portano dove serve | 5 |
| Aggiornare meteo e distanze adesso, prima di entrare in una zona senza campo | 6 |
| Scaricare i dintorni: un pulsante a parte, perché è la richiesta più pesante | 7 |
| Compattare i file quando le correzioni si accumulano | **da fare** |

**Cosa è dichiarato qui e non c'è ancora.** Vale scriverlo dentro il documento invece di
lasciarlo scoprire usando l'app:

- **compattare i file.** La funzione che riscrive una tabella tenendo solo l'ultima versione
  di ogni riga esiste ed è verificata, ma nessun pulsante la chiama
- **ingrandire una foto dentro l'app.** Si guarda a schermo intero ma non si zooma né si fa
  scorrere: per leggere una cifra su uno scontrino c'è «Apri con…», che la passa alla
  galleria del telefono
- **filtrare e cercare nel testo delle note** (4.2): c'è solo lo scorrimento per giorno
- **la coda delle giornate da narrare senza rete** (5): il pulsante dice che manca la rete e
  la giornata resta cronaca
- **la dettatura di una nota** (4.2) si appoggia al microfono della tastiera, e non è codice
  di questa app

---

## 5. I due modelli

Le funzioni generative — Esplora e la prosa del diario — chiamano un modello via rete.
Sono configurati **tre fornitori**, con ruoli diversi:

| | | |
|---|---|---|
| **Principale** | Gemini | `gemini-flash-latest` |
| **Riserva** | Grok (xAI) | `grok-4-fast` |
| **Riserva gratuita** | Groq | `groq/compound-mini` |

Si parte sul **piano gratuito**: un Gemini di fascia Flash, che ha una quota giornaliera
gratuita e include 5.000 richieste di ricerca al mese. Per un'app usata in vacanza quel
tetto non si vede.

**Groq non è Grok**, nonostante la lettera: il primo è un servizio che esegue modelli
aperti su hardware proprio, con una fascia gratuita senza carta di credito. Di riposo si usa
un sistema `compound` e non un modello secco, e la ragione è una regola di questa app:
Esplora mostra **le fonti** di una risposta, e su Groq la ricerca web ce l'hanno solo i
`compound` — un `openai/gpt-oss-120b` risponderebbe a memoria, senza un link da controllare.
Chi preferisce la velocità alla verificabilità cambia l'identificativo.

**Gli identificativi dei modelli si cambiano dalle impostazioni**, e non sono compilati
dentro l'app: quelli in tabella sono i valori di partenza, non una scelta definitiva. I nomi
dei modelli vengono ritirati ogni pochi mesi, e un ritiro non deve rendere l'app muta fino
al prossimo aggiornamento: quando succede, la schermata mostra **l'errore del servizio così
com'è** e l'identificativo si corregge in dieci secondi. Per la stessa ragione il prompt di
Esplora è un'impostazione e non una costante.

E per sapere *cosa* scrivere in quel campo c'è **«Quali modelli vedo?»**: chiede al
fornitore l'elenco che quella chiave vede davvero e lo scrive in `impostazioni.json` accanto
alle altre tracce. È la sola risposta autorevole — le guide in rete restano ferme a nomi
ritirati, e un nome ritirato si presenta come un 404 che sembra un problema di chiave.

Le riserve **scattano da sole**, senza chiedere niente, quando il principale dà errore, va
in timeout o ha esaurito la quota: si provano in ordine, saltando quelle senza chiave. La
risposta dice sempre chi ha risposto: se il tono di una pagina di diario cambia, si deve
poter capire perché.

Un solo client per tutti: cambiano l'indirizzo, il formato della richiesta e la chiave. La
ricerca web è compresa nel modello e non c'è un motore di ricerca da integrare — ma **come
si chiede cambia**: Gemini vuole uno strumento dichiarato, Grok un parametro, Groq niente,
perché lì è una proprietà del modello scelto.

Le chiavi si inseriscono nelle impostazioni, e non ne serve più di una: se non c'è riserva
l'app lo dice; se non c'è nessuna chiave, le funzioni AI non compaiono e tutto il resto
funziona come sempre.

---

## 6. Le schermate

Quattro schede in basso, impostazioni in alto. Barra superiore e navigazione stanno nel
contenitore, non nelle singole schermate.

```
┌─────────────────────────────────────────────┐
│  Toscana, agosto 2026            ⚙          │
├─────────────────────────────────────────────┤
│                                             │
│   Sei a Orvieto — arrivato alle 14:12       │
│                                             │
│   Prossima: Bolsena                         │
│   34 km · 45 min                            │
│                                             │
│   ┌────────┬────────┬────────┬────────┐    │
│   │  Foto  │  Nota  │ Litri  │ Spesa  │    │
│   └────────┴────────┴────────┴────────┘    │
│                                             │
│   ✓ Firenze          mer 5                  │
│   ● Orvieto          gio 6                  │
│   ○ Bolsena          gio 6                  │
│   ⤫ Viterbo          ven 7      saltata     │
│   ○ Roma             sab 8                  │
│                                             │
├─────────────────────────────────────────────┤
│  Viaggio   Diario   Numeri   Esplora        │
└─────────────────────────────────────────────┘
```

**Viaggio** è la schermata d'apertura: dove sei, dove vai, l'itinerario, e le quattro
azioni rapide. **Diario** sono le giornate. **Numeri** sono consumi, autonomia e spese.
**Esplora** è la ricerca nei dintorni.

Un tocco su una tappa apre **la sua scheda**, che sta sopra le quattro schede: la
descrizione per intero dall'itinerario, il meteo del **suo** giorno, cosa c'è nei suoi
dintorni, da dove ci si arriva e quanta strada c'è, e un pulsante che chiede al modello di
quel posto. Le righe dei dintorni si aprono: "Da vedere · 24" porta all'elenco delle
ventiquattro, ognuna con il paese in cui si trova — il geocoding inverso lo fa l'app con i
toponimi salvati, senza rete — e con due collegamenti, la mappa del telefono che funziona
offline e la scheda di Google Maps per orari e recensioni. Si scorre di lato per la tappa successiva — un itinerario si legge in fila, e
tornare all'elenco per aprire la tappa dopo sarebbero due tocchi per quello che fa un dito.
Tutto tranne il pulsante funziona senza rete: sono dati che l'app ha già.

### Il metro di paragone sono i due tocchi

Nel sistema attuale mandare una foto al bot non richiede comandi: si allega e si invia.
Se nell'app registrare un rifornimento costasse sei tocchi e due schermate, l'app
sarebbe peggiore di quello che sostituisce, per quanto sofisticata.

Da qui le quattro azioni rapide sulla schermata d'apertura, e il vincolo che ciascuna
si chiuda in **un tocco per aprirla e un tocco per confermarla**, con i campi già
compilati con quello che l'app sa: ora, posizione, tappa corrente, e per i rifornimenti
il chilometraggio stimato dall'ultimo noto. Si corregge quello che non torna.

---

## 7. Il ciclo di vita di un viaggio

Come le parti si mettono insieme, in ordine di tempo.

**Prima, a casa, con il wifi.** Si condivide con l'app il file `.md` dell'itinerario.
L'app lo legge, scrive le tappe, e approfitta della connessione per prendere distanze e
tempi fra tutte le tappe. Da questo momento il viaggio è autosufficiente.

**In viaggio, senza rete.** Si arriva, si fa check-in, la tappa diventa fatta e compare
la prossima con i chilometri che restano. Durante il giorno si scattano foto, si annotano
cose, si registra il pieno e la sosta pagata. Ogni gesto è una riga accodata a un file:
nessuna attesa, nessun errore possibile per mancanza di rete.

**Alle 19:00.** L'app prova a scaricare il meteo da domani in avanti; ci riesca o no,
manda il briefing: le tappe dei prossimi giorni, il meteo che ha (con la sua data), e
l'avviso di rifornimento se i chilometri di domani non ci stanno nell'autonomia residua.

**Quando ricapita il campo.** Se una giornata merita più di una cronaca, si manda al
modello e torna in prosa dentro `diario.md`. Se si vuole sapere dove dormire domani,
Esplora risponde per davvero invece di limitarsi all'elenco locale.

**Dopo, a casa.** I numeri del viaggio sono già calcolati: consumo medio, costo totale
per categoria e per modalità, spesa al giorno. Il diario è un unico file Markdown che si
legge dall'inizio alla fine. Le foto sono file JPEG con data e località nel nome. Se
serve un grafico che l'app non fa, i CSV si aprono in un foglio di calcolo.

---

## 8. Regole di comportamento

Valgono in ogni schermata, e sono il criterio con cui giudicare se una cosa è fatta bene.

| Regola | In pratica |
|---|---|
| **Scrivere non aspetta niente** | Salvare è un'aggiunta a un file locale, che riesce sempre. Copia nella cartella d'archivio, invii e scarico del meteo avvengono dopo e possono fallire senza conseguenze |
| **Nessuna schermata dipende dalla rete per esistere** | Un errore di connessione non produce mai una pagina vuota: produce un dato più vecchio, o lo strato locale da solo |
| **L'età di un dato si dichiara** | "Meteo di ieri sera alle 19" e non un meteo senza data. Un numero vecchio spacciato per fresco è peggio di un numero assente |
| **Una stima si chiama stima** | L'autonomia residua è dedotta dalle tappe fatte, non misurata. La distanza in linea d'aria non è la distanza su strada. Va scritto accanto al numero |
| **Correggere non distrugge** | Le modifiche si accodano, non riscrivono: correggere una voce aggiunge una riga con lo stesso identificativo, cancellarla aggiunge una lapide, e l'originale resta leggibile nel file. Un file troncato a metà per un telefono che si spegne non deve poter esistere |
| **Si sa chi ha risposto** | Quando parla un modello, si vede quale dei due |
| **Niente conta più delle azioni rapide** | Vedi sezione 6 |

---

## 9. Cosa non fa

Perché il confine sia esplicito, non implicito. Le motivazioni stanno nell'analisi,
sezione 9.

| | |
|---|---|
| **Non naviga** | Non disegna mappe e non calcola percorsi: apre la tappa nell'app di mappe offline che quel lavoro lo fa di mestiere |
| **Non registra la traccia del percorso** | La posizione si salva quando la salvi tu. Niente notifica permanente, niente batteria consumata in sottofondo |
| **Non mette le foto in galleria** | Stanno in un posto solo, la cartella del viaggio. Per averne una copia altrove, scegli come cartella d'archivio una cartella già sincronizzata: **altrimenti le foto vivono solo sul telefono** |
| **Non tiene i livelli di bordo** | Acqua, grigie, gas, batteria: non si registrano. L'unico consumo che l'app segue è il carburante |
| **Non fa da scheda del mezzo** | Nessun libretto, nessuna scadenza, nessun tagliando. Un solo parametro: i km con un pieno |
| **Non conosce i prezzi dei distributori** | Si guardano dove si guardano oggi |
| **Non divide le spese fra le persone** | Registra chi paga come, non chi deve a chi |
| **Non importa il passato** | Si parte dal viaggio in corso. Lo storico su Sheets resta dov'è, leggibile lì. Un archivio **di questa app** invece si recupera: indicare la cartella che lo contiene ne fa entrare i viaggi |
| **Non importa estratti conto** | Le spese si registrano quando si fanno |
| **Non gira un modello sul telefono** | Le funzioni generative chiamano un servizio via rete, e senza rete non ci sono |
| **Non sincronizza da sé** | Nessun cloud, nessun account. La cartella d'archivio può stare dentro una cartella sincronizzata da altri strumenti, e in quel caso i file si fondono senza conflitti |
| **Non è multiutente** | Un telefono, una persona |
| **Non scrive su Google Sheets né su Drive** | Produce file che si aprono negli stessi programmi |

---

## 10. Rapporto con il sistema attuale

L'app non va in produzione contro il bot: lo svuota per gradi. Fino a quando le funzioni
generative non sono dentro l'app — fase 8 — il bot resta la via per Esplora e per la prosa
del diario, e i due sistemi convivono senza darsi noia: il bot su Sheets, l'app sui suoi
file.

Alla fine si spengono n8n, il token del bot, le Data Table e il webhook. Resta il
telefono, una cartella di file, e le chiamate di rete per le cose che richiedono qualcuno
all'altro capo.

**Dove siamo adesso.** Tutte le fasi sono realizzate, quindi il bot si può spegnere. Con
un'avvertenza sull'ordine: le due chiamate ai modelli non sono ancora mai partite per
davvero, quindi conviene provare una domanda in Esplora e una giornata in prosa **prima**
di spegnere i workflow, non dopo. Tutto il resto dell'app non dipende da quelle chiamate.
