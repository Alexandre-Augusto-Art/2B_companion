package br.offgr1d.companion.chat

import br.offgr1d.companion.alerts.AlertFeed
import br.offgr1d.companion.alerts.Alerts
import br.offgr1d.companion.knowledge.Daily
import br.offgr1d.companion.knowledge.Doc
import br.offgr1d.companion.knowledge.DocKind
import br.offgr1d.companion.knowledge.FeedParser
import br.offgr1d.companion.knowledge.KnowledgeIndex
import br.offgr1d.companion.knowledge.brDate
import br.offgr1d.companion.text.Text
import java.time.LocalDate

/** O que a 2B consulta direto no 0FF-GR1D. [feed] é baixado na hora, se a cópia não for recente. */
enum class Tool(val label: String, val ask: String, val feed: String?) {
    NEWS("NOTÍCIA DO DIA", "Qual a notícia do dia do W4K3 PR0T0C0L?", "reflections"),
    QUOTE("MENSAGEM DO DIA", "Qual a mensagem motivacional do dia?", "motivations"),
    ACTS("PRÓXIMOS ATOS", "Onde vão ser os próximos atos?", "organize"),
    GEAR("O QUE LEVAR", "Como me equipar para um protesto?", null),
    TOOLS("FERRAMENTAS", "Quais ferramentas o 0FF-GR1D tem?", null),
}

class ToolAnswer(val text: String, val sources: List<Source>)

/** Respostas montadas com o conteúdo do site, sem IA: data, local e link não podem ser inventados. */
object Toolbox {
    private const val MAX_ACTS = 6

    private val NEWS = listOf(
        Regex("\\b(w4k3|wake|waken|woken) ?(pr0t0c0l|protocol|protocolo)?\\b"),
        Regex("\\bnoticias?\\b.{0,12}\\b(dia|hoje)\\b"), Regex("\\b(qual|quais|tem|ver|mostra\\w*|me (da|de|conta|fala))\\b.{0,12}\\bnoticias?\\b.{0,20}$"),
    )
    private val NOT_NEWS = Regex("\\b(o que e|que e o|como funciona|quem (fez|criou|faz)|verdade|fake|falsa?|golpe|checar|essa noticia|esta noticia|avis\\w+|notifica\\w+)\\b")
    private val QUOTE = Regex("\\b(mensagem|frase|citacao)\\b.{0,20}\\b(motiva\\w+|inspira\\w+)\\b|\\b(mensagem|frase|citacao) (do dia|de hoje|diaria)\\b|\\bmotivac(ao|ional)\\b.{0,20}\\b(dia|diaria|hoje)\\b|\\bme (motiva|inspira)\\b")
    private val ACT_WORD = "(atos?|protestos?|manifestac(ao|oes)|passeatas?|marchas?|mobilizac(ao|oes))"
    private val ACTS = listOf(
        Regex("\\b(onde|quando|quais|qual|proxim\\w+|tem|havera|vai ter|vao ter|lista|mostra\\w*|agenda|calendario)\\b.{0,30}\\b$ACT_WORD\\b"),
        Regex("\\b$ACT_WORD\\b.{0,25}\\b(hoje|amanha|semana|perto|proxim\\w+|marcad\\w+|convocad\\w+|agendad\\w+|vao ocorrer|vao acontecer)\\b"),
    )
    // Estar num ato, direitos e riscos são conversa, não agenda.
    private val NOT_ACTS = Regex("\\b(direitos?|policia|preso|detid\\w+|levar|equipar|vestir|usar|proteger|cuidados?|celular|gas|foto|filmar|reconhecid\\w+|seguro|segura|seguranca|preparar|medo|organizar um|fazer um|criar um)\\b")
    private val GEAR = listOf(
        Regex("\\b(equipar|equipo|equipamentos?|checklist|mochila|kit)\\b.{0,40}\\b$ACT_WORD\\b"),
        Regex("\\b(o que|oque|q|que|como)\\b.{0,15}\\b(levar|vestir|levo|visto|usar)\\b.{0,30}\\b$ACT_WORD\\b"),
        Regex("\\b$ACT_WORD\\b.{0,30}\\b(o que|oque|como)\\b.{0,15}\\b(levar|levo|vestir|equipar)\\b"),
    )
    private val NOT_GEAR = Regex("\\b(identidade|reconhecid\\w+|cameras?|facial|gas|celular|tarot)\\b")
    private val TOOLS = Regex("\\b(ferramentas?|paginas?|recursos?)\\b.{0,30}\\b(0ff|off|grid|gr1d|site|dedsec|voce tem)\\b|\\bo que (tem|ha) no (site|0ff|off)|\\bferramentas do (site|0ff|off|projeto)")

    fun match(question: String): Tool? {
        Tool.entries.firstOrNull { it.ask == question }?.let { return it }
        val q = Text.normalize(question)
        if (q.length > 160) return null // pergunta longa costuma ter contexto próprio
        return when {
            GEAR.any { it.containsMatchIn(q) } && !NOT_GEAR.containsMatchIn(q) -> Tool.GEAR
            ACTS.any { it.containsMatchIn(q) } && !NOT_ACTS.containsMatchIn(q) -> Tool.ACTS
            QUOTE.containsMatchIn(q) -> Tool.QUOTE
            NEWS.any { it.containsMatchIn(q) } && !NOT_NEWS.containsMatchIn(q) -> Tool.NEWS
            TOOLS.containsMatchIn(q) -> Tool.TOOLS
            else -> null
        }
    }

    /** "e em São Paulo?" logo depois da agenda continua sendo a agenda. */
    fun followUp(last: Tool?, question: String): Tool? =
        Tool.ACTS.takeIf { last == it && KnowledgeIndex.detectUf(question) != null && Text.normalize(question).split(' ').size <= 6 }

    /** @param stale não deu para baixar agora: vale a cópia guardada, com aviso. */
    fun answer(tool: Tool, question: String, daily: Daily, docs: List<Doc>, ufs: List<String>, today: LocalDate, stale: Boolean): ToolAnswer {
        val a = when (tool) {
            Tool.NEWS -> news(daily, today)
            Tool.QUOTE -> quote(daily, today)
            Tool.ACTS -> acts(question, daily, ufs, today)
            Tool.GEAR -> gear()
            Tool.TOOLS -> tools(docs)
        }
        return if (!stale || tool.feed == null) a
        else ToolAnswer(a.text + "\n\n(Não consegui atualizar agora. Isso é o último que tenho guardado.)", a.sources)
    }

    private fun page(n: Int, title: String, path: String) = Source(n, title, "0FF-GR1D", FeedParser.SITE + path)

    private fun news(daily: Daily, today: LocalDate): ToolAnswer {
        val r = daily.reflections.firstOrNull()
            ?: return ToolAnswer("Ainda não tenho nada do W4K3 PR0T0C0L aqui. Conecta na internet e me pergunta de novo.", listOf(page(1, "W4K3 PR0T0C0L", "w4k3-protocol.html")))
        val sources = ArrayList<Source>()
        val day = if (r.date == today.toString()) "Transmissão de hoje" else "A de hoje ainda não saiu. A última é de ${brDate(r.date)}"
        val text = StringBuilder("${Alerts.voice(AlertFeed.W4K3, r.id)}\n$day:\n\n${r.title}\n${Text.cut(r.excerpt, 400)}")
        r.newsTitle?.let {
            text.append("\n\nA notícia por trás: $it (${r.newsSource ?: "fonte no site"}).")
            if (r.newsLink != null) sources += Source(sources.size + 1, it, r.newsSource ?: "fonte", r.newsLink)
        }
        val others = daily.reflections.drop(1).take(3)
        if (others.isNotEmpty()) text.append("\n\nAntes disso:\n" + others.joinToString("\n") { "• ${brDate(it.date)}: ${it.title}" })
        text.append("\n\nA reflexão é opinião e usa ficção como metáfora. Quer conversar sobre ela?")
        sources += page(sources.size + 1, "Ler a reflexão completa", "w4k3-protocol.html")
        return ToolAnswer(text.toString(), sources)
    }

    private fun quote(daily: Daily, today: LocalDate): ToolAnswer {
        val src = listOf(page(1, "Motivação Diária", "motivacao.html"))
        val q = daily.quotes.firstOrNull() ?: return ToolAnswer("Ainda não baixei a mensagem do dia. Conecta na internet e me pergunta de novo.", src)
        val day = if (q.date == today.toString()) "" else " (de ${brDate(q.date)}, a de hoje ainda não saiu)"
        return ToolAnswer("${Alerts.voice(AlertFeed.QUOTE, q.id)}$day\n\n\"${q.content}\"${q.author?.let { "\n$it" }.orEmpty()}", src)
    }

    private fun acts(question: String, daily: Daily, saved: List<String>, today: LocalDate): ToolAnswer {
        val asked = KnowledgeIndex.detectUf(question)
        val ufs = asked?.let(::listOf) ?: saved
        val where = when {
            asked != null -> " em ${KnowledgeIndex.UF_NAMES[asked]}"
            ufs.isNotEmpty() -> " em ${ufs.sorted().joinToString(", ")}"
            else -> ""
        }
        val site = { n: Int -> page(n, "Todos os atos", "organize-se.html") }
        val list = daily.upcoming(ufs, today)
        if (list.isEmpty()) {
            val why = if (daily.acts.isEmpty()) "Ainda não baixei a lista de atos. Conecta na internet e me pergunta de novo."
            else "Não achei ato convocado$where na lista de hoje. Ela vem das notícias, então pode faltar algo."
            return ToolAnswer(why, listOf(site(1)))
        }
        val sources = ArrayList<Source>()
        val shown = list.take(MAX_ACTS)
        val lines = shown.joinToString("\n\n") { a ->
            val n = a.url?.let { sources += Source(sources.size + 1, Text.cut(a.title, 70), a.source ?: "fonte", it); " [${sources.size}]" }.orEmpty()
            "• ${a.place()}, ${a.whenText(today)}\n${a.title}$n"
        }
        val rest = if (list.size > shown.size) "\n\nTem mais ${list.size - shown.size} no site." else ""
        val tip = if (where.isEmpty()) " Se me disser o estado, eu filtro." else ""
        sources += site(sources.size + 1)
        return ToolAnswer("Atos convocados$where:\n\n$lines$rest\n\nConfirme data e local na fonte: pode mudar de última hora.$tip", sources)
    }

    private fun gear() = ToolAnswer(
        """
        Bora se preparar. Checklist do 0FF-GR1D:

        LEVE
        • Documento com foto
        • Água em garrafa de plástico e lanche leve
        • Dinheiro trocado e cartão de transporte
        • Remédios de uso contínuo, com receita
        • Soro fisiológico ou água limpa para os olhos
        • Protetor solar e capa de chuva
        • Telefones anotados no papel ou no braço: 192, um contato e a Defensoria

        VISTA
        • Roupa confortável, lisa, e tênis fechado
        • Boné, máscara PFF2 e óculos no lugar da lente de contato
        • Em alguns lugares cobrir o rosto em atos é restrito: confira antes

        NÃO LEVE
        • Nada que pareça arma, vidro, rojão, haste de madeira ou metal
        • Bebida, drogas, objetos de valor, documentos de que não precisa

        CELULAR
        • O mais seguro é deixar em casa ou levar um reserva sem suas contas
        • Se levar: bateria cheia, senha forte, sem rosto nem digital, backup feito

        ANTES DE SAIR
        • Confirme data e local, vá em grupo e combine um ponto de encontro
        • Avise alguém de confiança a que horas você volta
        """.trimIndent(),
        listOf(
            page(1, "Checklist completo e cuidados", "organize-se.html"), page(2, "Revolution Assets (equipamentos)", "revolution-assets.html"),
            page(3, "F4C3 SC4N (teste seu disfarce)", "f4c3-sc4n.html"), page(4, "Bitchat (chat sem internet)", "bitchat.html"),
        ),
    )

    /** Uma entrada por página do site. */
    fun pages(docs: List<Doc>): List<Doc> =
        docs.filter { it.kind == DocKind.FERRAMENTA && it.url != null && !it.url.endsWith("index.html") }.distinctBy { it.url }

    private fun tools(docs: List<Doc>): ToolAnswer {
        val list = pages(docs)
        return ToolAnswer(
            "Isso é o que a gente tem no 0FF-GR1D:\n\n" +
                list.mapIndexed { i, d -> "[${i + 1}] ${d.title}: ${d.text.substringBefore(". ").trimEnd('.')}." }.joinToString("\n\n") +
                "\n\nToque num link para abrir no navegador, ou me pergunte sobre qualquer uma.",
            list.mapIndexed { i, d -> Source(i + 1, d.title, "0FF-GR1D", d.url) },
        )
    }
}
