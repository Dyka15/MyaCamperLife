package it.myacamperlife.app.dominio

import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Legge la data e l'ora che l'utente digita in una form.
 *
 * Serve a registrare una spesa o un rifornimento **di ieri**: uno scontrino si
 * ritrova in tasca due giorni dopo, e costringere a registrarlo con la data di
 * oggi vuol dire avere un diario sbagliato per sempre.
 *
 * La data si legge con [GiornoTappa], che e' tollerante e conosce le forme che
 * la gente scrive davvero. L'ora accetta `21:30`, `21.30` e `2130`: sono i tre
 * modi in cui si digita un orario su una tastiera numerica.
 *
 * Il fuso e l'offset arrivano dal riferimento, cioe' dall'adesso di chi chiama:
 * una spesa di ieri sera in Svizzera resta nel fuso in cui l'hai fatta.
 *
 * Funzione pura.
 */
object Momento {

    /**
     * @return l'istante, o `null` se la data non si e' capita. Un'ora
     *   illeggibile **non** fa fallire tutto: si tiene quella del riferimento,
     *   perche' sbagliare l'ora di qualche ora e' un guaio molto minore che
     *   rifiutare la registrazione.
     */
    fun leggi(data: String?, ora: String?, riferimento: OffsetDateTime): OffsetDateTime? {
        val giorno = GiornoTappa.leggi(data, riferimento.toLocalDate()) ?: return null
        val orario = orario(ora) ?: riferimento.toLocalTime()
        return OffsetDateTime.of(giorno, orario, riferimento.offset)
    }

    /** `21:30`, `21.30`, `2130`, `9:05`, `9`. */
    fun orario(testo: String?): LocalTime? {
        val pulito = testo?.trim()?.takeUnless { it.isEmpty() } ?: return null

        SEPARATO.matchEntire(pulito)?.let { trovato ->
            return tempo(trovato.groupValues[1].toInt(), trovato.groupValues[2].toInt())
        }
        ATTACCATO.matchEntire(pulito)?.let { trovato ->
            val cifre = trovato.groupValues[1]
            return tempo(cifre.dropLast(2).toInt(), cifre.takeLast(2).toInt())
        }
        SOLA_ORA.matchEntire(pulito)?.let { trovato ->
            return tempo(trovato.groupValues[1].toInt(), 0)
        }
        return null
    }

    /**
     * Vero se la data e' oltre oggi.
     *
     * Serve a validare le form: una spesa o un rifornimento **nel futuro** e'
     * sempre un errore di battitura, e rifiutarlo mostrando la data letta e'
     * meglio che accettare una riga che sporca il diario. Il confronto e' sul
     * giorno e non sull'istante, cosi' registrare stasera alle 23 quello che si
     * e' speso alle 22 resta possibile anche se l'orologio dice 20:00.
     */
    fun oltreOggi(istante: OffsetDateTime, riferimento: OffsetDateTime): Boolean =
        istante.toLocalDate() > riferimento.toLocalDate()

    /** Come si scrive una data in una casella: quella di oggi, precompilata. */
    fun scriviData(istante: OffsetDateTime): String = istante.format(DATA)

    fun scriviOra(istante: OffsetDateTime): String = istante.format(ORA)

    /** Un'ora fuori dal quadrante non e' un'ora, ed e' meglio dirlo con `null`. */
    private fun tempo(ore: Int, minuti: Int): LocalTime? =
        if (ore in 0..23 && minuti in 0..59) LocalTime.of(ore, minuti) else null

    private val SEPARATO = Regex("""(\d{1,2})[:.,h](\d{1,2})""")
    private val ATTACCATO = Regex("""(\d{3,4})""")
    private val SOLA_ORA = Regex("""(\d{1,2})""")

    private val DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ORA = DateTimeFormatter.ofPattern("HH:mm")
}
