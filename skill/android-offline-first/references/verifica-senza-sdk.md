# Verificare senza l'SDK Android

Il caso normale in un ambiente di lavoro remoto: `dl.google.com` è bloccato,
quindi **non esiste un SDK Android**, quindi `./gradlew` non arriva nemmeno a
configurare il progetto. Senza contromisure si scrive codice alla cieca e il
compilatore lo vede per la prima volta in CI, dieci minuti dopo il push.

Tre strumenti, in ordine di quanto pescano:

## 1. L'armatura JVM

Un progetto Gradle **kotlin("jvm")** separato, fuori dal repository, che compila
ed esegue i sorgenti **senza dipendenze Android** — cioè il dominio puro e la
parte dell'archivio che lavora su `java.io.File`. In pratica il 60-70% del codice
e il 100% delle prove che contano.

Vive nella cartella di lavoro temporanea, non nel repository: è
un'infrastruttura di verifica, non un pezzo del prodotto.

```bash
scripts/armatura.sh crea    # scheletro: build.gradle.kts, settings, cartelle
scripts/armatura.sh sync    # copia i sorgenti elencati in armatura.txt
scripts/armatura.sh prova   # gradle test --offline
```

`armatura.txt` elenca i file da specchiare, uno per riga, con i percorsi
relativi alla radice del repository. Si costruisce per sottrazione: metti tutto,
compila, e togli quello che tira dentro `android.*` o `androidx.*`.

**La sincronizzazione è a senso unico: repository → armatura.** Questa è la
regola più importante e l'unica che ho violato: una volta ho aggiunto un import
mancante *nella copia dell'armatura*, la mia suite è passata, e la CI è caduta
sul repository. Se serve una correzione, si corregge il repository e si
risincronizza.

Cosa l'armatura **non** vede: Compose, i ViewModel, `Uri`, `Context`, SAF,
`BitmapFactory`. Per quelli servono i controlli statici e la lettura attenta,
e restano il posto dove i difetti sopravvivono più a lungo.

## 2. I controlli statici

`scripts/controlli.py`, dalla radice del progetto. Fa quattro cose, tutte nate da
un guasto:

- **import Compose mancanti.** Il compilatore Kotlin non li deduce, e in
  un'interfaccia Compose ce ne sono decine per file. Un `Row` senza import è un
  errore di CI di dieci minuti.
- **import di asserzioni JUnit mancanti nei test.** Un `assertNotNull` senza
  import non lo vede nessuno finché la CI non cade: è esattamente cosa è
  successo.
- **stringhe**: usate ma non definite (crash a runtime o errore di compilazione)
  e definite ma non usate (residui di una funzione cambiata). Il secondo controllo
  è quello che tiene pulito il file: quando cambi una funzione, la stringa vecchia
  te la ricorda lui.
- **XML delle risorse valido.** Un `&` non escapato in una stringa italiana ferma
  la compilazione.

Aggiungi controlli al file quando un difetto ti sfugge due volte: è la stessa
logica di una prova di regressione.

**La tabella dei simboli contiene solo simboli di primo livello.** Ci ho messo
`stickyHeader`, che è un *membro* di `LazyListScope` e non si importa: il
controllo ha chiesto un import inesistente, io l'ho aggiunto, e la CI è caduta
su quello. Un controllo statico sbagliato è peggio di un controllo mancante —
il primo non trova un difetto, il secondo ne fabbrica uno, e con l'autorità di
uno strumento. Prima di aggiungere un simbolo alla tabella, verifica che
esista davvero un `import` che lo porta.

## 3. La CI come compilatore

Il workflow gira i test unitari, poi `assembleDebug`, poi `assembleRelease`. Si
segue per `head_sha`, non per «l'ultimo run»:

```bash
until curl -sS "https://api.github.com/repos/<owner>/<repo>/actions/runs?per_page=5" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in d['workflow_runs']:
    if r['head_sha'].startswith('<sha-breve>'):
        print(r['id'], r['status'], r['conclusion'])
        sys.exit(0 if r['status']=='completed' else 1)
sys.exit(1)
"; do sleep 30; done
```

Lancialo **in background** e usa l'attesa per rileggere il diff: dieci minuti di
CI sono dieci minuti di revisione gratis. Quando cade, i log del job dicono la
riga esatta (`e: file:///...`): non aggiungere `--stacktrace` al workflow, seppellisce
la riga utile sotto duecento righe di interni Gradle.

La dimensione dell'artifact si legge dall'API e si riferisce all'utente: è il
modo più semplice di accorgersi che qualcosa è cresciuto di colpo.

## Le prove: cosa provare e con cosa

- **Il dominio puro va provato in modo esaustivo**: è dove stanno le regole, e
  costa poco. Prendi la data come parametro, mai `LocalDate.now()`.
- **L'archivio si prova su una cartella temporanea vera.** Niente mock: crea,
  scrivi, rileggi, e verifica anche il **testo del file** quando la promessa
  riguarda il file (la virgola decimale sopravvive? la lapide c'è?).
- **Usa i file veri dell'utente come fixture.** Metti i documenti che carica
  davvero in `esempi/` e portali nel classpath delle prove invece di copiarli:

  ```kotlin
  android { sourceSets { getByName("test") { resources.srcDir("../esempi") } } }
  ```

  Due copie dello stesso file divergono, e a divergere sarebbe quella che le
  prove leggono. Una prova su trenta kilobyte di Markdown scritto da un altro
  trova cose che nessun documento inventato da chi scrive il codice trova.
- **Prova anche il filo.** Le prove sulla logica passano mentre la funzione è
  inerte, se nessuno prova il collegamento fra interfaccia e archivio. Quando non
  puoi provarlo in automatico (ViewModel, Compose), almeno **rileggi il percorso
  completo** dal tocco alla scrittura, ad alta voce, cercando chi azzera cosa.
- **Quando una prova e il codice non concordano, chiediti chi ha ragione.** Più
  volte aveva ragione la prova, e una volta l'aveva il codice ma il commento
  prometteva un'altra cosa: la contraddizione fra codice e commento è un difetto
  come gli altri.

## Le versioni si muovono insieme

Un aggiornamento sbagliato costa una giornata. La tabella va tenuta in un
documento del progetto e verificata quando se ne cambia una:

| Componente | Vincolo |
|---|---|
| Gradle | lo richiede AGP; il wrapper dichiara la versione, così locale e CI usano la stessa |
| Android Gradle Plugin | richiede un JDK preciso (8.7 → JDK 17) |
| Kotlin | il plugin Compose segue la versione di Kotlin |
| Compose BOM | fissa in blocco tutte le librerie Compose: non versionarle a mano |
| compileSdk / targetSdk | possono restare sotto la versione del telefono di prova |
| minSdk | scegli alto se l'app è personale: meno compatibilità da scrivere |

Non aggiornare le dipendenze alla fine di una fase, quando non c'è modo di
collaudarle. Lint segnalerà che esistono versioni più nuove: è un promemoria, non
un ordine.
