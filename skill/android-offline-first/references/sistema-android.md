# Android di sistema: sveglie, HyperOS, build, release

## Sveglie e lavori in background

La distinzione che conta: **`AlarmManager` garantisce *quando*, `WorkManager`
garantisce *che prima o poi*.** Sceglierne uno per il lavoro dell'altro è il
difetto classico.

- un annuncio, una notifica a un'ora precisa → `AlarmManager`
- un controllo di coerenza, una risincronizzazione → `WorkManager`

Per le sveglie vere: **`setAlarmClock`** è la corsia preferenziale, immune a Doze,
e mostra l'icona in barra di stato — che è il segnale con cui l'utente verifica a
colpo d'occhio che è impostata. Per le ripetizioni di una sveglia già scattata,
`setExactAndAllowWhileIdle`: non sono sveglie nuove e non ha senso che ognuna
faccia comparire quell'icona.

Cose imparate a spese proprie:

- **un solo `PendingIntent` per sveglia**, condiviso fra occorrenza principale e
  ripetizioni: così un solo `cancel()` porta via tutto quello che quella sveglia ha
  in agenda, e nessuna ripetizione sfugge perché programmata da un percorso
  diverso. I dati dell'intent devono portare un `Uri` distinto per sveglia —
  senza, Android considera equivalenti i `PendingIntent` di sveglie diverse e ne
  riusa uno solo.
- **il prossimo allarme si programma prima di parlare**, non dopo: se il sistema
  uccide il processo durante l'annuncio, la catena non si interrompe.
- **confronto stretto nel calcolo della prossima occorrenza**: quando una sveglia
  suona e si riprogramma, «adesso» coincide con l'istante appena scattato, e un
  confronto largo la rimette in agenda per lo stesso momento, all'infinito.
- **le sessioni non si riprendono dopo un riavvio**: riaprire una sveglia alla
  terza ripetizione mezz'ora dopo sarebbe più sconcertante che utile.
- **un riavvio cancella tutte le sveglie.** Serve `RECEIVE_BOOT_COMPLETED` e un
  receiver che riprogrammi, e vale la pena ascoltare anche
  `MY_PACKAGE_REPLACED`: un aggiornamento dell'app le cancella allo stesso modo.
- **il watchdog**: un `WorkManager` periodico (sei ore) che controlla se quello
  che dovrebbe essere in agenda c'è ancora, e riprogramma se manca. È la rete di
  sicurezza contro un congelamento silenzioso e costa quasi nulla. Riprogrammare
  deve essere idempotente.

## Xiaomi, HyperOS e i telefoni che uccidono le app

Su HyperOS il codice può essere perfetto e la sveglia non suonare comunque. Va
trattato come **requisito di progetto**, non come dettaglio di supporto. Tre
impostazioni di sistema che nessun permesso Android può sostituire:

| Impostazione | Se manca |
|---|---|
| Avvio automatico (Autostart) | dopo un riavvio nessuna sveglia è più programmata |
| Risparmio batteria: nessuna restrizione | l'app viene congelata dopo poche ore di inattività |
| Blocco nelle app recenti | un «chiudi tutto» accidentale la disattiva |

Da qui due conseguenze implementative:

1. **una schermata di onboarding dedicata**, con un pulsante per passo che apre
   direttamente la schermata di sistema corrispondente (`ACTION_APPLICATION_DETAILS_SETTINGS`
   più gli intent MIUI specifici, con ripiego se l'intent non esiste). Non un testo
   da leggere: pulsanti che portano dove serve. E se il telefono non è uno Xiaomi,
   il pulsante non compare.
2. **lo stato di quelle impostazioni si mostra ma non si inventa**: l'avvio
   automatico non è interrogabile, e una spunta inventata sarebbe peggio di
   nessuna spunta. Tre stati: fatto, da fare, non lo so.

Altri due attriti dello stesso telefono:

- **`adb install` rifiutato** con `INSTALL_FAILED_USER_RESTRICTED` anche a debug
  USB attivo: serve *Installa tramite USB* nelle Opzioni sviluppatore, che Xiaomi
  lega all'accesso con account Mi. In alternativa `adb push` in `/sdcard/Download/`
  e installazione toccando il file dal gestore file.
- **da Android 11 un'app non vede le altre** se non lo dichiara: `resolveActivity`
  risponde `null` anche quando l'app c'è. Servono le `<queries>` nel manifest —
  il minimo indispensabile, non un `*/*`.

## Permessi: chiedere il minimo e dirlo

Dichiara solo quello che usi, e scrivi nel manifest **perché** (o perché *non*):
niente posizione in background se la traccia continua è fuori scope, niente
`CAMERA` se le foto le scatta l'app di sistema a cui passi il file, niente
`SCHEDULE_EXACT_ALARM` se un riepilogo serale tollera qualche minuto di scarto.

Da Android 13 le notifiche si chiedono a runtime, e **senza il permesso la
notifica viene scartata in silenzio**: la schermata delle impostazioni deve dire
se manca.

## La build e la CI

Workflow su ogni push: test unitari → `assembleDebug` → **pubblica l'APK di
debug** → `assembleRelease` → pubblica release e mappa di R8.

L'ordine dei passi conta: **l'APK di debug si pubblica prima dei passi di
release**, così un inciampo di R8 non porta via anche l'artifact che funziona.

Nota la **nota di versione**: `versionName` cambia una volta ogni tante fasi
mentre gli APK si susseguono a ogni push, e «che build ho installato?» è la prima
domanda davanti a un difetto. Metti il commit in un `buildConfigField`:

```kotlin
val commit: String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeUnless { it.isEmpty() } ?: "sviluppo"
```

e mostralo in fondo alle impostazioni. Attenzione: in un build script Kotlin
`java` è la property dell'estensione Gradle, non il nome del package —
`java.util.Properties` non si risolve, va importato in cima.

## La release firmata e R8

- **la chiave di firma** si legge da un `keystore.properties` non versionato
  oppure dalle variabili d'ambiente (la via della CI, dove i segreti arrivano da
  GitHub). Se manca, la compilazione **non** fallisce: l'APK esce non firmato, e
  serve comunque perché è il solo modo di verificare R8 — anche su un fork.
- **la chiave si conserva.** Android accetta un aggiornamento solo se firmato con
  la stessa chiave: perderla significa dover disinstallare per aggiornare.
- **passando dal debug alla release la prima volta l'app va disinstallata** (firme
  diverse), e disinstallare porta via l'area privata. Prima si assegna la cartella
  e si attende lo specchio, dopo si riassegna la stessa e la fusione rimette a
  posto l'archivio. **Dillo prima**, non dopo.
- **le regole di R8 hanno ognuna un motivo scritto accanto.** Le tre categorie che
  servono davvero: i serializzatori generati (kotlinx.serialization), i `Worker`
  che WorkManager costruisce **per nome** da una coda scritta giorni prima, e le
  librerie che registrano i propri gestori per riflessione (Tink sotto
  `EncryptedSharedPreferences`). Sbagliare la terza dà chiavi illeggibili su un
  APK che però compila: il guasto peggiore, perché si scopre dopo
  l'installazione.
- **tieni righe e nomi dei file** nelle tracce (`-keepattributes SourceFile,LineNumberTable`)
  e **pubblica la mappa** accanto all'APK: vale solo per quel build, e senza di
  lei una segnalazione di crash non si legge.
- il risultato tipico: da ~10 MB a ~2 MB.

## Migrazioni

Se usi Room per la configurazione: **scrivi la migrazione a mano** invece di
cancellare e ricreare, dal primo momento in cui i dati contano qualcosa. Non per
quella tabella: per l'abitudine. E verifica la DDL contro lo schema esportato da
Room, che è la definizione autorevole.

Per i file CSV la migrazione non esiste: aggiungere una colonna è compatibile per
costruzione (→ `formato-file.md`). Quando cambia il **significato** di un campo,
aggiungi una colonna nuova accanto alla vecchia e lascia che chi legge usi quella
che trova — due misure diverse dello stesso fatto non si sovrascrivono.
