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

**Fase 1 di 9 realizzata.** L'app apre un itinerario che hai già e ne mostra le tappe.

- importa un file `.md` con dentro il blocco `waypoints`, quello che usi oggi
- scrive `tappe.csv` nella cartella del viaggio, e `FORMATI.md` che spiega le colonne
- tiene più viaggi e li elenca dal più recente
- si riceve un itinerario anche condividendolo da un'altra app

Non fa ancora niente altro: check-in, diario, consumi, spese e briefing serale sono le
fasi successive. Nessun permesso richiesto, nessuna rete usata.

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
