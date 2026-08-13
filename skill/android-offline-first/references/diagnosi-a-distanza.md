# Diagnosticare un guasto sul telefono di qualcun altro

Chi usa l'app ha un telefono e nient'altro: nessun logcat, nessun cavo, nessun
computer. Tu hai il codice e non hai il telefono. Questa asimmetria è la
condizione normale, e va progettata invece di subita.

## Progettare per essere diagnosticabile

**Scrivi l'esito delle operazioni che possono fallire in modo invisibile.** Una
notifica dura tre secondi e la domanda arriva il giorno dopo, in mezzo al nulla.
Nel file delle impostazioni (che è testo, e finisce nella cartella dell'utente):

```
dintorniEsito     "rifiutata con 504: Error: runtime error: open64: … [overpass-api.de]"
dintorniProvatoIl "2026-08-13T21:20:11+02:00"
importEsito       "seguito di «Baviera…» (2026-08-baviera-…): 12 fuori, 13 dentro, 9 restate"
importProvatoIl   "2026-08-13T21:30:02+02:00"
```

Tre regole per queste tracce:

1. **Coprono anche i fallimenti e il «non ho fatto niente»**: «file non
   leggibile», «file non capito (SENZA_WAYPOINT)». *Non ha fatto niente* è un
   esito, e non scriverlo lo rende indistinguibile da *ha fatto altro*.
2. **Dicono su cosa hanno agito**, non solo cosa: due viaggi possono chiamarsi
   uguale, e allora il nome non risponde alla domanda «quale?». Metti
   l'identificativo della cartella.
3. **Stanno anche dove nasce la domanda**, non solo nelle impostazioni. Se
   l'utente guarda l'elenco delle tappe per vedere se il file ha fatto quello che
   voleva, la riga va in fondo a quell'elenco: così arriva da sé nel prossimo
   screenshot, senza che nessuno debba andarla a cercare.

Non contengono niente di riservato — un codice HTTP, una frase di un server, dei
conteggi — quindi possono stare in un file che finisce su un cloud.

## Quando arriva «non funziona»

**Prima cosa: prova la catena vera dal tuo lato**, con i file veri dell'utente se
li hai. Nel caso che è costato tre giri, la catena funzionava (12 tappe fuori, 13
dentro) e questo ha ristretto il campo all'interfaccia: senza quella prova avrei
continuato a limare la scrittura.

**Seconda cosa: chiedi le informazioni che discriminano, e dì cosa distinguerà
ciascuna.** Non «mandami un po' di dettagli»: un elenco numerato, in ordine di
quanto è decisivo, dove ogni voce spiega cosa saprai dalla risposta. L'utente sta
in piedi accanto a un camper: la richiesta deve essere eseguibile in due minuti.

Il set che ha funzionato:

1. **la riga della traccia** (copre tutti i casi, compreso «nessuna strada è stata
   percorsa»)
2. **cosa è comparso sullo schermo e cosa hai toccato** — l'unica cosa che non
   puoi ricavare da nessun file
3. **l'elenco dei viaggi / delle voci** — dice se l'operazione è finita su un
   oggetto diverso da quello previsto
4. **cosa c'è nella cartella**: la presenza o assenza di un file che *solo*
   quell'operazione scrive è una prova secca
5. **le ultime righe del CSV**: lapidi e righe nuove si vedono a occhio
6. **il commit in fondo alle impostazioni**, per essere sicuri di parlare della
   stessa build

## Leggere i dati che arrivano

- **Il file è la verità.** Tre righe di `tappe.csv` hanno chiuso in un giro quello
  che due giri di ipotesi non avevano trovato: nessuna lapide, nessuna riga nuova,
  nessun file scritto. Chiedere il file prima costava una frase.
- **Uno screenshot dice più di quanto sembra.** L'icona nuova nella barra prova
  quale build è installata; i pallini vuoti provano che non ci sono check-in; le
  date in forma ISO invece di `13/8` provano che quel file non è quello che hai
  tu. Guardalo per intero, non solo dove l'utente ha indicato.
- **«Tutte le strade non fanno niente» è un indizio, non un mistero**: cerca cosa
  hanno in comune, non cosa hanno di diverso. Era una riga condivisa da tutti i
  pulsanti.
- **Un dato inatteso nel file va spiegato prima di andare avanti.** Timestamp che
  non tornano, date in un formato diverso, un `ts` più vecchio di quello che dovrebbe
  sostituire: ognuno di questi ha una spiegazione, e una di quelle spiegazioni è il
  difetto che stai cercando.

## Cosa non fare

- **Non concludere che il codice è giusto perché le prove passano.** Passavano, e
  la funzione era inerte.
- **Non chiedere all'utente di rifare la stessa prova** senza aver cambiato
  qualcosa che renda il prossimo tentativo più informativo.
- **Non dire «ora funziona» prima che l'abbia visto funzionare.** Di' cosa
  cambierà e cosa guardare; il verdetto è suo.
- **Non spiegare il difetto minimizzandolo.** Se è tuo, dillo in una riga e passa
  al rimedio: «il pulsante chiudeva la domanda prima di risponderle» è
  un'informazione, le scuse no.
