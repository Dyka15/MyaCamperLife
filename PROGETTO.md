# CamperLife — Progetto

Cosa è l'app, cosa riceve, cosa produce, cosa fa.

Versione 1, 6 agosto 2026.

Per il *si può fare* — cosa regge offline, cosa no, e con quale tecnologia — vedi
[ANALISI.md](ANALISI.md). Questo documento descrive il prodotto,
non le sue fattibilità.

---

## 1. Cos'è

Un'app Android per chi viaggia in camper: tiene l'itinerario, registra la giornata di
viaggio, calcola consumi e spese, e la sera dice cosa aspettarsi domani.

Sostituisce un bot Telegram appoggiato a workflow n8n, Google Sheets e Google Drive. La
differenza che conta non è l'interfaccia: **il sistema attuale non fa nulla senza
connessione**, e un camper passa buona parte del suo tempo dove la connessione non c'è.

Tre frasi che definiscono il prodotto meglio di un elenco di funzioni:

1. **Registrare un evento non richiede rete, mai.** Foto, nota, rifornimento, spesa,
   check-in: sono scritture su un file locale. Nessuna di queste azioni può fallire
   perché il telefono è offline.
2. **Quello che serve dalla rete si prende in anticipo.** L'itinerario si carica a casa,
   e in quel momento arrivano meteo, distanze e punti di interesse per tutte le tappe.
   In viaggio si consulta una scorta.
3. **I file sono il prodotto, non il contenuto.** L'app scrive CSV e Markdown in una
   cartella del telefono. Si aprono in un foglio di calcolo, si leggono fra dieci anni,
   si danno in pasto a un modello linguistico. Non c'è un dentro da cui esportare.

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
| **Tappa singola** | Form: nome, giorno, posizione (GPS o `lat,lng`), punto di inserimento nell'itinerario | Quando il viaggio cambia in corsa |
| **Check-in** | Un tocco sulla tappa corrente | Arrivando |
| **Foto** | Fotocamera dentro l'app, o condivisione dalla galleria | Sempre |
| **Nota** | Campo di testo, con dettatura vocale offline come alternativa | Sempre |
| **Rifornimento** | Form: chilometri, litri, importo, pieno sì/no | Al distributore |
| **Spesa** | Form: categoria, importo, valuta, foto dello scontrino | Quando si paga |
| **Livelli serbatoi** | Form: acqua caricata, gas sostituito | Al carico |
| **Dati del mezzo** | Form: capacità dei serbatoi, consumi di riferimento, scadenze | Una volta, poi si ritocca |
| **Cartella di archivio** | Scelta una volta con il selettore di sistema | Al primo avvio |
| **Chiave API del modello** | Incollata nelle impostazioni | Una volta, se si vogliono le funzioni AI |

### Dal dispositivo

| Input | Nota |
|---|---|
| **Posizione GPS** | Non richiede rete. Se il primo agganciamento è lento si può usare l'ultima posizione nota, l'EXIF di una foto appena scattata, o digitare le coordinate |
| **Data e ora** | Con fuso orario, per i viaggi all'estero |

### Dalla rete, quando c'è

Nessuno di questi input è necessario al funzionamento: sono scorta.

| Input | Fonte | A cosa serve |
|---|---|---|
| **Previsioni meteo** | Open-Meteo, senza chiave | Riepilogo serale, dossier di tappa |
| **Distanze e tempi di guida** | OSRM | "Prossima tappa: 87 km, 1h 40" |
| **Prezzi carburante** | Open data Osservaprezzi MIMIT | Confronto al distributore |
| **Punti di interesse** | Estratto OpenStreetMap, scaricato una volta per regione | Aree di sosta, carico/scarico, distributori, supermercati |
| **Toponimi** | Dataset GeoNames, allegato o scaricato una volta | Dare un nome alla posizione senza rete |
| **Risposte del modello** | API Claude o Gemini, con ricerca web | Esplora, diario in prosa |

### Dal passato

| Input | Come |
|---|---|
| **Storico esistente** | Le schede del foglio Google Sheets, scaricate in CSV e messe nella cartella. Le colonne coincidono per costruzione |
| **Estratti conto** | CSV della banca, per non ridigitare le spese |

---

## 3. Output

### File nella cartella di archivio

Il prodotto principale. Tutti in `;` con la virgola decimale, apribili in un foglio di
calcolo senza conversioni.

| File | Contenuto |
|---|---|
| `viaggi/<viaggio>/tappe.csv` | Le tappe con stato (`da_fare`, `fatta`, `saltata`) e data di check-in |
| `viaggi/<viaggio>/spostamenti.csv` | Posizioni e check-in — le stesse colonne della scheda di oggi |
| `viaggi/<viaggio>/note.csv` | Le note di viaggio |
| `viaggi/<viaggio>/rifornimenti.csv` | Chilometri, litri, importo, pieno |
| `viaggi/<viaggio>/spese.csv` | Categoria, importo, valuta, cambio |
| `viaggi/<viaggio>/foto.csv` | Nome file, didascalia, coordinate |
| `viaggi/<viaggio>/foto/*.jpg` | Le foto, nominate `foto_AAAAMMGG_HHMMSS[_localita].jpg` come oggi |
| `viaggi/<viaggio>/diario/AAAA-MM-GG.md` | La pagina di diario del giorno |
| `mezzo.json` | Configurazione del camper |
| `digest-AAAA.md` | Riepilogo compatto dell'anno, scritto per essere letto da un modello |
| `FORMATI.md` | Le colonne di ogni file, perché un CSV non si spiega da sé |

### Sullo schermo

Numeri che nel sistema attuale non esistono, perché un foglio non li calcola da solo:

| Output | Come nasce |
|---|---|
| **Consumo del mezzo** | km/l, l/100 km, €/100 km, calcolati solo fra due pieni consecutivi |
| **Autonomia stimata** | Quanti giorni di acqua e gas restano, dedotti dallo storico dei carichi. È una stima da storico, e viene presentata come tale |
| **Costo del viaggio** | Totali per categoria e per giorno, spesa media giornaliera, divisione per persona |
| **Prossima tappa** | Distanza e tempo di guida, dal dato precalcolato o in linea d'aria se non c'è |
| **Avanzamento** | Quante tappe fatte, saltate, da fare |

### Notifiche

| Output | Quando |
|---|---|
| **Riepilogo serale** | Alle 19:00, se attivo: le tappe ancora da fare raggruppate per giorno, fino a tre giorni, con il meteo dell'ultima tappa di ciascuno. Se le previsioni in cache sono vecchie lo dice; se non ci sono, esce comunque con l'elenco delle tappe |

### Prodotti del modello, quando c'è rete

| Output | Dove finisce |
|---|---|
| **Dossier di tappa** | La risposta di Esplora, salvata come file: resta leggibile offline quando si arriva |
| **Pagina di diario in prosa** | Sostituisce la cronaca in `diario/AAAA-MM-GG.md`; la cronaca resta come sorgente |

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
| Aggiungere una tappa scegliendo dove inserirla nell'itinerario | 2 |
| Vedere distanza e tempo di guida verso la prossima tappa | 7 |
| Aprire una tappa nell'app di mappe installata, per la navigazione vera | 2 |
| Tenere più viaggi, e riaprire quelli passati in sola consultazione | 1 |

### 4.2 Diario di bordo

| | Fase |
|---|---|
| Registrare la posizione attuale, con il nome della località | 2 |
| Scattare una foto e allegarla alla giornata, con didascalia facoltativa | 2 |
| Aggiungere una nota di testo, dettandola se si preferisce | 2 / 10 |
| Vedere la giornata come sequenza di eventi in ordine di ora | 2 |
| Generare la pagina di diario del giorno: cronaca ordinata di tappe, posizioni, note, foto, rifornimenti e spese | 2 |
| Far riscrivere quella cronaca in prosa da un modello, quando c'è rete | 9 |
| Sfogliare le giornate passate, filtrare, cercare nel testo delle note | 2 |

### 4.3 Rifornimenti e consumi

| | Fase |
|---|---|
| Registrare un rifornimento: chilometri, litri, importo, se è un pieno | 4 |
| Vedere il consumo per segmento e la media del viaggio | 4 |
| Registrare carichi d'acqua e sostituzioni del gas | 4 |
| Vedere l'autonomia stimata di acqua e gas | 4 |
| Consultare i prezzi dei distributori intorno, con la data del dato in chiaro | 7 |

### 4.4 Spese

Funzione nuova: nel sistema attuale non c'è.

| | Fase |
|---|---|
| Registrare una spesa con categoria, importo e tappa | 5 |
| Allegare la foto dello scontrino | 5 |
| Leggere l'importo dallo scontrino, sul dispositivo | 5 |
| Registrare spese in valuta estera, con il cambio del momento, modificabile | 5 |
| Vedere totali per viaggio, per giorno, per categoria | 5 |
| Dividere le spese fra i componenti dell'equipaggio | 5 |
| Importare un CSV della banca | 5 |

### 4.5 Riepilogo serale

| | Fase |
|---|---|
| Ricevere alle 19:00 le tappe dei prossimi giorni con il meteo | 6 |
| Attivare e disattivare la notifica | 6 |
| Sopravvivere a riavvio del telefono e a HyperOS che congela le app | 6 |
| Avere il riepilogo anche senza rete, con l'età del dato meteo dichiarata | 7 |

### 4.6 Esplora

Due strati: sotto la ricerca locale, che risponde sempre; sopra il modello, quando c'è
rete.

| | Fase |
|---|---|
| Cercare intorno alla posizione: aree di sosta, campeggi, carico e scarico, distributori, supermercati, attrazioni | 8 |
| Ordinare per distanza e aprire il risultato nell'app di mappe | 8 |
| Fare una domanda libera e avere una risposta ragionata con le fonti | 9 |
| Ritrovare la risposta salvata quando si arriva sul posto, offline | 9 |

### 4.7 Impostazioni

| | Fase |
|---|---|
| Configurare il mezzo: serbatoi, consumi di riferimento, scadenze | 4 |
| Scegliere la cartella di archivio | 10 |
| Inserire la chiave del modello, e modificare il prompt di Esplora | 9 |
| Sistemare i permessi e le impostazioni HyperOS, con pulsanti che portano dove serve | 6 |
| Compattare i file quando le correzioni si accumulano | 10 |
| Scaricare i dati offline: toponimi, punti di interesse per regione | 8 |

---

## 5. Le schermate

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
azioni rapide. **Diario** sono le giornate. **Numeri** sono consumi e spese.
**Esplora** è la ricerca nei dintorni.

### Il metro di paragone sono i due tocchi

Nel sistema attuale mandare una foto al bot non richiede comandi: si allega e si invia.
Se nell'app registrare un rifornimento costasse sei tocchi e due schermate, l'app
sarebbe peggiore di quello che sostituisce, per quanto sofisticata.

Da qui le quattro azioni rapide sulla schermata d'apertura, e il vincolo che ciascuna
si chiuda in **un tocco per aprirla e un tocco per confermarla**, con i campi già
compilati con quello che l'app sa: ora, posizione, tappa corrente, e per i rifornimenti
il chilometraggio stimato dall'ultimo noto. Si corregge quello che non torna.

---

## 6. Il ciclo di vita di un viaggio

Come le parti si mettono insieme, in ordine di tempo.

**Prima, a casa, con il wifi.** Si condivide con l'app il file `.md` dell'itinerario.
L'app lo legge, scrive le tappe, e approfitta della connessione per fare scorta:
distanze e tempi fra tutte le tappe, previsioni per ciascuna, punti di interesse della
zona se non ci sono già. Da questo momento il viaggio è autosufficiente.

**In viaggio, senza rete.** Si arriva, si fa check-in, la tappa diventa fatta e compare
la prossima con i chilometri che restano. Durante il giorno si scattano foto, si
annotano cose, si registra il pieno e la sosta pagata. Ogni gesto è una riga accodata a
un file: nessuna attesa, nessun errore possibile per mancanza di rete. Alle 19:00 arriva
il riepilogo di domani, col meteo scaricato quando c'era campo e la sua data in chiaro.

**Quando ricapita il campo.** La scorta si aggiorna da sé. Se in coda ci sono giornate
non ancora narrate, si possono mandare in blocco al modello e tornano le pagine di
diario in prosa. Se si vuole sapere dove dormire domani, Esplora risponde per davvero
invece di limitarsi all'elenco locale.

**Dopo, a casa.** I numeri del viaggio sono già calcolati: consumo medio, costo totale,
spesa al giorno, divisione fra le persone. Le pagine di diario sono file Markdown, le
foto sono file JPEG con la data e la località nel nome. Se serve un grafico che l'app
non fa, i CSV si aprono in un foglio di calcolo. Se si vuole ragionare su tre viaggi
insieme, si dà il digest dell'anno a un modello.

---

## 7. Regole di comportamento

Valgono in ogni schermata, e sono il criterio con cui giudicare se una cosa è fatta bene.

| Regola | In pratica |
|---|---|
| **Scrivere non aspetta niente** | Salvare è un'aggiunta a un file locale, che riesce sempre. Copia nella cartella d'archivio, invii e aggiornamenti della scorta avvengono dopo e possono fallire senza conseguenze |
| **Nessuna schermata dipende dalla rete per esistere** | Un errore di connessione non produce mai una pagina vuota: produce un dato più vecchio, o lo strato locale da solo |
| **L'età di un dato si dichiara** | "Previsione di stamattina alle 9" e non una previsione senza data. Un numero vecchio spacciato per fresco è peggio di un numero assente |
| **Una stima si chiama stima** | L'autonomia dei serbatoi è dedotta dallo storico, non misurata. La distanza in linea d'aria non è la distanza su strada. Va scritto accanto al numero |
| **Correggere non distrugge** | Le modifiche si accodano, non riscrivono. Un file troncato a metà per un telefono che si spegne non deve poter esistere |
| **Niente conta più delle azioni rapide** | Vedi sezione 5 |

---

## 8. Cosa non fa

Perché il confine sia esplicito, non implicito. Le motivazioni stanno nell'analisi,
sezione 9.

| | |
|---|---|
| **Non naviga** | Non disegna mappe e non calcola percorsi: apre la tappa nell'app di mappe offline che quel lavoro lo fa di mestiere |
| **Non sostituisce il modello linguistico** | Non gira un modello sul telefono. Le funzioni generative chiamano un servizio via rete, e senza rete non ci sono |
| **Non legge i sensori del camper** | Livelli e batteria si digitano. Leggerli richiede hardware di cui non si sa la presenza |
| **Non sincronizza da sé** | Nessun cloud, nessun account. La cartella d'archivio può stare dentro una cartella sincronizzata da altri strumenti, e in quel caso i file si fondono senza conflitti |
| **Non è multiutente** | Un telefono, una persona |
| **Non scrive su Google Sheets né su Drive** | Legge i loro CSV per importare lo storico, e produce file che si aprono negli stessi programmi |

---

## 9. Rapporto con il sistema attuale

L'app non va in produzione contro il bot: lo svuota per gradi. Fino a quando le funzioni
generative non sono dentro l'app — fase 9 — il bot resta la via per Esplora e per la
prosa del diario, e i due sistemi convivono senza darsi noia: il bot su Sheets, l'app
sui suoi file, e le colonne che coincidono.

Alla fine si spengono n8n, il token del bot, le Data Table e il webhook. Resta il
telefono, una cartella di file, e due chiamate HTTP per le cose che richiedono qualcuno
all'altro capo.
