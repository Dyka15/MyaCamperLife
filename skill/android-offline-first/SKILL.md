---
name: android-offline-first
description: Come costruire app Android personali in Kotlin e Jetpack Compose che funzionano senza rete e tengono i dati in file leggibili (CSV, Markdown) invece che in un database opaco. Usa questa skill ogni volta che il lavoro riguarda un'app Android — anche solo "aggiungi una schermata", "compila l'APK", "leggi questo file di dati sul telefono", "la sveglia non suona su Xiaomi", "i dati sono spariti dopo la reinstallazione" — e in particolare quando l'ambiente di sviluppo non ha l'SDK Android, quando l'APK arriva all'utente via GitHub Actions, o quando l'utente prova l'app da telefono e riferisce difetti a parole. Contiene il formato dei file, il modo di verificare il codice senza SDK, le trappole di Compose e dello stato, la sopravvivenza su HyperOS, e il metodo per diagnosticare un guasto che accade sul telefono di qualcun altro.
---

# App Android offline-first, in Kotlin e Compose

Questa skill raccoglie quello che è costato un difetto ciascuno in due app vere
(un diario di viaggio in camper, una sveglia parlante), scritte con un utente che
le prova sul campo e riferisce a parole cosa non va. Non è un manuale di Android:
è l'insieme delle regole che non si deducono dalla documentazione e che si
imparano una volta sola, se si scrivono.

## Quando serve

Un'app Android personale, per una persona o una famiglia, che deve funzionare
dove la connessione non c'è; i dati contano e devono restare leggibili anche fra
dieci anni; l'APK si consegna via CI perché l'utente non compila; e chi la usa
non ha un computer sotto mano quando qualcosa non va.

Se invece stai lavorando a un'app con backend, account e sincronizzazione
server, molte regole qui sotto restano valide (il formato dei file, le trappole
di Compose, HyperOS) ma i quattro principi no: quelli descrivono un'app che non
ha un dentro da cui esportare.

## I quattro principi

Tutto il resto discende da questi. Quando una decisione è dubbia, si decide
guardando qui.

1. **Registrare non richiede rete, mai.** Una foto, una nota, una spesa, un
   check-in sono righe accodate a un file locale. Nessuna di queste azioni può
   fallire perché il telefono è offline, e nessuna aspetta una risposta.
2. **Quello che serve dalla rete si prende in anticipo.** Meteo, distanze,
   punti di interesse: si scaricano quando c'è campo e diventano una *scorta*
   locale. **Nessuna schermata aspetta la rete.** Una scorta dichiara sempre la
   propria età, e oltre una certa età non si mostra affatto — una previsione di
   cinque giorni fa non è un dato vecchio, è un dato sbagliato.
3. **I file sono il prodotto.** CSV e Markdown in una cartella del telefono, che
   si aprono in un foglio di calcolo. Non c'è un "dentro" da cui esportare:
   quello che vedi nell'app è una vista di quei file.
4. **Ogni numero dichiara com'è nato.** Una stima si chiama stima, una distanza
   in linea d'aria si dice tale, un dato derivato si ricalcola in lettura e non
   si memorizza. Se il numero peggiore è quello vero, si mostra quello.

## Il ciclo di lavoro

Lavora **a fasi**, e ogni fase è verticale: dominio puro → archivio su file →
interfaccia → prove → CI verde → APK all'utente. Una fase che tocca solo il
dominio non si consegna: l'utente non può provarla.

Dentro una fase, l'ordine che funziona:

1. **Scrivi il dominio puro per primo.** Le regole — cosa dire, cosa tacere,
   come si compone una frase, chi vince fra due righe — vanno in funzioni senza
   Android: prendono la data invece di leggere l'orologio, prendono i dati
   invece dei file. Anche la composizione dei testi (`TestoBriefing`,
   `TestoMeteo`, `Cronaca`) sta lì: decidere cosa scrivere è logica, e si
   verifica senza un telefono.
2. **Poi l'archivio**, che scrive quelle strutture su file rispettando il
   formato (→ `references/formato-file.md`).
3. **Poi l'interfaccia**, che è sottile: legge lo stato e chiama il ViewModel.
   Le trappole vere sono qui (→ `references/interfaccia-compose.md`).
4. **Le prove coprono anche il filo, non solo i nodi.** Vedi la regola sul
   codice «troppo semplice per sbagliarsi», più sotto.
5. **Controlli statici e armatura di compilazione** prima di ogni push
   (→ `references/verifica-senza-sdk.md`).
6. **CI verde, poi riferisci** all'utente: cosa fa la funzione, cosa deve
   provare, e la dimensione dell'APK. Il numero di run e i test verdi sono
   fatti, non promesse.

## Le regole che sono costate un difetto ciascuna

Ognuna di queste è nata da un guasto vero. Sono in ordine di quanto sono state
care.

### Il dato di una domanda in sospeso si passa, non si rilegge

Un dialogo che chiede conferma tiene la domanda nello stato del ViewModel. Il
pulsante che risponde faceva `onChiudi(); onConferma()` — come tutti gli altri
dialoghi — ma `onChiudi()` **cancella la domanda dallo stato**, e `onConferma()`
la rileggeva da lì: trovava `null` e usciva in silenzio. Quattro pulsanti su
quattro inerti, per tre segnalazioni di fila.

Il rimedio non è invertire due righe: è `confermaX(proposta)` invece di
`confermaX()`. **Un dato che arriva come parametro non può essere cancellato da
chi lo passa**, e l'ordine dei tocchi torna a non contare. Invertire le righe
avrebbe funzionato lasciando la trappola armata per il prossimo dialogo.

### Un gesto che non fa niente non deve poter passare inosservato

Quel difetto è vissuto tre segnalazioni perché il codice diceva
`?: return` — usciva senza scrivere e senza dire. In un comando (non in una
lettura) l'uscita silenziosa è un difetto in sé: annota, avvisa, o almeno lascia
una traccia. **Se una funzione può non fare niente, deve poterlo dire.**

### Il codice non testato è quello che sembra troppo semplice per sbagliarsi

Le prove coprivano la scrittura su file — la parte delicata, nove casi sui file
veri dell'utente — e non il filo che la raggiunge: il ViewModel, il dialogo, il
collegamento. La logica era giusta e la funzione era inerte. Quando una funzione
"non funziona" e le prove passano, **il difetto è nel pezzo che nessuno ha
pensato di provare.**

### Una funzione non esiste finché non esiste il gesto per invocarla

La sostituzione dell'itinerario era realizzata, verificata e spinta in un APK. Il
pulsante stava in fondo a un elenco di ventidue righe, e l'utente ha usato quello
che conosceva — «Importa» — ottenendo quello che quel pulsante fa. Nessun errore
da nessuna parte, e una funzione che «non funziona».

Corollario: **quando lo stesso gesto può significare due cose, l'app non
indovina, chiede.** «Carico un file di itinerario» può voler dire *un viaggio
nuovo* o *il seguito di questo*: la domanda porta i numeri veri («escono 12
tappe, ne entrano 13, le 9 fatte restano»), perché «sostituisco l'itinerario?»
non è una domanda a cui si possa rispondere.

### Un servizio che può fallire deve poter dire come — e i suoi modi di fallire non sono dove li cerchi

Overpass segnala i propri guasti **dentro una risposta 200**: `elements` vuoto e
un campo `remark`. Letto come "qui non c'è niente", ha prodotto quattro fasi di
dintorni vuoti con una spiegazione plausibile e quindi credibile. E quando la
richiesta è stata corretta, il guasto successivo era un 504 col *dispatcher*
congestionato: non una query da correggere, un server che in quel momento non
risponde. → `references/rete-e-guasti.md`

### Quando un rimedio dopo l'altro non guarisce, il difetto può essere nel gesto chiesto

Tre atti di correzioni giuste sulla stessa richiesta, e la quarta volta la
domanda giusta non era «cosa c'è di sbagliato» ma «**perché quella richiesta
esiste**». Faceva scorta di tutto l'itinerario in una query enorme; ora cerca un
punto per volta, quando lo si chiede, e salva quello che trova. Non è meno
offline-first: è più onesto, perché non promette una copertura che non c'era.

### Chi risolve «vince l'ultima riga» deve scrivere un `ts` che vince davvero

Con un orologio indietro — telefono appena riaccesso, fuso preso male — la riga
nuova perde contro quella che vuole sostituire: il gesto sembra riuscito e non
cambia niente. Ogni scrittura che sostituisce una riga passa da una funzione che
garantisce un istante posteriore a quello della riga sostituita.

### Una scelta giusta su un caso previsto può rendere irreversibile un caso non previsto

«Non si salta un posto in cui sei stato» è una regola giusta, e ha reso un
check-in dato per sbaglio **impossibile da annullare**, portandosi dietro dove
sei, la prossima tappa, il riepilogo della sera e il nome delle foto. Per ogni
gesto che scrive, chiediti: *e se l'utente l'ha toccato per errore?*

### Una riga d'aiuto che manda in un posto vuoto è peggio di nessuna riga

«Lo stato di una tappa si cambia dalla sua scheda» — dove non si cambiava. Fa
cercare, e chi cerca conclude di non aver capito. Le stringhe che spiegano vanno
verificate come il codice: se indicano un gesto, quel gesto deve esistere.

### Il difetto sta anche nella regola, non solo nel file

Il diario intestava le giornate col posto sbagliato, e rigenerarlo lo riscriveva
identico: la vista era corretta, la regola no (il ripiego era «dove sei
adesso», un anacronismo per un giorno passato). Quando rigenerare non guarisce,
il difetto è a monte del file.

## Prima di dire «fatto»

- [ ] i controlli statici passano (`scripts/controlli.py`)
- [ ] l'armatura JVM compila ed esegue tutte le prove del dominio e dell'archivio
- [ ] ogni nuova stringa è usata, e ogni stringa usata esiste
- [ ] ogni gesto nuovo è raggiungibile da dove nasce la domanda, e l'ho detto
      all'utente in una riga
- [ ] nessun comando esce in silenzio senza fare niente
- [ ] i dati che l'utente non può ricostruire non vengono mai riscritti in
      luogo: si accoda, e la riga di prima resta nel file
- [ ] la CI è verde, e ho riferito il numero del run e la dimensione dell'APK
- [ ] se non ho potuto provare qualcosa da qui (rete bloccata, SDK assente),
      l'ho detto esplicitamente invece di lasciarlo intendere

## Come parlare con chi la usa

- **Riferisci i fatti con i numeri**: run verde, N test, APK di X MB. Se una
  cosa non l'hai potuta verificare, dillo in una riga.
- **Quando l'utente dice «non funziona», non tirare a indovinare più di una
  volta.** Chiedi le tre o quattro informazioni che discriminano, e dì cosa
  distinguerà ciascuna. → `references/diagnosi-a-distanza.md`
- **Il file è la verità.** Chiedi il CSV, non l'impressione: tre righe di file
  hanno chiuso in un giro quello che due giri di ipotesi non avevano trovato.
- **Le sue proposte di solito sono giuste**, perché usa l'app e tu no. Quando
  propone di cambiare approccio («salva le ricerche invece di anticiparle»,
  «voglio scrivere il parziale, non il contachilometri»), la richiesta contiene
  un'informazione sul mondo che non hai.

## Riferimenti

Leggili quando serve, non tutti in una volta.

| File | Quando |
|---|---|
| `references/formato-file.md` | Prima di toccare qualunque scrittura su file: il contratto completo (append-only, `id`/`ts`/`cancellato`, lapidi, fusione, specchio SAF) |
| `references/verifica-senza-sdk.md` | Quando l'SDK Android non c'è o la rete blocca `dl.google.com`: l'armatura JVM, i controlli statici, come si segue la CI |
| `references/interfaccia-compose.md` | Prima di scrivere schermate e dialoghi: stato, trappole dei callback, layout che non si spezza, cosa nascondere e cosa spegnere |
| `references/rete-e-guasti.md` | Ogni volta che si chiama un servizio esterno, in particolare uno gratuito |
| `references/sistema-android.md` | Sveglie, notifiche, HyperOS/Xiaomi, versioni allineate, CI, release firmata e R8 |
| `references/diagnosi-a-distanza.md` | Quando il guasto accade sul telefono di qualcun altro |

Gli script sono pronti all'uso:

- `scripts/controlli.py` — import Compose e JUnit mancanti, stringhe assenti o
  non usate, XML delle risorse valido. Da lanciare dalla radice del progetto.
- `scripts/armatura.sh` — crea e sincronizza il progetto JVM con cui si
  compilano e provano i sorgenti senza Android.
