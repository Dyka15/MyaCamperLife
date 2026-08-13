# Il formato dei file

I file **sono** il prodotto: l'app è una vista di una cartella. Da questo
discende tutto il resto, e ogni regola qui sotto serve a una promessa precisa —
*i dati che l'utente non può ricostruire non si perdono mai.*

## Il CSV, riga per riga

- **separatore `;`**, decimali con la **virgola**, UTF-8. È il dialetto che un
  foglio di calcolo italiano apre con un doppio clic senza chiedere niente.
- **un file per tipo di record**: `tappe.csv`, `note.csv`, `spese.csv`,
  `rifornimenti.csv`. Non una tabella universale con una colonna «tipo».
- **solo `append`.** Correggere è accodare una riga con lo stesso `id`;
  cancellare è accodare una **lapide** (`cancellato` = vero). La riga di prima
  resta scritta: è il motivo per cui offrire «correggi» e «cancella» non fa
  paura.
- **colonne obbligatorie**: `id` (breve, otto caratteri bastano), `ts` (istante
  ISO con fuso, quando la **riga** è stata scritta), `cancellato`.
- **`ts` non è quando è accaduto il fatto.** Chi registra stasera lo scontrino
  di ieri ha bisogno di due istanti diversi: aggiungi una colonna `istante` per
  *quando è accaduto*. Confonderli significa che correggere una riga vecchia la
  sposta nel diario di oggi.
- **niente `\n` dentro un campo.** Un testo lungo si scrive con le sequenze di
  escape (`\\` e `\n`) e si rilegge consumandole: una riga di CSV deve restare
  una riga, altrimenti `wc -l` mente e un editor la rompe.
- **l'intestazione si legge per nome**, non per posizione. Aggiungere una colonna
  non è una migrazione: i file vecchi non ce l'hanno e valgono comunque.
- **niente database.** I volumi sono migliaia di righe; un archivio opaco
  contraddirebbe il terzo principio. (Room va bene per dati di configurazione —
  vedi Cicala — ma non per quello che l'utente vuole poter leggere.)

## La risoluzione: chi vince

Leggere una tabella significa:

1. raggruppare per `id`
2. per ogni gruppo tenere la riga col `ts` più alto
3. scartare il gruppo se quella riga è una lapide

Da qui due conseguenze da non dimenticare mai:

- **il `ts` di una riga che sostituisce deve battere quello sostituito.** Non
  basta `now()`: con un orologio indietro la riga nuova perde e il gesto sembra
  riuscito senza cambiare niente. Passa da una funzione dedicata:

  ```kotlin
  private fun dopoDi(riga: Riga, adesso: OffsetDateTime): OffsetDateTime {
      val precedente = runCatching { OffsetDateTime.parse(riga.ts) }.getOrNull() ?: return adesso
      return if (adesso.isAfter(precedente)) adesso else precedente.plusNanos(1_000_000)
  }
  ```

  Vale anche quando si riscrivono molte righe in un colpo: allora l'istante deve
  battere **la più recente di tutte**.

- **i valori derivati non si memorizzano**, si ricalcolano in lettura: litri =
  euro ÷ prezzo, euro = importo × cambio. Così correggere il prezzo in un foglio
  di calcolo aggiorna il consumo, e la cifra dello scontrino — l'unica
  verificabile — resta intatta.

## Il formato è fondibile

Due copie dello stesso archivio si fondono così: **concatena le righe, tieni la
più recente per `id`, e tieni le lapidi.** Nient'altro. Questa proprietà, gratis
dal formato, è quella che rende possibile:

- cambiare telefono (la cartella arriva, l'app la assorbe)
- reinstallare l'app senza perdere niente
- correggere a mano un file col telefono e risincronizzare

Quando la implementi:

- gli allegati (foto, scontrini, dossier) si copiano **solo se mancano**, mai
  sopra
- le viste generate (`diario.md`) non si fondono: si rigenerano
- un CSV che questa versione non conosce **non si tocca**: potrebbe venire da una
  versione più nuova, e fonderlo senza sapere quali colonne abbia lo rovinerebbe
- le impostazioni si adottano da fuori **solo se le nostre sono intonse**,
  ignorando i campi che non sono preferenze (la cartella scelta, le date di
  sincronizzazione, le tracce diagnostiche). Contarli renderebbe l'archivio
  «già toccato» sempre, e la fusione non scatterebbe mai — difetto silenzioso.

## L'area privata e lo specchio

L'archivio di lavoro vive nell'area privata dell'app: funziona sempre, non
chiede permessi, e lì valgono `append` + `fsync` e la rinomina atomica.

La cartella scelta dall'utente (SAF, `androidx.documentfile`) è uno **specchio**:
su un albero SAF non esiste `append`, quindi scrivere direttamente là
perderebbe tutte quelle proprietà. La copia privata resta l'autorità; lo specchio
si riscrive dopo ogni operazione.

Due cose da sapere:

- **il permesso sulla cartella non sta nel file**, vive nell'installazione: dopo
  una reinstallazione la cartella va riscelta, e le impostazioni devono dirlo
  invece di tacere.
- **assegnare una cartella è il primo gesto dopo un'installazione**, e deve
  fondere quello che ci trova. Se l'utente ha già un archivio là dentro, i suoi
  viaggi entrano subito: è il percorso di un cambio di telefono, e va offerto
  come invito in prima pagina, non nascosto nelle impostazioni.

## Il Markdown

`diario.md` (una sezione per giorno), `itinerario.md` (il documento importato),
`FORMATI.md` (la documentazione delle colonne, scritta dall'app stessa nella
cartella).

- Le sezioni si riconoscono da un'**intestazione con la data in forma ISO**
  davanti (`## 2026-08-13 — giovedì 13 agosto, Würzburg`): è ciò che permette di
  ritrovare e riscrivere la sezione di un giorno, e di ordinarle senza
  interpretare l'italiano.
- Rigenerare una sezione in mezzo al file richiede di dividere, sostituire e
  ricomporre: tutto quello che non è una sezione di giorno (il titolo, un testo
  scritto a mano) **resta dov'è**.
- Il documento importato **non si butta e non si sovrascrive**: se ne carica uno
  nuovo, i due convivono (`itinerario.md`, `itinerario-2.md`) e sui giorni di cui
  parlano entrambi vince l'ultimo. Il racconto dei giorni già passati sta scritto
  solo nel primo.

## Cosa scrivere nell'archivio oltre ai dati

- **`FORMATI.md`**: cosa significa ogni colonna, scritto dall'app. Chi apre la
  cartella fra cinque anni non ha il codice sotto mano.
- **le tracce diagnostiche** (l'esito dell'ultima operazione di rete, dell'ultimo
  import): non sono dati, ma sono l'unica risposta possibile a «cos'ha fatto?»
  quando l'utente ha solo il telefono. → `diagnosi-a-distanza.md`
- **mai le chiavi API.** Quella cartella può stare dentro una cartella
  sincronizzata su un cloud. Le chiavi vanno in `EncryptedSharedPreferences`, e
  nelle impostazioni si mostrano solo le ultime quattro cifre.
