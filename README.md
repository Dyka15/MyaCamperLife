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
- **Quello che serve dalla rete si prende in anticipo.** L'itinerario si carica a casa, e
  in quel momento arrivano meteo, distanze fra le tappe e punti di interesse. In viaggio
  si consulta una scorta, non un servizio.
- **I file sono il prodotto.** L'app scrive CSV e Markdown in una cartella del telefono:
  si aprono in un foglio di calcolo, si leggono fra dieci anni, si danno in pasto a un
  modello linguistico. Non c'è un dentro da cui esportare.

## Stato

**Progettazione.** Nessuna riga di codice ancora scritta. Ci sono due documenti:

| | |
|---|---|
| [PROGETTO.md](PROGETTO.md) | Cos'è e cosa fa: input, output, funzionalità, schermate, confini |
| [ANALISI.md](ANALISI.md) | Si può fare: cosa regge offline e cosa no, workflow per workflow, con le scelte tecniche e la tabella di marcia |

## Tecnologie previste

Kotlin, Jetpack Compose, file CSV e Markdown su archiviazione locale (nessun database).
`minSdk` 33. Dispositivo di riferimento: Poco F7 (HyperOS, Android 16).
