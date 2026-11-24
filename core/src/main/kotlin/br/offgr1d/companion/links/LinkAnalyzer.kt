package br.offgr1d.companion.links

import br.offgr1d.companion.text.Text
import java.net.IDN
import java.net.URLDecoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Análise de links do Fact Check do 0FF-GR1D (js/fact-check/link.js), portada para Kotlin.
 * Olha só o ENDEREÇO: o site nunca é aberto nem baixado, e nada sai do aparelho.
 */
@Serializable
data class LinkConfig(
    val trusted: List<Trusted> = emptyList(),
    val shorteners: List<String> = emptyList(),
    val riskyTlds: List<String> = emptyList(),
    val scamWords: List<String> = emptyList(),
    val dangerousExtensions: List<String> = emptyList(),
) {
    @Serializable
    data class Trusted(
        val name: String,
        @SerialName("type") val type: String = "",
        val domains: List<String> = emptyList(),
        val brands: List<String> = emptyList(),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(raw: String): LinkConfig = json.decodeFromString(serializer(), raw)
    }
}

enum class Level { INFO, OK, WARN, DANGER }
enum class Verdict { OK, UNKNOWN, WARN, DANGER }
data class Signal(val level: Level, val title: String, val detail: String)
data class HostParts(val domain: String, val label: String, val suffix: String, val sub: String)

data class LinkReport(
    val url: String,
    val host: String,
    val verdict: Verdict,
    val signals: List<Signal>,
    val trustedName: String?,
) {
    fun summary(): String = "$host: " + when (verdict) {
        Verdict.DANGER -> "PERIGO"
        Verdict.WARN -> "CUIDADO"
        Verdict.OK -> "ENDEREÇO OFICIAL"
        Verdict.UNKNOWN -> "DESCONHECIDO"
    }
}

class LinkAnalyzer(private val cfg: LinkConfig) {

    /** null se não há link http(s) reconhecível no texto. */
    fun analyze(input: String): LinkReport? = extractUrls(input, 1).firstOrNull()?.let(::analyzeUrl)

    fun analyzeAll(text: String, max: Int = 3): List<LinkReport> =
        extractUrls(text, max * 3).mapNotNull(::analyzeUrl).distinctBy { it.url }.take(max)

    private class Parts(val scheme: String, val user: String?, val host: String, val port: String?, val path: String, val query: String)

    private fun split(raw: String): Parts? {
        val m = URL.matchEntire(if (SCHEME.containsMatchIn(raw)) raw else "https://$raw") ?: return null
        val scheme = m.groupValues[1].lowercase()
        val authority = m.groupValues[2]
        val at = authority.lastIndexOf('@')
        val user = if (at >= 0) authority.substring(0, at) else null
        val hostPort = authority.substring(at + 1)
        val pm = PORT.find(hostPort)
        val rawHost = (if (pm != null) hostPort.substring(0, pm.range.first) else hostPort).trimEnd('.').lowercase()
        if (rawHost.isEmpty()) return null
        val host = if (rawHost.startsWith("[")) rawHost else
            runCatching { IDN.toASCII(rawHost, IDN.ALLOW_UNASSIGNED).lowercase() }.getOrNull() ?: return null
        val default = if (scheme == "http") "80" else "443"
        return Parts(scheme, user, host, pm?.groupValues?.get(1)?.takeIf { it != default && it.isNotEmpty() },
            m.groupValues[3].ifEmpty { "/" }, m.groupValues[4])
    }

    private fun analyzeUrl(raw: String): LinkReport? {
        val u = split(raw) ?: return null
        val host = u.host
        val parts = splitHost(host)
        val signals = ArrayList<Signal>()
        fun add(level: Level, title: String, detail: String) { signals += Signal(level, title, detail) }

        val trusted = cfg.trusted.find { t -> t.domains.any { host == it || host.endsWith(".$it") } }
        val pathTokens = Text.stripAccents(u.path + " " + u.query).split(NON_ALNUM).filter { it.isNotEmpty() }
        val hostTokens = host.split('.', '-').filter { it.isNotEmpty() }
        val riskyTld = parts.suffix.substringAfterLast('.') in cfg.riskyTlds
        val scamHits = cfg.scamWords.filter { it in hostTokens || it in pathTokens }.distinct()
        val ip = isIp(host)

        // 1. Disfarces no próprio endereço
        if (u.user != null) {
            val shown = runCatching { URLDecoder.decode(u.user, "UTF-8") }.getOrDefault(u.user)
            add(Level.DANGER, "ENDEREÇO DISFARÇADO",
                "Tudo antes do \"@\" é ignorado pelo navegador. Este link leva para $host, e não para \"$shown\".")
        }
        if (ip) add(Level.DANGER, "SEM NOME DE SITE",
            "O link usa só números (endereço IP) no lugar do nome do site. Sites de notícia e bancos não fazem isso.")
        if (host.split('.').any { it.startsWith("xn--") }) add(Level.DANGER, "LETRAS DISFARÇADAS",
            "O nome usa letras de outros alfabetos que parecem iguais às nossas. Aparece como " +
                "\"${hostToUnicode(host)}\", mas é outro site: $host.")

        // 2. Site conhecido ou imitação
        if (trusted != null && trusted.type == "plataforma") {
            add(Level.INFO, "REDE SOCIAL: QUEM POSTOU?",
                "${parts.domain} é mesmo o ${trusted.name}, mas qualquer pessoa publica lá. O site ser verdadeiro não " +
                    "diz nada sobre o conteúdo: veja quem é o perfil e se a informação aparece em veículos de imprensa.")
        } else if (trusted != null) {
            val what = mapOf("imprensa" to "veículo de imprensa", "checagem" to "agência de checagem",
                "governo" to "site do governo", "servico" to "serviço")[trusted.type] ?: "site"
            add(Level.OK, "SITE OFICIAL", "${parts.domain} é o endereço oficial de ${trusted.name} ($what).")
        } else if (!ip) {
            val imitated = findImitated(parts)
            if (imitated != null) {
                add(Level.DANGER, "IMITA ${imitated.first.name.uppercase()}",
                    "\"${parts.domain}\" ${imitated.second}. É um truque comum para parecer o site verdadeiro.")
            } else {
                val flat = hostTokens.joinToString("")
                // Sites de governo de outros países (nasa.gov, gov.uk) não fingem ser o gov.br.
                val foreignGov = FOREIGN_GOV.containsMatchIn(parts.suffix)
                val brand = cfg.trusted.find { t ->
                    !(foreignGov && t.type == "governo") &&
                        t.brands.any { it in hostTokens || (it.length >= 6 && it in flat) }
                }
                if (brand != null) {
                    val serious = brand.type == "governo" ||
                        (brand.type in setOf("servico", "imprensa") && (riskyTld || scamHits.isNotEmpty()))
                    add(if (serious) Level.DANGER else Level.WARN, "USA O NOME DE ${brand.name.uppercase()}",
                        "O endereço tem o nome \"${brand.name}\", mas o site de verdade é ${parts.domain}, que não é o " +
                            "oficial (${brand.domains.joinToString(", ")}).")
                }
            }
        }

        // 3. Outros sinais
        if (cfg.shorteners.any { host == it || host.endsWith(".$it") }) add(Level.WARN, "LINK ENCURTADO",
            "Não dá para saber para onde ele leva sem abrir. Peça o link completo a quem mandou.")
        if (u.scheme == "http") add(Level.WARN, "SEM CADEADO (HTTP)",
            "A conexão não é protegida. Nunca digite senha, CPF ou dados de cartão num site assim.")
        if (riskyTld && trusted == null) add(Level.WARN, "FINAL \".${parts.suffix.uppercase()}\"",
            "Esse final de endereço é barato e muito usado em golpes. Não é prova, mas pede cuidado.")
        val ext = EXT.find(u.path.substringAfterLast('/').lowercase())?.groupValues?.get(1)
        if (ext != null && ext in cfg.dangerousExtensions) add(Level.DANGER, "BAIXA UM ARQUIVO .${ext.uppercase()}",
            "Este link baixa um arquivo que pode instalar um programa no seu aparelho. Não abra.")
        if (scamHits.isNotEmpty() && trusted == null) add(Level.WARN, "PALAVRAS DE GOLPE",
            "O endereço tem ${scamHits.joinToString(", ") { "\"$it\"" }}, comuns em golpes de Pix, prêmio e cadastro.")
        if (parts.sub.split('.').count { it.isNotEmpty() } >= 3) add(Level.WARN, "ENDEREÇO COMPRIDO DEMAIS",
            "Muitas partes antes do site de verdade (${parts.domain}). Isso costuma esconder o nome real.")
        if (Regex("\\d{4,}").containsMatchIn(parts.label) || parts.label.length > 30) add(Level.WARN, "NOME ESQUISITO",
            "\"${parts.label}\" não parece o nome de um site sério: muitos números ou letras.")
        if (u.port != null) add(Level.WARN, "PORTA INCOMUM", "O link usa a porta ${u.port}. Sites comuns não aparecem assim.")

        if (trusted == null && signals.none { it.level != Level.INFO }) add(Level.INFO, "SITE DESCONHECIDO",
            "${parts.domain} não está na nossa lista. Isso não é ruim por si só: veja quem é o dono do site.")

        val verdict = when {
            signals.any { it.level == Level.DANGER } -> Verdict.DANGER
            signals.any { it.level == Level.WARN } -> Verdict.WARN
            trusted != null && trusted.type != "plataforma" -> Verdict.OK
            else -> Verdict.UNKNOWN
        }
        return LinkReport(raw, host, verdict, signals, trusted?.name)
    }

    private fun findImitated(parts: HostParts): Pair<LinkConfig.Trusted, String>? {
        val label = parts.label.replace("-", "")
        for (t in cfg.trusted) for (d in t.domains) {
            val off = splitHost(d)
            val offFlat = off.label.replace("-", "")
            if (offFlat.length < 3) continue
            if (label == offFlat) {
                if (parts.domain != off.domain) return t to "tem o mesmo nome de $d, mas termina em \".${parts.suffix}\""
            } else if (offFlat.length >= 4 && confusable(label) == confusable(offFlat)) {
                return t to "troca letras parecidas (0 por o, 1 por l, rn por m...) para parecer $d"
            } else if (offFlat.length >= 8) {
                // Nomes curtos ficam de fora: "baixa" está a uma letra de "caixa" e não é golpe.
                val dist = levenshtein(label, offFlat)
                if (dist <= 2) return t to "é quase igual a $d, com ${if (dist == 1) "uma letra" else "duas letras"} de diferença"
            }
        }
        return null
    }

    companion object {
        // Finais de domínio com duas partes: em "noticias.uol.com.br" o site é "uol.com.br".
        private val MULTI_SUFFIXES = ("com.br gov.br org.br net.br edu.br art.br blog.br jus.br leg.br mp.br mil.br ind.br " +
            "inf.br tv.br eco.br app.br dev.br log.br nom.br emp.br co.uk org.uk gov.uk ac.uk com.ar com.pt com.mx com.au " +
            "co.jp co.in com.co").split(' ').toSet()
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        private val URL = Regex("^([hH][tT][tT][pP][sS]?)://([^/?#\\\\]+)([^?#]*)(\\??[^#]*)(?:#.*)?$")
        private val PORT = Regex(":(\\d*)$")
        private val EXT = Regex("\\.([a-z0-9]{1,4})$")
        private val NON_ALNUM = Regex("[^a-z0-9]+")
        private val FOREIGN_GOV = Regex("^(gov|mil)(\\.|$)")
        private val FULL = Regex("\\bhttps?://[^\\s<>\"'`]+", RegexOption.IGNORE_CASE)
        private val BARE = Regex("(?:^|[\\s(<])((?:[a-z0-9\\u00a1-\\uffff-]+\\.)+[a-z\\u00a1-\\uffff]{2,}(?::\\d+)?(?:[/?#][^\\s<>\"'`]*)?)",
            RegexOption.IGNORE_CASE)
        private val TRAIL = Regex("[).,;:!?\\]}>»\"']+$")

        fun extractUrls(text: String, max: Int = 5): List<String> {
            val full = FULL.findAll(text).map { it.value }
            val found = if (full.any()) full else BARE.findAll(text).map { it.groupValues[1] }
            return found.map { it.replace(TRAIL, "") }.filter { it.isNotEmpty() }.distinct().take(max).toList()
        }

        fun splitHost(host: String): HostParts {
            if (isIp(host)) return HostParts(host, host, "", "")
            val p = host.split('.')
            val n = if (p.size > 2 && p.takeLast(2).joinToString(".") in MULTI_SUFFIXES) 2 else 1
            return HostParts(
                domain = p.takeLast(n + 1).joinToString("."),
                label = p.getOrElse(p.size - n - 1) { "" },
                suffix = p.takeLast(n).joinToString("."),
                sub = p.dropLast(n + 1).joinToString("."),
            )
        }

        fun hostToUnicode(host: String): String = IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED)

        /** Inclui as formas que o navegador também aceita como IP: "3232235786" e "0xC0A8000A". */
        fun isIp(host: String): Boolean = host.startsWith("[") ||
            host.split('.').let { p -> p.size <= 4 && p.all { Regex("\\d+|0x[0-9a-f]+").matches(it) } }

        /** Desfaz trocas visuais e letras dobradas: "g1obo" e "gloobo" viram "globo". */
        private fun confusable(s: String): String = s.replace("rn", "m").replace("vv", "w").replace("cl", "d")
            .replace('0', 'o').replace(Regex("[1i|]"), "l").replace('3', 'e').replace('4', 'a')
            .replace('5', 's').replace('7', 't').replace(Regex("(.)\\1+"), "$1")

        fun levenshtein(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
            return prev[b.length]
        }
    }
}
