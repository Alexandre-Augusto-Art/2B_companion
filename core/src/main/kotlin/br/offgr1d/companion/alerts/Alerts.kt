package br.offgr1d.companion.alerts

import br.offgr1d.companion.knowledge.Act
import br.offgr1d.companion.knowledge.Daily
import br.offgr1d.companion.knowledge.FeedParser
import br.offgr1d.companion.knowledge.Quote
import br.offgr1d.companion.knowledge.Reflection
import br.offgr1d.companion.knowledge.brDate
import br.offgr1d.companion.store.Settings
import br.offgr1d.companion.text.Text
import java.time.LocalDate
import kotlinx.serialization.Serializable

enum class AlertFeed(val label: String, val feed: String) {
    W4K3("W4K3 PR0T0C0L", "reflections"),
    QUOTE("Motivação Diária", "motivations"),
    ACTS("Organize-se", "organize"),
}

/** Um aviso pronto: vira notificação do sistema e mensagem da 2B na conversa. */
@Serializable
data class Alert(
    val feed: AlertFeed,
    val id: String,
    val title: String,
    val text: String,
    /** Fala de abertura da 2B. */
    val voice: String,
    val url: String,
    val read: Boolean = false,
)

@Serializable
data class AlertState(val seen: Map<AlertFeed, List<String>> = emptyMap(), val inbox: List<Alert> = emptyList())

/** As mesmas regras do bot da 2B no Telegram (automation/bot-2b no site), sem servidor: o aparelho compara os feeds. */
object Alerts {
    private const val MAX_SEEN = 1000
    private const val MAX_INBOX = 20
    private const val MAX_POSTS = 3
    private const val MAX_ACTS = 8

    private val VOICE = mapOf(
        AlertFeed.W4K3 to listOf("Nova transmissão do W4K3.", "Sinal captado. Leitura do dia liberada.", "Tem coisa que eles não querem que você leia."),
        AlertFeed.QUOTE to listOf("Combustível do dia.", "Para não esquecer por que a gente luta.", "Uma frase antes de sair."),
        AlertFeed.ACTS to listOf("Tem gente indo para a rua.", "Convocação recebida. Confira antes de ir.", "Atos marcados no radar."),
    )

    /** Sempre a mesma fala para o mesmo conteúdo. */
    fun voice(feed: AlertFeed, id: String): String = VOICE.getValue(feed).let { it[Math.floorMod(id.hashCode(), it.size)] }

    fun enabled(s: Settings): List<AlertFeed> = AlertFeed.entries.filter {
        when (it) {
            AlertFeed.W4K3 -> s.alertW4k3
            AlertFeed.QUOTE -> s.alertQuote
            AlertFeed.ACTS -> s.alertActs
        }
    }

    /**
     * Compara o que chegou com o que já foi tratado. Só entra feed ligado e baixado agora ([fetched]).
     * Na primeira vez de um feed, só marca o que já existe, sem avisar.
     * Feed desligado perde a marcação: ao religar, não despeja tudo o que saiu no meio.
     */
    fun collect(state: AlertState, daily: Daily, s: Settings, fetched: Set<String>, today: LocalDate): Pair<AlertState, List<Alert>> {
        val on = enabled(s)
        val seen = state.seen.filterKeys { it in on }.toMutableMap()
        val fresh = ArrayList<Alert>()
        for (feed in on) {
            if (feed.feed !in fetched) continue
            val ids = when (feed) {
                AlertFeed.W4K3 -> daily.reflections.map { it.id }
                AlertFeed.QUOTE -> daily.quotes.map { it.id }
                AlertFeed.ACTS -> daily.acts.map { it.id }
            }
            val before = seen[feed]
            if (before == null) {
                if (ids.isNotEmpty()) seen[feed] = ids.take(MAX_SEEN)
                continue
            }
            val new = ids.toSet() - before.toSet()
            if (new.isEmpty()) continue
            seen[feed] = (new + before).take(MAX_SEEN) // o que sai do feed e volta não avisa de novo
            fresh += when (feed) {
                AlertFeed.W4K3 -> daily.reflections.filter { it.id in new }.take(MAX_POSTS).asReversed().map(::reflection)
                AlertFeed.QUOTE -> daily.quotes.filter { it.id in new }.take(MAX_POSTS).asReversed().map(::quote)
                AlertFeed.ACTS -> listOfNotNull(acts(daily.upcoming(s.alertUfs, today).filter { it.id in new }, today))
            }
        }
        return AlertState(seen, (state.inbox + fresh).takeLast(MAX_INBOX)) to fresh
    }

    fun reflection(r: Reflection) = Alert(
        AlertFeed.W4K3, r.id, "W4K3 PR0T0C0L${r.date?.let { " · ${brDate(it)}" }.orEmpty()}: ${r.title}",
        Text.cut(r.excerpt, 400) + r.newsTitle?.let { "\nNotícia: $it (${r.newsSource ?: "fonte no site"})" }.orEmpty(),
        voice(AlertFeed.W4K3, r.id), FeedParser.SITE + "w4k3-protocol.html",
    )

    fun quote(q: Quote) = Alert(
        AlertFeed.QUOTE, q.id, "Mensagem do dia · ${brDate(q.date)}", "\"${q.content}\"" + q.author?.let { "\n$it" }.orEmpty(),
        voice(AlertFeed.QUOTE, q.id), FeedParser.SITE + "motivacao.html",
    )

    fun acts(list: List<Act>, today: LocalDate): Alert? {
        if (list.isEmpty()) return null
        val lines = list.take(MAX_ACTS).joinToString("\n") { "• ${it.place()}, ${it.whenText(today)}: ${Text.cut(it.title, 120)}" }
        val rest = if (list.size > MAX_ACTS) "\n+ ${list.size - MAX_ACTS} no site." else ""
        return Alert(
            AlertFeed.ACTS, list.first().id, if (list.size == 1) "Organize-se: ato convocado" else "Organize-se: ${list.size} atos convocados",
            "$lines$rest\nConfirme local e horário na fonte antes de ir.", voice(AlertFeed.ACTS, list.first().id), FeedParser.SITE + "organize-se.html",
        )
    }
}
