# Interfaccia: Compose, stato, dialoghi

L'interfaccia è la parte che l'armatura JVM non compila e che le prove non
coprono: qui i difetti sopravvivono. Le regole sotto sono quelle che li hanno
prodotti.

## Lo stato e i callback

### Una domanda in sospeso vive nel ViewModel, la risposta la porta con sé

Il difetto più caro di tutta l'app:

```kotlin
// nel dialogo
onClick = { onChiudi(); onConferma() }
// nel ViewModel
fun scartaProposta() = _stato.update { it.copy(proposta = null) }   // onChiudi
fun conferma() { val p = _stato.value.proposta ?: return /* … */ }  // onConferma
```

`onChiudi()` azzera la proposta, `onConferma()` la rilegge e trova `null`: esce in
silenzio. **Passa il dato come parametro**: `conferma(proposta)`, e nel chiamante
`onConferma = { vista.conferma(proposta) }` dove `proposta` è catturata dal `let`
che mostra il dialogo. Così l'ordine dei due tocchi non conta più.

Il pattern `onChiudi(); onAzione()` è corretto in tutti gli altri dialoghi
**perché là l'azione lavora su un dato catturato dalla lambda** (la voce, la
tappa) che nessuno può azzerare. La differenza sta lì, e va guardata ogni volta.

### Chiudere il dialogo subito, e agire con il dato

Tenere `onChiudi()` prima dell'azione ha un vantaggio: il dialogo sparisce nello
stesso frame e un doppio tocco non può eseguire l'operazione due volte. Con il
dato passato come parametro, è sicuro.

### L'orologio si legge una volta

`remember { LocalDate.now() }`, non `LocalDate.now()` nel corpo del composable:
«meteo di tre ore fa» che diventa «di quattro ore fa» mentre guardi la schermata
è esatto e inquietante. Vale anche per i dialoghi: l'`adesso` si congela
all'apertura, altrimenti i campi precompilati saltano mentre li correggi.

### Ricomporre costa: `remember` con le chiavi giuste

Comporre una scheda che calcola migliaia di distanze va fatto quando cambiano i
dati, non quando cambia una barra di attesa:
`remember(tappa, tappe, poi, tratte, meteo) { … }`.

## Il layout che non si spezza

- **`Row` non manda a capo.** Tre pulsanti con etichette italiane non ci stanno,
  e `Row` non lo dice: schiaccia l'ultimo figlio finché il testo va a capo **una
  lettera per riga**. Usa `FlowRow` (manda a capo il pulsante intero) oppure
  impila in `Column`. Vale a qualunque dimensione di carattere, che è la cosa che
  non si vede provando su un solo telefono.
- **Tutta la riga si tocca, non solo l'etichetta.** Se sotto il nome c'è una riga
  di dettaglio, quella riga è testo inerte: il dito che cade là non fa niente, e
  in un dialogo che si chiude toccando fuori è un modo per non ottenere niente
  credendo di aver risposto. `Column(Modifier.clickable { … })` su tutta la riga.
- **Il contenuto largo scorre dentro il suo contenitore**, non fa scorrere la
  pagina.

## Cosa nascondere e cosa spegnere

**Un pulsante che non c'è non si distingue da una funzione che non esiste.**
Quando un gesto non è disponibile — manca una chiave API, manca la rete — mostralo
**spento** e scrivi da qualche parte perché. La domanda «dov'è il pulsante?» non
ha nessun posto dove trovare risposta, e chi la fa conclude di non aver capito.

Corollario trovato per caso: quando raggruppi due gesti in un `if`, verifica che
condividano davvero la condizione. Un pulsante che rigenera un file **dalle
tabelle locali** era finito dentro `if (aiConfigurata)` solo perché era nato come
l'annullamento di una funzione che usa un modello: senza chiave API, l'unico modo
di rifare quel file era invisibile.

## Il testo che si legge va anche preso

**Un `Text` di Compose non è selezionabile.** Sembra testo, si comporta come un
disegno, e nessun errore lo segnala: la scoperta arriva dall'utente, davanti a una
risposta lunga che voleva mandare a qualcuno. Ogni testo *da leggere* — la risposta
di un modello, un riepilogo, una riga d'esito da cui copiare un identificativo — va
dentro un `SelectionContainer`, e se è lungo merita anche un pulsante che lo copia
intero: una frase la si prende con le dita, la risposta intera con un tocco, e su
un telefono tenuto in una mano il secondo gesto vince.

```kotlin
val appunti = LocalClipboardManager.current
SelectionContainer { Text(testo) }
TextButton(onClick = { appunti.setText(AnnotatedString(testo)) }) { Text("Copia") }
```

Da Android 13 la conferma della copia la mostra il sistema: **non aggiungere un
avviso tuo**, sarebbero due messaggi per lo stesso gesto.

Il caso peggiore è la riga che *serve* per copiare: se mostri l'elenco degli
identificativi visibili a una chiave API e poi non si possono selezionare, la
funzione costringe a ricopiarli a mano — e un carattere sbagliato dà un 404 che
sembra un problema di chiave.

## I testi stanno nel dominio, le stringhe nelle risorse

- **Comporre una frase è logica**: cosa dire, in che ordine, cosa tacere quando
  un dato manca. Va in oggetti puri (`TestoBriefing`, `TestoMeteo`, `Cronaca`) e
  si verifica senza un telefono acceso alle 19:00.
- **Le etichette dell'interfaccia stanno in `strings.xml`**, e i controlli
  statici verificano che esistano e che siano usate.
- Se l'app parla una lingua sola, il testo nel dominio è in quella lingua: se un
  giorno ne parlasse due, quelle funzioni sono le uniche da spostare.

## Avvisi, non silenzio

- Un'operazione che riesce lo dice **coi numeri**: «Trovati e salvati: 34 punti, 5
  località», non «fatto».
- Un'operazione che fallisce dice **cosa** e **quale rimedio**: 429 «aspetta un
  minuto», 504 «i server sono occupati», «ha risposto ma non ho saputo leggerlo:
  è un difetto dell'app, non un problema di rete».
- Un'operazione che non fa niente lo dice: «nessuna data da spostare».
- Le notifiche di sistema e gli avvisi in-app hanno vite diverse: una notifica
  dura tre secondi, e la domanda arriva il giorno dopo. Quello che serve dopo va
  **scritto** (→ `diagnosi-a-distanza.md`).

## Navigazione

- Il tasto indietro ha tanti livelli quanti sono i contenitori aperti: prima
  chiude la scheda, poi il viaggio. Saltarne uno fa uscire da dentro una tappa.
- Uno stato che identifica un elemento tiene **l'id**, non l'oggetto: così la
  schermata segue le modifiche e, se l'elemento sparisce, si torna indietro invece
  di mostrare un fantasma.
- Uno scorrimento laterale (`HorizontalPager`) fra elementi in fila vale due
  tocchi risparmiati per ogni elemento: quando l'ordine ha senso, mettilo.

## Immagini senza librerie

Per un'app che mostra qualche foto locale, due megabyte di dipendenza non si
giustificano: `BitmapFactory` con `inSampleSize` calcolato, una `LruCache`
dimensionata su `maxMemory / 8`, ed `ExifInterface` per l'orientamento. Il
calcolo del fattore di riduzione è aritmetica, quindi va nel dominio e si prova:
è anche il posto dove ho trovato codice e commento che promettevano due cose
diverse.
