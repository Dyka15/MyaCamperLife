# MyaCamperLife

App Android per chi viaggia in camper. Tiene l'itinerario, registra la giornata di
viaggio, calcola consumi e spese, e la sera dice cosa aspettarsi domani.

**Funziona senza rete.** È il motivo per cui esiste.

## Cosa sostituisce

Un bot Telegram appoggiato a workflow n8n, Google Sheets e Google Drive. Quel sistema
funziona bene, ma non fa nulla offline — e un camper passa buona parte del suo tempo
dove la connessione non c'è.

## I tre principi

- **Registrare non richiede rete, mai.** Foto, nota, rifornimento, spesa, check-in sono
  righe accodate a un file locale. Nessuna di queste azioni può fallire perché il
  telefono è offline.
- **Quello che serve dalla rete si prende in anticipo.** Le distanze fra le tappe
  arrivano quando si carica l'itinerario, il meteo ogni sera alle 19:00. In viaggio si
  consulta una scorta, non un servizio.
- **I file sono il prodotto.** L'app scrive CSV e Markdown in una cartella del telefono:
  si aprono in un foglio di calcolo, si leggono fra dieci anni. Non c'è un dentro da cui
  esportare.

## Stato

**Fasi 1–4 di 9 realizzate.** L'app registra una giornata di viaggio, calcola i consumi
del mezzo e tiene il conto delle spese, tutto senza rete.

Itinerario:

- importa un file `.md` con dentro il blocco `waypoints`, quello che usi oggi
- check-in su una tappa, con l'ora e la posizione
- salta una tappa e ripristinala: lo stesso comando fa le due cose
- aggiungi una tappa scegliendo dove inserirla, con le coordinate dal GPS o digitate
- tiene più viaggi e li elenca dal più recente

Diario:

- registra posizione, note, foto e rifornimenti con quattro tocchi dalla schermata
  d'apertura
- le foto si chiamano `foto_AAAAMMGG_HHMMSS_Localita.jpg`, come già oggi
- scrive `diario.md`: un file per viaggio, una sezione per giorno, aggiornata a ogni evento

Numeri:

- consumo **pieno-a-pieno**: km/l, litri e euro per 100 km, tratto per tratto e in media
  pesata sui chilometri
- **autonomia residua stimata** dai km con un pieno, meno i chilometri dedotti da tutti i
  punti registrati — check-in, posizioni, foto, rifornimenti
- **conto del viaggio**: totali per categoria, per modalità di pagamento e per giorno,
  spesa media giornaliera
- ogni numero dichiara com'è nato: una stima si chiama stima

Spese:

- categoria, importo e **modalità di pagamento** — contanti, POS, carta — in una form
  che si compila con tre tocchi e una cifra
- **valuta estera**: si salva quello che c'era sullo scontrino più il cambio del momento,
  modificabile; il totale in euro si ricalcola sempre da quei due
- **foto dello scontrino, con l'importo letto sul telefono**: il riconoscimento del testo
  è dentro l'APK, nessuna immagine esce dal dispositivo e funziona senza rete. Quello che
  legge è una proposta, e lo dice
- il carburante resta nei rifornimenti e non fra le spese: il conto somma le due tabelle
  tenendole distinte, così un pieno non viene contato due volte

Sotto: `tappe.csv`, `spostamenti.csv`, `note.csv`, `foto.csv`, `rifornimenti.csv`,
`spese.csv`, `impostazioni.json` e `FORMATI.md` che spiega le colonne. Nessuna rete usata;
l'unico permesso è la posizione, chiesto quando serve.

Restano le fasi 5–9: briefing serale, meteo, punti di interesse offline, client AI.

## Documenti

| | |
|---|---|
| [PROGETTO.md](PROGETTO.md) | Cos'è e cosa fa: input, output, funzionalità, schermate, confini |
| [ANALISI.md](ANALISI.md) | Si può fare: cosa regge offline e cosa no, workflow per workflow, con le scelte tecniche e la tabella di marcia |

## Provarla

L'APK viene compilato da GitHub Actions a ogni push e pubblicato come artifact
scaricabile nella tab **Actions** del repository, anche dal browser del telefono.

In `esempi/` c'è un itinerario con cui provare l'importazione.

## Tecnologie

Kotlin, Jetpack Compose (Material 3), file CSV e Markdown su archiviazione locale.
**Nessun database:** i volumi sono migliaia di righe e un archivio opaco contraddirebbe
il terzo principio. `minSdk` 33 (Android 13). Dispositivo di riferimento: Poco F7
(HyperOS, Android 16).
