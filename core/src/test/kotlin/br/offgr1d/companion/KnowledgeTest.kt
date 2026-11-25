package br.offgr1d.companion

import br.offgr1d.companion.knowledge.DocKind
import br.offgr1d.companion.knowledge.FeedParser
import br.offgr1d.companion.knowledge.KnowledgeBase
import br.offgr1d.companion.knowledge.KnowledgeIndex
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KnowledgeTest {
    private val today = LocalDate.parse("2026-11-23")
    private fun seed(n: String) = javaClass.getResource("/seed/$n.json")!!.readText()

    private val organize = """{"eventos":[
        {"id":"1","titulo":"Centrais convocam ato em Salvador","tipo":"convocacao","uf":"BA","cidade":"Salvador","publicadoEm":"2026-11-20T10:00:00Z","fonte":{"nome":"Jornal","url":"https://exemplo.org/a"},"data":"2026-11-28"},
        {"id":"2","titulo":"Ato em Recife reúne milhares","tipo":"cobertura","uf":"PE","publicadoEm":"2026-11-21T10:00:00Z","fonte":{"nome":"Jornal","url":"https://exemplo.org/b"}},
        {"id":"3","titulo":"Ato passado em Feira","tipo":"convocacao","uf":"BA","publicadoEm":"2026-11-01T10:00:00Z","fonte":{"nome":"Jornal","url":"https://exemplo.org/c"},"data":"2026-11-10"},
        {"titulo":"sem id"}, "lixo"]}"""

    private val index = KnowledgeIndex(
        FeedParser.parse("guides", seed("guides")) + FeedParser.parse("chatbot", seed("chatbot")) +
            FeedParser.parse("organize", organize),
    )

    private fun top(q: String) = index.search(q, today = today).firstOrNull()?.doc?.id

    @Test fun `pergunta comum acha o guia certo`() {
        assertEquals("guia:cai-no-golpe", top("caí num golpe do pix, e agora?"))
        assertEquals("guia:conta-invadida", top("hackearam minha conta do instagram"))
        assertEquals("guia:exposicao", top("vazaram meus dados e estou recebendo ameaça"))
        assertEquals("guia:direitos-ato", top("quais meus direitos se a polícia me abordar no protesto"))
        assertEquals("guia:direitos-ato", top("quais meus direitos num protesto?"))
        assertEquals("guia:gas-ato", top("e se jogarem gás?"))
        assertEquals("site:facescan", top("o que é o F4C3 SC4N"))
    }

    @Test fun `assunto sem fonte não devolve nada`() {
        assertTrue(index.search("receita de bolo de cenoura", today = today).isEmpty())
        assertTrue(index.search("oi", today = today).isEmpty())
    }

    @Test fun `atos filtram por estado e escondem os que já passaram`() {
        val ids = index.search("tem manifestação na Bahia?", limit = 10, today = today).map { it.doc.id }
        assertTrue("ato:1" in ids, ids.toString())
        assertTrue("ato:2" !in ids && "ato:3" !in ids, ids.toString())
        assertEquals("ato:1", ids.first())
    }

    @Test fun `estado não é confundido com palavra comum`() {
        assertNull(KnowledgeIndex.detectUf("vou para o ato e se chover"))
        assertEquals("PA", KnowledgeIndex.detectUf("atos no Pará"))
        assertEquals("MS", KnowledgeIndex.detectUf("protesto em mato grosso do sul"))
        assertEquals("SP", KnowledgeIndex.detectUf("ato em SP"))
    }

    @Test fun `cobertura não vira convocação e data ausente é dita`() {
        val docs = FeedParser.parse("organize", organize)
        assertEquals(3, docs.size)
        assertTrue("não é convocação" in docs[1].text && "não informada" in docs[1].text)
    }

    @Test fun `reflexão é marcada como opinião`() {
        val d = FeedParser.parse("reflections", """[{"id":"x","date":"2026-11-23","news":{"title":"N","link":"https://a.org","source":"S"},
            "reflection":{"title":"T","content":"<p>Corpo <b>forte</b></p>"}}]""").single()
        assertEquals(DocKind.REFLEXAO, d.kind)
        assertTrue("OPINIÃO" in d.kind.label)
        assertTrue("Corpo forte" in d.text.replace(Regex("\\s+"), " "))
    }

    /** Opcional: FEEDS_DIR=.../0FF-GR1D_WEB/data confere o app com o conteúdo real do site. */
    @Test fun `feeds reais do site`() {
        val dir = System.getenv("FEEDS_DIR")?.let(::File) ?: return
        FeedParser.FEEDS.forEach { name ->
            val docs = FeedParser.parse(name, File(dir, "$name.json").readText())
            println("$name: ${docs.size}")
            if (name != "factcheck") assertTrue(docs.isNotEmpty(), name)
        }
    }

    @Test fun `link que não é https é descartado`() {
        assertTrue(FeedParser.parse("factchecks", """{"items":[{"title":"x","link":"javascript:alert(1)"}]}""").isEmpty())
    }

    @Test fun `feed com formato errado não substitui o que funciona`() {
        val store = memoryStore()
        assertFails { FeedParser.parse("organize", """{"outra":1}""") }
        val kb = KnowledgeBase(store) { url -> if ("organize" in url) organize else "<html>erro</html>" }
        kb.load()
        val before = kb.index.docs.size
        val r = kb.update()
        assertEquals(listOf("organize"), r.ok)
        assertEquals(5, r.failed.size)
        assertEquals(before + 3, kb.index.docs.size)
        assertEquals("perigo", if (kb.links.analyze("https://g1obo.com")!!.verdict.name == "DANGER") "perigo" else "ok")
    }
}
