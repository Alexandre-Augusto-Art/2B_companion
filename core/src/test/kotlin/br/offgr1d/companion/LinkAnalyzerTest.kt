package br.offgr1d.companion

import br.offgr1d.companion.links.LinkAnalyzer
import br.offgr1d.companion.links.LinkConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mesmos casos de tests/fact-check/link.test.js do 0FF-GR1D: app e site precisam concordar. */
class LinkAnalyzerTest {
    private val analyzer = LinkAnalyzer(LinkConfig.parse(javaClass.getResource("/seed/factcheck.json")!!.readText()))

    private val cases = listOf(
        Triple("https://g1.globo.com/politica/noticia/2026/11/17/governo-anuncia-novo-programa.ghtml", "OK", "SITE OFICIAL"),
        Triple("folha.uol.com.br/mercado/2026/11/dolar-cai.shtml", "OK", "SITE OFICIAL"),
        Triple("https://www.gov.br/saude/pt-br", "OK", "SITE OFICIAL"),
        Triple("https://www.nasa.gov/news", "UNKNOWN", "SITE DESCONHECIDO"),
        Triple("https://g1-globo.com/noticia/urgente", "WARN", "USA O NOME DE G1"),
        Triple("https://g1obo.com/noticia", "DANGER", "IMITA G1"),
        Triple("https://nubank.com/pix", "DANGER", "IMITA NUBANK"),
        Triple("https://caixa-auxilio.online/resgatar", "DANGER", "USA O NOME DE CAIXA"),
        Triple("https://govbr-regularizar.com/cpf", "DANGER", "USA O NOME DE GOVERNO FEDERAL"),
        Triple("https://estadaoo.com.br/x", "DANGER", "IMITA ESTADÃO"),
        Triple("https://metropolles.com/x", "DANGER", "IMITA METRÓPOLES"),
        Triple("https://baixa-receitas.com.br/bolo", "UNKNOWN", "SITE DESCONHECIDO"),
        Triple("https://serra.com.br", "UNKNOWN", "SITE DESCONHECIDO"),
        Triple("https://bit.ly/3abcXYZ", "WARN", "LINK ENCURTADO"),
        Triple("http://exemplo.com.br/noticia", "WARN", "SEM CADEADO"),
        Triple("https://g1.globo.com@golpe.xyz/noticia", "DANGER", "ENDEREÇO DISFARÇADO"),
        Triple("http://192.168.0.10/login", "DANGER", "SEM NOME DE SITE"),
        Triple("https://xn--nbank-kva.com.br", "DANGER", "LETRAS DISFARÇADAS"),
        Triple("https://atualiza-app.com/whatsapp.apk", "DANGER", "BAIXA UM ARQUIVO .APK"),
        Triple("https://www.youtube.com/watch?v=abc", "UNKNOWN", "REDE SOCIAL"),
        Triple("https://a.b.c.d.exemplo.com/", "WARN", "ENDEREÇO COMPRIDO"),
        Triple("https://premio-123456.top/", "WARN", "FINAL \".TOP\""),
        // Só no app
        Triple("https://nübank.com.br", "DANGER", "LETRAS DISFARÇADAS"),
        Triple("http://3232235786/", "DANGER", "SEM NOME DE SITE"),
        Triple("https://exemplo.com.br:8443/a", "WARN", "PORTA INCOMUM"),
    )

    @Test fun `mesmos vereditos do site`() = cases.forEach { (input, verdict, signal) ->
        val r = analyzer.analyze(input) ?: error("não analisou $input")
        val titles = r.signals.map { it.title }
        assertEquals(verdict, r.verdict.name, "$input -> $titles")
        assertTrue(titles.any { it.startsWith(signal) }, "$input: faltou $signal em $titles")
    }

    @Test fun `acha links no meio da mensagem`() {
        assertEquals("https://bit.ly/xyz123", analyzer.analyze("URGENTE!!! veja: https://bit.ly/xyz123. Compartilhe!")!!.url)
        assertNull(analyzer.analyze("isso é verdade?"))
        assertEquals(2, analyzer.analyzeAll("https://g1obo.com e https://bit.ly/a").size)
    }

    @Test fun `letras disfarçadas aparecem legíveis`() = assertEquals("nübank.com.br", LinkAnalyzer.hostToUnicode("xn--nbank-kva.com.br"))

    @Test fun `entrada estranha não quebra`() {
        listOf("https://", "https://@", "http://[::1]/x", "https://%zz@a.com/", "a..b.com", "https://${"a".repeat(80)}.com", "javascript:alert(1)")
            .forEach { analyzer.analyze(it) }
    }
}
