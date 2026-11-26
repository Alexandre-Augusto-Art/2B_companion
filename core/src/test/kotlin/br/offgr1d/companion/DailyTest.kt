package br.offgr1d.companion

import br.offgr1d.companion.alerts.Alert
import br.offgr1d.companion.alerts.AlertFeed
import br.offgr1d.companion.alerts.AlertState
import br.offgr1d.companion.alerts.Alerts
import br.offgr1d.companion.chat.Companion2B
import br.offgr1d.companion.chat.SmallTalk
import br.offgr1d.companion.chat.Tool
import br.offgr1d.companion.chat.Toolbox
import br.offgr1d.companion.knowledge.Daily
import br.offgr1d.companion.knowledge.FeedParser
import br.offgr1d.companion.knowledge.todayBR
import br.offgr1d.companion.llm.LlmEngine
import br.offgr1d.companion.llm.LlmRequest
import br.offgr1d.companion.screen.ScreenContext
import br.offgr1d.companion.store.BlobCipher
import br.offgr1d.companion.store.BlobStore
import br.offgr1d.companion.store.Settings
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFails
import br.offgr1d.companion.screen.Attachment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object Plain : BlobCipher {
    override fun encrypt(plain: ByteArray) = plain
    override fun decrypt(sealed: ByteArray) = sealed
    override fun destroyKey() {}
}

private class Recorder(val answer: String = "ok") : LlmEngine {
    val requests = ArrayList<LlmRequest>()
    override fun load(model: File, vision: Boolean, useGpu: Boolean, cacheDir: File) {}
    override fun generate(request: LlmRequest): Flow<String> = flow { requests += request; emit(answer) }
    override fun close() {}
}

class DailyTest {
    private val today = LocalDate.parse("2026-11-24")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val models = tempDir()
    private val data = tempDir()

    @AfterTest fun end() = scope.cancel()

    private fun act(id: String, uf: String, date: String? = null, call: Boolean = true, extra: String = "", title: String = "Ato $id") =
        """{"id":"$id","titulo":"$title","tipo":"${if (call) "convocacao" else "cobertura"}","uf":"$uf","publicadoEm":"2026-11-20T10:00:00Z",
           "fonte":{"nome":"Jornal","url":"https://exemplo.org/$id"}${date?.let { ",\"data\":\"$it\"" }.orEmpty()}$extra}"""

    private fun organize(vararg acts: String) = """{"eventos":[${acts.joinToString()}]}"""
    private fun reflections(vararg days: String) = days.joinToString(",", "[", "]") {
        """{"id":"$it","date":"$it","news":{"title":"Notícia $it","link":"https://n.org/$it","source":"Fonte"},"reflection":{"title":"Título $it","content":"<p>Corpo</p>","excerpt":"Resumo $it"}}"""
    }
    private fun quotes(vararg days: String) = days.joinToString(",", "[", "]") { """{"date":"$it","content":"Frase $it","author":"Autora","bankId":"x"}""" }

    private fun daily(o: String = organize(), r: String = "[]", q: String = "[]") =
        Daily(FeedParser.reflections(r), FeedParser.quotes(q), FeedParser.acts(o))

    private val site = HashMap<String, String>()
    private fun app(engine: LlmEngine = Recorder(), fetch: (String) -> String = { url -> site[url.substringAfterLast('/').removeSuffix(".json")] ?: throw IOException("sem rede") }) =
        Companion2B(scope, BlobStore(data, Plain), models, tempDir(), { engine }, fetch = fetch, modelReady = fakeReady(models)).also { runBlocking { it.awaitReady() } }

    private fun fakeModel() = fakeModelIn(models, "gemma-4-e2b")
    private fun Companion2B.ask(q: String) = runBlocking { withTimeout(10_000) { send(q)!!.join() } }
    private val Companion2B.last get() = state.value.messages.last()

    // ---------- atalhos ----------

    @Test fun `pergunta natural chega ao atalho certo`() {
        mapOf(
            Tool.NEWS to listOf("quais as notícias do dia do waken protocol?", "notícia do dia", "o que saiu no W4K3 hoje?", "me conta as notícias de hoje", "Quais as notícias?"),
            Tool.QUOTE to listOf("mensagem motivacional do dia", "qual a frase do dia?", "manda uma mensagem de motivação do dia", "me motiva"),
            Tool.ACTS to listOf("onde vão ocorrer os próximos atos?", "tem protesto hoje?", "quais manifestações em São Paulo", "quando é o próximo ato em PE?", "atos marcados pra essa semana"),
            Tool.GEAR to listOf("como me equipar para um protesto?", "o que levar para uma manifestação", "que roupa usar no protesto? o que vestir em um ato", "checklist do ato"),
            Tool.TOOLS to listOf("quais ferramentas o 0ff grid tem?", "o que tem no site?"),
        ).forEach { (tool, list) -> list.forEach { assertEquals(tool, Toolbox.match(it), it) } }
        Tool.entries.forEach { assertEquals(it, Toolbox.match(it.ask)) }
    }

    @Test fun `conversa e dúvida não viram atalho`() = listOf(
        "oi, tudo bem?", "quais meus direitos numa manifestação?", "o que é o W4K3 PR0T0C0L?", "essa notícia é verdade?", "fui detido num protesto, e agora?",
        "como proteger meu celular no ato?", "o que você acha de protestos?", "recebi uma mensagem estranha hoje", "como não ser reconhecido no protesto",
        "vi um filme sobre manifestações", "como funcionam os avisos do w4k3?", "Transcreva a mensagem desta tela",
    ).forEach { assertNull(Toolbox.match(it), it) }

    @Test fun `papo do dia a dia é reconhecido e assunto sério não`() {
        listOf("oi 2B", "bom dia! dormi mal", "tô triste hoje", "me indica um filme", "conta uma piada", "valeu!", "acordei com preguiça").forEach { assertTrue(SmallTalk.isCasual(it), it) }
        listOf("caí num golpe do pix", "to com medo de ser preso no ato", "oi, recebi esse link https://x.com", "estou recebendo ameaças", "hackearam minha conta", "como usar o signal")
            .forEach { assertFalse(SmallTalk.isCasual(it), it) }
    }

    @Test fun `notícia e mensagem dizem quando não são de hoje`() {
        val d = daily(r = reflections("2026-11-20", "2026-11-23"), q = quotes("2026-11-24"))
        val n = Toolbox.answer(Tool.NEWS, "", d, emptyList(), emptyList(), today, stale = false)
        assertTrue("A de hoje ainda não saiu. A última é de 23/11" in n.text && "Título 2026-11-23" in n.text, n.text)
        assertEquals("https://n.org/2026-11-23", n.sources.first().url)
        val q = Toolbox.answer(Tool.QUOTE, "", d, emptyList(), emptyList(), today, stale = true)
        assertTrue("Frase 2026-11-24" in q.text && "ainda não saiu" !in q.text && "último que tenho" in q.text, q.text)
    }

    @Test fun `atos - só convocados e futuros, por data, com estado pedido ou salvo`() {
        val d = daily(organize(act("velho", "BA", "2026-11-10"), act("noticia", "BA", call = false), act("semdata", "BA"), act("tarde", "BA", "2026-11-28"),
            act("hoje", "SP", "2026-11-24", extra = ",\"hora\":\"18h\""), act("nacional", "BR", "2026-11-26"), act("bahiaepe", "BR", "2026-11-27", extra = ",\"estados\":[\"PE\"]")))
        fun ids(ufs: List<String>) = d.upcoming(ufs, today).map { it.id }
        assertEquals(listOf("hoje", "nacional", "bahiaepe", "tarde", "semdata"), ids(emptyList()))
        assertEquals(listOf("nacional", "tarde", "semdata"), ids(listOf("BA")))
        val a = Toolbox.answer(Tool.ACTS, "tem ato em São Paulo?", d, emptyList(), listOf("BA"), today, false)
        assertTrue("HOJE, 24/11 às 18h" in a.text && "Ato tarde" !in a.text && "em São Paulo" in a.text, a.text)
        assertTrue("Ato tarde" in Toolbox.answer(Tool.ACTS, "próximos atos", d, emptyList(), listOf("BA"), today, false).text)
        assertTrue("Não achei ato" in Toolbox.answer(Tool.ACTS, "ato no Acre", daily(organize(act("x", "SP", "2026-11-28"))), emptyList(), emptyList(), today, false).text)
    }

    @Test fun `atalho baixa na hora, reaproveita e funciona sem rede nem modelo`() {
        val now = todayBR().toString()
        site["motivations"] = quotes(now)
        var calls = 0
        val a = app { url -> calls++; site.getValue(url.substringAfterLast('/').removeSuffix(".json")) }
        a.ask("mensagem motivacional do dia")
        assertTrue("Frase $now" in a.last.text && !a.last.generated, a.last.text)
        a.ask("mensagem motivacional do dia")
        assertEquals(1, calls)
        val offline = Companion2B(scope, BlobStore(data, Plain), models, tempDir(), { Recorder() }, fetch = { throw IOException("sem rede") })
        runBlocking { offline.awaitReady() }
        offline.ask("mensagem motivacional do dia")
        assertTrue("Frase $now" in offline.last.text && "último que tenho" in offline.last.text, offline.last.text)
        offline.ask("como me equipar para um protesto?")
        assertTrue("Documento com foto" in offline.last.text)
    }

    @Test fun `estado logo depois da agenda continua na agenda`() {
        fakeModel()
        val e = Recorder()
        site["organize"] = organize(act("x", "PE"), act("y", "SP"))
        val a = app(e)
        a.ask("onde vão ser os próximos atos?")
        a.ask("e em Recife?")
        assertTrue("Ato x" in a.last.text && "Ato y" !in a.last.text, a.last.text)
        a.ask("e em SP?")
        assertTrue("Ato y" in a.last.text && e.requests.isEmpty())
        assertNull(Toolbox.followUp(Tool.NEWS, "e em SP?"))
    }

    @Test fun `depois da notícia a IA tem o texto dela para conversar`() {
        fakeModel()
        val e = Recorder()
        site["reflections"] = reflections("2026-11-24")
        val a = app(e)
        a.ask("qual a notícia do dia?")
        a.ask("me explica de um jeito simples")
        assertTrue("Título 2026-11-24\nCorpo" in e.requests.single().user, e.requests.single().user)
        assertTrue(a.last.sources.isEmpty(), "pedido vago não traz fonte sem relação: ${a.last.sources}")
    }

    @Test fun `guia conferido acompanha a resposta da IA`() {
        fakeModel()
        val a = app()
        a.ask("caí num golpe do pix, o que faço?")
        assertEquals("guia:cai-no-golpe", a.last.guide!!.id)
        a.ask("e se jogarem gás no ato?")
        assertEquals("guia:gas-ato", a.last.guide!!.id)
        assertTrue("Saia contra o vento" in a.last.text && !a.last.generated)
        a.ask("e o gás lacrimogêneo, lavo os olhos com o quê?")
        assertTrue(a.last.generated, "o passo a passo não se repete: agora a IA conversa, com o guia junto")
        a.ask("fui detido no ato, e agora?")
        assertTrue("Não assine nada" in a.last.text && !a.last.generated, a.last.text)
        a.ask("quais são meus direitos numa manifestação?")
        assertTrue("art. 5" in a.last.text && !a.last.generated, a.last.text)
        a.ask("nunca fui num protesto, estou com medo")
        assertEquals("guia:preparar-ato", a.last.guide!!.id)
        a.ask("oi, tudo bem?")
        assertNull(a.last.guide)
    }

    @Test fun `AIGF pede idade uma vez, muda o tom e desliga`() {
        fakeModel()
        val e = Recorder()
        val a = app(e)
        a.ask("#AIGF")
        assertTrue(a.state.value.askAdult && !a.state.value.settings.aigf && e.requests.isEmpty())
        a.confirmAdult(false)
        assertFalse(a.state.value.settings.aigf)
        a.ask("#aigf")
        a.confirmAdult(true)
        assertTrue(a.state.value.settings.aigf && a.state.value.settings.adult)
        a.ask("senti sua falta hoje")
        assertTrue("MODO AIGF" in e.requests.last().system && e.requests.last().temperature > 0.8)
        assertTrue(e.requests.last().history.none { "#" in it.text }, "comando não vai para a IA")
        a.ask("caí num golpe do pix")
        assertTrue("Faz isso, com calma" in a.last.text, "urgência continua com o guia conferido")
        a.ask("#AIGF")
        assertFalse(a.state.value.settings.aigf)
        a.ask(" #AiGf ")
        assertTrue(a.state.value.settings.aigf && !a.state.value.askAdult, "a idade só é pedida uma vez")
        a.ask("oi")
        a.ask("#AIGF")
        a.ask("oi de novo")
        assertFalse("MODO AIGF" in e.requests.last().system)
        runBlocking { withTimeout(10_000) { a.panic(includeModels = false).join() } }
        assertFalse(a.state.value.settings.aigf || a.state.value.settings.adult)
    }

    @Test fun `lista de ferramentas sai do conteúdo do site`() {
        val a = app()
        a.ask(Tool.TOOLS.ask)
        listOf("W4K3 PR0T0C0L", "Organize-se", "F4C3 SC4N", "Bitchat", "ALT3RN4T1V3 T00LS").forEach { assertTrue(it in a.last.text, it) }
        assertTrue(a.last.sources.all { it.url!!.startsWith("https://") })
    }

    // ---------- opções fixas ----------

    @Test fun `web fica ligada entre perguntas e depois de reabrir`() {
        val a = app()
        a.setWeb(true)
        runBlocking { delay(200) }
        a.ask("oi, tudo bem?")
        assertTrue(a.state.value.settings.web)
        assertTrue(a.state.value.net.isEmpty(), "papo não usa a rede")
        assertTrue(app().state.value.settings.web)
    }

    @Test fun `tela automática vale para a primeira pergunta e quando a pessoa fala dela`() {
        fakeModel()
        val e = Recorder()
        val a = app(e)
        a.saveSettings(Settings(autoScreen = true))
        a.attachScreen(ScreenContext("conteúdo da tela", null))
        a.ask("valeu!")
        assertFalse("conteúdo da tela" in e.requests[0].user)
        a.ask("qual o melhor jeito de estudar programação?")
        assertTrue("conteúdo da tela" in e.requests[1].user)
        a.ask("e de estudar inglês?")
        assertFalse("conteúdo da tela" in e.requests[2].user)
        a.ask("o que está escrito na tela mesmo?")
        assertTrue("conteúdo da tela" in e.requests[3].user)
        a.saveSettings(Settings(autoScreen = false))
        assertNull(a.state.value.screen)
    }

    @Test fun `limpeza automática espera sair e cancela se voltar`() = runBlocking {
        val a = app()
        a.saveSettings(Settings(autoClear = true))
        a.ask("como me equipar para um protesto?")
        a.onHidden(100)
        a.onVisible()
        delay(300)
        assertEquals(3, a.state.value.messages.size)
        a.onHidden(100)
        delay(400)
        assertEquals(1, a.state.value.messages.size)
    }

    @Test fun `papo não leva fontes nem limite de frases, e é mais solto`() {
        fakeModel()
        val e = Recorder()
        val a = app(e)
        a.ask("bom dia! tive um dia cansativo")
        assertEquals("bom dia! tive um dia cansativo", e.requests[0].user)
        a.ask("como criar senhas fortes?")
        assertTrue("<<<DADOS" in e.requests[1].user && e.requests[0].temperature > e.requests[1].temperature)
    }

    // ---------- avisos ----------

    private val all = Settings(alertW4k3 = true, alertQuote = true, alertActs = true, alertUfs = listOf("BA", "SP"))
    private val feeds = AlertFeed.entries.map { it.feed }.toSet()

    @Test fun `primeira leitura só marca, depois avisa uma vez`() {
        val d1 = daily(organize(act("a", "BA", "2026-11-28")), reflections("2026-11-23"), quotes("2026-11-23"))
        val (s1, f1) = Alerts.collect(AlertState(), d1, all, feeds, today)
        assertTrue(f1.isEmpty())
        val d2 = daily(
            organize(act("a", "BA", "2026-11-28"), act("b", "SP", "2026-11-29"), act("c", "SP", "2026-11-01"), act("d", "SP", call = false)),
            reflections("2026-11-24", "2026-11-23"), quotes("2026-11-24", "2026-11-23"),
        )
        val (s2, f2) = Alerts.collect(s1, d2, all, feeds, today)
        assertEquals(listOf(AlertFeed.W4K3, AlertFeed.QUOTE, AlertFeed.ACTS), f2.map { it.feed })
        assertTrue("Ato b" in f2[2].text && "Ato c" !in f2[2].text && "Ato d" !in f2[2].text && "Ato a" !in f2[2].text, f2[2].text)
        assertTrue("24/11" in f2[0].title)
        assertTrue(Alerts.collect(s2, d2, all, feeds, today).second.isEmpty())
        assertTrue(Alerts.collect(s2, d1, all, feeds, today).first.let { Alerts.collect(it, d2, all, feeds, today).second.isEmpty() }, "sumir e voltar não repete")
    }

    @Test fun `avisos seguem assunto, estado e falha de rede`() {
        val d1 = daily(organize(act("a", "BA", "2026-11-28")), reflections("2026-11-23"), quotes("2026-11-23"))
        val s1 = Alerts.collect(AlertState(), d1, all, feeds, today).first
        val d2 = daily(
            organize(act("a", "BA", "2026-11-28"), act("b", "SP", "2026-11-29"), act("v", "BA", "2026-11-30"), act("n", "BR", "2026-11-30")),
            reflections("2026-11-24", "2026-11-23"), quotes("2026-11-24", "2026-11-23"),
        )
        assertEquals(listOf(AlertFeed.QUOTE), Alerts.collect(s1, d2, all.copy(alertW4k3 = false, alertActs = false), feeds, today).second.map { it.feed })
        val onlyBa = Alerts.collect(s1, d2, all.copy(alertUfs = listOf("BA")), feeds, today).second.single { it.feed == AlertFeed.ACTS }
        assertTrue("Ato v" in onlyBa.text && "Ato b" !in onlyBa.text && "Ato n" !in onlyBa.text, onlyBa.text)
        val (partial, f) = Alerts.collect(s1, d2, all, setOf("motivations"), today)
        assertEquals(1, f.size)
        assertEquals(2, Alerts.collect(partial, d2, all, feeds, today).second.size, "o que falhou avisa depois")
        // Desligar e religar não despeja o que saiu no meio.
        val off = Alerts.collect(s1, d1, all.copy(alertW4k3 = false), feeds, today).first
        assertTrue(Alerts.collect(off, d2, all, feeds, today).second.none { it.feed == AlertFeed.W4K3 })
    }

    @Test fun `aviso de ato só do estado escolhido e para ir presencialmente`() {
        val first = daily(organize(act("velho", "BA", "2026-11-25")))
        val s1 = Alerts.collect(AlertState(), first, all, feeds, today).first
        val d = daily(organize(
            act("velho", "BA", "2026-11-25"), act("vai", "BA", "2026-11-26"), act("hoje", "BA", "2026-11-24"),
            act("nacional", "BR", "2026-11-26"), act("nacionalba", "BR", "2026-11-26", extra = ",\"estados\":[\"BA\"]"),
            act("semdata", "BA"), act("passou", "BA", "2026-11-20"), act("noticia", "BA", "2026-11-26", call = false),
            act("online", "BA", "2026-11-26", title = "Plenária online contra a escala 6x1"), act("live", "BA", "2026-11-27", title = "Live do movimento"),
            act("sp", "SP", "2026-11-26"),
        ))
        val onlyBa = all.copy(alertUfs = listOf("BA"), alertW4k3 = false, alertQuote = false)
        val text = Alerts.collect(s1, d, onlyBa, feeds, today).second.single().text
        assertTrue("Ato vai" in text && "Ato hoje" in text, text)
        listOf("nacional", "semdata", "passou", "noticia", "online", "Live", "Ato sp", "velho").forEach { assertTrue(it !in text, "$it em: $text") }
        assertTrue(Alerts.collect(s1, d, onlyBa.copy(alertUfs = emptyList()), feeds, today).second.isEmpty(), "sem estado, nenhum aviso de ato")
        assertEquals(listOf("hoje", "velho", "vai"), d.attendable(listOf("BA"), today).map { it.id })
    }

    @Test fun `W4K3 traz o vídeo do dia, só do próprio site`() {
        val r = """[{"id":"2026-11-24","date":"2026-11-24","reflection":{"title":"T","content":"C"},
            "video":{"src":"assets/videos/w4k3/2026-11-24.mp4","poster":"assets/videos/w4k3/2026-11-24.jpg"}},
            {"id":"2026-11-23","date":"2026-11-23","reflection":{"title":"T2","content":"C"},"video":{"src":"https://mal.org/x.mp4","poster":"../x.jpg"}}]"""
        val (today, old) = FeedParser.reflections(r)
        assertEquals(FeedParser.SITE + "assets/videos/w4k3/2026-11-24.mp4", today.video)
        assertEquals(FeedParser.SITE + "assets/videos/w4k3/2026-11-24.jpg", Alerts.reflection(today).poster)
        assertEquals(today.video, Alerts.reflection(today).video)
        assertEquals(null, old.video)
        assertEquals(null, old.poster)
        assertEquals(null, FeedParser.siteFile("//mal.org/x"))
    }

    @Test fun `mensagem do W4K3 na conversa vem com o vídeo e o vídeo só vem do site`() {
        site["reflections"] = """[{"id":"2026-11-24","date":"2026-11-24","news":{"title":"N","source":"F"},"reflection":{"title":"T","content":"C"},
            "video":{"src":"assets/videos/w4k3/2026-11-24.mp4","poster":"assets/videos/w4k3/2026-11-24.jpg"}}]"""
        val a = app()
        runBlocking { a.updateContent().join() }
        a.use(Tool.NEWS)!!.let { runBlocking { withTimeout(10_000) { it.join() } } }
        assertEquals(FeedParser.SITE + "assets/videos/w4k3/2026-11-24.mp4", a.last.media?.url)
        assertEquals(Attachment.VIDEO, a.last.media?.kind)
        assertFails { runBlocking { a.video("https://mal.org/x.mp4") } }
        assertFails { runBlocking { a.picture(FeedParser.SITE + "../x.jpg") } }
    }

    @Test fun `foto e vídeo vão uma vez, mesmo sem texto`() {
        fakeModel()
        val e = Recorder()
        val a = app(e)
        a.attachScreen(ScreenContext(null, byteArrayOf(1, 2, 3), Attachment.PHOTO))
        a.ask("")
        assertEquals("O que você acha dessa foto?", a.state.value.messages.last { it.fromUser }.text)
        assertEquals(Attachment.PHOTO, a.state.value.messages.last { it.fromUser }.media?.kind)
        assertTrue(e.requests[0].image != null && "foto que te mandaram" in e.requests[0].user && "<<<DADOS" !in e.requests[0].user, e.requests[0].user)
        assertEquals(null, a.state.value.screen, "a foto vale uma pergunta")
        a.ask("e agora?")
        assertEquals(null, e.requests[1].image)
        a.saveSettings(a.state.value.settings.copy(autoScreen = true))
        a.attachScreen(ScreenContext(null, byteArrayOf(4), Attachment.VIDEO))
        a.ask("oi, tudo bem?")
        assertTrue("4 quadros" in e.requests[2].user, "com leitura automática, o vídeo escolhido vai mesmo em papo")
        assertEquals(null, a.state.value.screen)
    }

    @Test fun `muitos atos viram um aviso só`() {
        val d = daily(organize(*(1..20).map { act("a$it", "SP") }.toTypedArray()))
        val a = Alerts.acts(d.upcoming(emptyList(), today), today)!!
        assertTrue("20 atos" in a.title && "+ 12 no site" in a.text)
    }

    private fun Companion2B.check(): List<Alert>? = runBlocking { withTimeout(10_000) { checkAlerts() } }

    @Test fun `aviso vira mensagem, sobrevive a reabrir e não repete`() {
        site["motivations"] = quotes("2026-11-23")
        val a = app()
        assertEquals(emptyList(), a.check(), "desligado não consulta")
        assertTrue(a.state.value.net.isEmpty())
        a.saveSettings(Settings(alertQuote = true))
        runBlocking { delay(200) }
        assertEquals(0, a.check()!!.size)
        site["motivations"] = quotes("2026-11-24", "2026-11-23")
        assertEquals(1, a.check()!!.size)
        assertEquals(AlertFeed.QUOTE, a.last.alert!!.feed)
        assertTrue("Frase 2026-11-24" in a.last.text)

        val b = app()
        assertEquals(1, b.state.value.messages.count { it.alert != null }, "não lido volta na conversa")
        assertEquals(0, b.check()!!.size)
        b.markAlertsRead()
        runBlocking { withTimeout(5000) { while (b.state.value.alerts.any { !it.read }) delay(10) }; delay(100) }
        val c = app()
        assertEquals(0, c.state.value.messages.count { it.alert != null })
        assertEquals(1, c.state.value.alerts.size)
        site.clear()
        assertNull(c.check(), "sem rede")
    }

    @Test fun `a IA sabe de qual aviso a pessoa fala`() {
        fakeModel()
        val e = Recorder()
        val a = app(e)
        site["motivations"] = quotes("2026-11-23")
        a.saveSettings(Settings(alertQuote = true))
        a.check()
        site["motivations"] = quotes("2026-11-24", "2026-11-23")
        a.check()
        a.ask("de onde veio isso?")
        assertTrue("Frase 2026-11-24" in e.requests.single().user)
        a.ask("e agora, o que mais?")
        assertFalse("AVISO QUE VOCÊ" in e.requests[1].user)
    }

    @Test fun `pânico não espera a rede e nada é gravado depois`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val a = app { _ -> started.countDown(); release.await(8, TimeUnit.SECONDS); quotes("2026-11-24") }
        a.saveSettings(Settings(alertQuote = true))
        delay(200)
        val check = scope.async { a.checkAlerts() }
        started.await(5, TimeUnit.SECONDS)
        withTimeout(4000) { a.panic(includeModels = false).join() }
        assertTrue(data.listFiles().isNullOrEmpty(), data.list()?.joinToString())
        assertFalse(a.state.value.settings.anyAlert)
        release.countDown()
        check.await()
        delay(200)
        assertTrue(data.listFiles().isNullOrEmpty(), data.list()?.joinToString())
        assertTrue(a.state.value.alerts.isEmpty() && a.state.value.messages.none { it.alert != null })
    }
}
