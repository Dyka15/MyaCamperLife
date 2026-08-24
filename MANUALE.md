# MyaCamperLife — manuale

Diario di viaggio in camper che **funziona senza rete**. Tutto quello che si legge è già
sul telefono; la rete serve solo a fare scorta in anticipo, e quando manca non si ferma
niente.

---

## 1. Come si usa, in breve

L'app segue il ritmo di un viaggio: si prepara **quando c'è campo**, si usa **quando non
c'è**.

| Quando | Cosa fai |
|---|---|
| **Prima di partire** | importi l'itinerario, scegli la cartella d'archivio, dai i tre permessi |
| **Con la rete** (casa, campeggio, bar) | aggiorni meteo e distanze, cerchi i dintorni delle prossime tappe, chiedi al modello quello che ti serve sapere |
| **In viaggio, senza rete** | check-in, posizione, foto, note, rifornimenti, spese: scrivere non aspetta niente |
| **La sera alle 19:00** | arriva il riepilogo con le tappe dei prossimi giorni e il meteo |
| **A fine giornata** | rileggi il diario, correggi una voce, se vuoi fallo riscrivere in prosa |

La regola che spiega tutte le altre: **la rete serve solo a riempire una scorta**. Quello
che hai scaricato resta, con la sua data scritta accanto — e una previsione di cinque
giorni fa non viene mostrata affatto, perché un dato vecchio spacciato per fresco è peggio
di un dato assente.

---

## 2. Il primo avvio

1. **Importa un itinerario** — il pulsante in basso a destra. È un file `.md` che contiene
   un blocco `waypoints` in JSON: nome, coordinate, giorno, descrizione. Se non ne hai uno,
   crea le tappe a mano con **+ Tappa**.
2. **Scegli la cartella d'archivio** — Impostazioni → *Scegli la cartella*. Da quel momento
   ogni file dell'app viene copiato lì (vedi §8). Scegline una dentro Drive: è quella che
   salva i dati se il telefono cade in mare.
3. **Dai i tre permessi** — in fondo alle impostazioni, sotto *Se il riepilogo non arriva*:
   - **posizione**: serve a registrare dove sei;
   - **notifica**: senza, il riepilogo della sera viene scartato in silenzio;
   - **batteria senza limiti** e, su Xiaomi, **avvio automatico**: senza, il sistema congela
     l'app dopo qualche ora e la sveglia delle 19:00 sparisce.
4. **Km con un pieno** (facoltativo): serve a stimare l'autonomia.

---

## 3. Le quattro schermate

### Viaggio

In cima: dove sei, la prossima tappa, quanti chilometri e quanto si guida. Sotto, le
**cinque azioni rapide** — Posizione, Foto, Nota, Litri, Spesa — che restano ferme mentre
l'itinerario scorre.

L'itinerario è diviso **per giornate**: l'intestazione col giorno e il meteo di quel giorno
resta in cima mentre scorri, un filo verticale lega le tappe, e sopra ogni nome c'è quanto
si guida per arrivarci. Il numero a destra è la posizione nell'itinerario.

I pallini dicono lo stato: **pieno** = fatta, **vuoto** = da fare, **spento e barrata** =
saltata.

### Diario

Tutte le voci registrate, raggruppate per giorno, dalla più recente. Un tocco su una voce
apre cosa puoi farci: correggerla, cambiarne la didascalia, eliminarla.

- **In prosa** riscrive la giornata come un racconto (serve la rete e una chiave di modello).
- **Rigenera diario.md dalla cronaca** rifà il file dai dati registrati: è il modo di
  disfare la prosa, e non ha bisogno di nessuna chiave.
- **Apri diario.md** lo apre con l'editor che preferisci: il diario è un file di testo, e
  puoi portartelo via.

### Numeri

Consumo medio, autonomia stimata, spese per categoria, tratti fra due pieni. Il consumo si
calcola **solo fra due pieni**: è l'unico intervallo in cui si sa quanto carburante è
entrato per quei chilometri.

### Esplora

Cosa c'è nei dintorni di dove sei, per categoria — aree di sosta, campeggi, carico e
scarico, acqua, distributori, supermercati, cose da vedere. **Funziona senza rete** se hai
già cercato quei dintorni. Sopra c'è il campo per chiedere a un modello, che invece la rete
la richiede: la risposta viene salvata e si rilegge offline.

---

## 4. La scheda di una tappa

Un tocco su una tappa la apre; **si scorre di lato** per passare alla successiva.

Dentro c'è tutto quello che l'app sa di quel posto: la descrizione dall'itinerario, il
programma della giornata, il meteo di quel giorno **diviso in mattino, pomeriggio e sera**,
da dove ci si arriva e quanta strada c'è, e i dintorni.

Le azioni:

| | |
|---|---|
| **Sono arrivato** | check-in: la tappa diventa fatta e viene registrata nel diario |
| **Salta** / **Ripristina** | per una tappa che non farai |
| **Annulla il check-in** | disfa un arrivo dato per errore |
| **Sposta le date** | sposta questa tappa e tutte le successive di N giorni |
| **Apri nella mappa** | apre il punto nell'app di mappe scelta (§7) |
| **Cerca i dintorni di questa tappa** | scarica cosa c'è intorno: serve la rete **una volta**, poi resta |
| **Cerca nei dintorni** | chiede a un modello, con il contesto che l'app già conosce |

Nella sezione **Nei dintorni**, ogni riga di categoria («Da vedere · 24») si apre con **Vedi
tutti**: l'elenco completo con nome, in che paese si trova, la distanza. Il tocco su una
riga apre la mappa; il pulsante **Maps** apre la scheda di Google, con orari e recensioni.

---

## 5. Il riepilogo della sera

Alle 19:00 (l'ora si cambia nelle impostazioni) arriva una notifica con le tappe dei
prossimi giorni, il meteo di domani diviso in fasce, e l'avviso di rifornire se i chilometri
di domani superano l'autonomia stimata.

Prima di comporlo l'app prova a scaricare il meteo: se non c'è campo il riepilogo arriva
comunque, con le previsioni della sera prima e la loro età dichiarata.

**Se non arriva**, in Impostazioni ci sono due righe che lo dicono: *Ultimo riepilogo* —
com'è finito, e cosa fare se manca qualcosa — e *Prossima sveglia*, con la data in cui
scatterà. Una data passata lì significa che il sistema ha congelato l'app: è il caso dei tre
interruttori del §2. Il pulsante **Mandala adesso** manda la notifica vera, subito, e serve
a distinguere «non scatta la sveglia» da «la notifica non passa».

---

## 6. Quando il piano cambia

- **Una tappa non si fa più** → aprila e usa *Salta*.
- **Sei in ritardo o in anticipo di giorni** → *Sposta le date* dalla tappa in cui sei: le
  successive slittano insieme. L'app se ne accorge da sola al check-in e te lo propone.
- **Il seguito del viaggio cambia del tutto** → *Carica un itinerario per il seguito*
  (l'icona in alto, o il pulsante in fondo all'elenco). Le tappe **da fare** vengono
  sostituite da quelle del file nuovo; quelle fatte e saltate restano, e **niente di
  registrato si perde** — diario, spese e rifornimenti sono tuoi, non dell'itinerario.

---

## 7. Impostazioni utili

| Voce | A cosa serve |
|---|---|
| **Km con un pieno** | stimare l'autonomia |
| **Riepilogo della sera** e l'ora | accendere/spegnere la notifica serale |
| **App per le mappe** | quale app apre «Apri nella mappa». *Chiedi ogni volta* ripropone il selettore, scavalcando l'app predefinita di Android |
| **Aggiorna meteo e distanze** | fa scorta adesso, prima di entrare in una zona senza campo |
| **Cerca i dintorni di qui** | scarica i punti d'interesse intorno alla posizione attuale |
| **Modelli** | chiavi API, identificativi dei modelli, prompt di Esplora. **Quali modelli vedo?** chiede al fornitore l'elenco che la tua chiave vede davvero |
| **Versione** | numero di build e commit: è la prima cosa da guardare davanti a un difetto |

Le chiavi API non finiscono mai nella cartella dei file: stanno cifrate dentro l'app.

---

## 8. Sincronizzazione: i tre comandi

**Dove stanno i file.** L'app scrive sempre e solo nella **memoria interna** del telefono
(*internal*): è la sua area privata, veloce, che funziona senza permessi e senza rete. La
cartella che hai scelto tu — su Drive — è **esterna** (*external*): è una **copia**, e serve
a due cose, poter aprire i file con un gestore file e sopravvivere alla disinstallazione o
al cambio di telefono.

**La regola di fondo: internal è l'autorità.** External non viene mai letta di sua
iniziativa; è uno specchio, non una sorgente.

### Copia tutto adesso — `internal → external`

Ricopia l'intero archivio fuori, adesso, in una passata sola. **Scrive soltanto**: non
guarda cosa c'è in external, non ne prende niente, non cancella niente.

Copia un file quando **manca o ha dimensione diversa** (le date non si guardano: Drive
riporta quella del caricamento, non del contenuto). Foto e scontrini si copiano solo se
mancano — non cambiano mai, e ricopiarli ogni volta vorrebbe dire ricaricare megabyte su un
cloud.

**Quando serve**: prima di disinstallare o di cambiare telefono, quando vuoi essere certo
che fuori ci sia tutto.

Durante l'uso normale non serve quasi mai: **dopo ogni scrittura** — una nota, un check-in,
una spesa — l'app rispecchia da sola in sottofondo. Questo comando è la versione «tutto,
adesso, e voglio vedere il numero di file».

### Sincronizza — `external → internal`, poi `internal → external`

Due passaggi in un tocco, e il primo è quello che gli altri comandi non fanno mai:

1. **Legge external e ne fa entrare quello che in internal manca.** Le tabelle CSV si
   fondono riga per riga tenendo l'ultima versione di ogni voce; foto, scontrini e risposte
   salvate entrano solo se mancano; `diario.md` si ignora e si rigenera, perché è una vista.
   **Non cancella mai niente**, né dentro né fuori.
2. **Poi ricopia tutto fuori**, come *Copia tutto adesso*, così le due copie finiscono
   allineate.

**Quando serve**: dopo una reinstallazione o su un telefono nuovo — assegni la stessa
cartella e l'archivio torna dentro. È anche il rimedio se hai modificato un CSV a mano dal
computer e vuoi che l'app lo veda.

Le impostazioni vengono prese da external **solo se in internal non hai ancora toccato
niente**: è l'unico caso in cui è certo che quelle di fuori valgono di più.

### Smetti di copiare — spegne lo specchio

Dimentica la cartella scelta: da quel momento l'app scrive solo in internal. **Non cancella
niente** — quello che è già in external resta lì, esattamente com'era, semplicemente smette
di aggiornarsi.

**Quando serve**: se la cartella non va più bene (spazio su Drive, sincronizzazione lenta,
cartella sbagliata). Per riprendere basta assegnarne un'altra, e quell'assegnazione fa una
fusione — cioè si comporta come *Sincronizza*.

### In tabella

| Comando | Verso | Legge external? | Cancella? | Quando |
|---|---|---|---|---|
| **Copia tutto adesso** | internal → external | no | mai | prima di disinstallare o cambiare telefono |
| **Sincronizza** | external → internal → external | **sì** | mai | dopo una reinstallazione, o se hai toccato i file da fuori |
| **Smetti di copiare** | — | no | mai | cambiare cartella, o non volerne più una |

---

## 9. Cambiare telefono, reinstallare

1. Sul vecchio telefono: **Copia tutto adesso**, poi controlla con un gestore file che nella
   cartella ci siano i `.csv` e i `.md`. È l'ultimo momento in cui un errore è reversibile.
2. Sul nuovo: installa l'app, **Scegli la cartella** e indica la stessa. L'assegnazione fonde
   quello che trova: viaggi, voci, foto, impostazioni.
3. Da rifare a mano: le **chiavi API** dei modelli (stanno cifrate nell'app, non nella
   cartella) e i **permessi di sistema**.

---

## 10. Se qualcosa non funziona

L'app scrive **cosa è successo**, invece di lasciartelo indovinare. In Impostazioni:

| Riga | Risponde a |
|---|---|
| *Ultima ricerca* (dintorni) | perché i dintorni sono vuoti |
| *Ultimo itinerario caricato* | cos'è diventato il file che hai importato, e su quale viaggio |
| *Ultimo riepilogo* e *Prossima sveglia* | perché la notifica non è arrivata |
| *Ultima verifica* (modelli) | quali modelli vede la tua chiave |
| *Ultima risposta* | com'è andata l'ultima domanda a un modello, e quante fonti ha citato |

Tutte le righe si selezionano e si copiano. Se qualcosa continua a non tornare, quelle righe
più la versione in fondo alle impostazioni sono esattamente ciò che serve per capire.

**Casi frequenti**

- *I dintorni non si scaricano* → il servizio (OpenStreetMap) a volte rifiuta: riprova più
  tardi, la riga d'esito dice il motivo.
- *Le distanze fra le tappe non compaiono* → mancano le distanze su strada in scorta:
  *Aggiorna meteo e distanze* con la rete.
- *Il meteo delle giornate non c'è* → stessa cosa, o le previsioni sono scadute (oltre tre
  giorni non si mostrano).
- *«Apri nella mappa» apre l'app sbagliata* → Impostazioni → *App per le mappe*.

---

## 11. I tuoi file

Nella cartella d'archivio, un file per genere di dato:

| File | Cosa contiene |
|---|---|
| `tappe.csv` | l'itinerario, con stato e check-in |
| `spostamenti.csv` | arrivi e posizioni registrate |
| `note.csv`, `foto.csv`, `spese.csv`, `rifornimenti.csv` | quello che registri |
| `diario.md` | il diario leggibile, rigenerato dai dati |
| `dossier/` | le risposte dei modelli, salvate |
| `foto/` | le foto e gli scontrini |
| `scorta/` | meteo, distanze, punti d'interesse scaricati |
| `impostazioni.json` | le tue preferenze (mai le chiavi API) |

Sono CSV e Markdown: si aprono con un foglio di calcolo o un editor di testo, e **restano
leggibili anche senza questa app**. È il motivo per cui l'archivio è fatto così.
