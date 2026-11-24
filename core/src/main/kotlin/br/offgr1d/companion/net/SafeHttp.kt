package br.offgr1d.companion.net

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** Registro do que saiu do aparelho. Fica só na memória e aparece no Inspetor. */
data class NetEvent(val at: Instant, val purpose: String, val url: String, val viaTor: Boolean, val result: String)

class NetLog {
    private val list = CopyOnWriteArrayList<NetEvent>()
    val events: List<NetEvent> get() = list.toList()
    fun add(e: NetEvent) {
        list.add(e)
        while (list.size > MAX) list.removeAt(0)
    }
    fun clear() = list.clear()
    private companion object { const val MAX = 100 }
}

/**
 * Única porta de saída para a rede. Só HTTPS, sem endereços da rede interna, sem cookies, com limite de tamanho.
 * Com Tor ligado, tudo passa pelo proxy; se ele não responder, a conexão falha em vez de sair direto.
 */
class SafeHttp(val log: NetLog, private val torEnabled: () -> Boolean) {

    class Response(val code: Int, val stream: InputStream, val length: Long, private val conn: HttpURLConnection) : AutoCloseable {
        override fun close() {
            runCatching { stream.close() }
            conn.disconnect()
        }
    }

    fun text(url: String, purpose: String, maxBytes: Int = 4 * 1024 * 1024): String =
        open(url, purpose).use { r ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = r.stream.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) throw IOException("resposta grande demais")
            }
            out.toString("UTF-8")
        }

    fun open(url: String, purpose: String, offset: Long = 0): Response {
        val tor = torEnabled()
        var current = url
        try {
            repeat(MAX_REDIRECTS + 1) {
                check(current, resolve = !tor)
                val u = URL(current)
                val conn = (if (tor) u.openConnection(TOR) else u.openConnection(Proxy.NO_PROXY)) as HttpURLConnection
                conn.instanceFollowRedirects = false // cada salto é conferido de novo
                conn.connectTimeout = if (tor) 60_000 else 20_000
                conn.readTimeout = 60_000
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", "2B-companion")
                conn.setRequestProperty("Accept-Language", "pt-BR")
                if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
                val code = conn.responseCode
                if (code in 300..399 && code != 304) {
                    val next = conn.getHeaderField("Location") ?: throw IOException("redirecionamento sem destino")
                    conn.disconnect()
                    current = URI(current).resolve(next).toString()
                    return@repeat
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IOException("HTTP $code")
                }
                log.add(NetEvent(Instant.now(), purpose, url, tor, "HTTP $code"))
                return Response(code, conn.inputStream, conn.contentLengthLong, conn)
            }
            throw IOException("redirecionamentos demais")
        } catch (e: Exception) {
            log.add(NetEvent(Instant.now(), purpose, url, tor, "falhou: ${e.message ?: e.javaClass.simpleName}"))
            throw if (e is IOException) e else IOException(e.message, e)
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        /** Orbot / Tor local. */
        private val TOR = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))

        /** Com Tor não resolvemos o nome aqui: a consulta DNS sairia fora do Tor. */
        fun check(url: String, resolve: Boolean) {
            val uri = runCatching { URI(url) }.getOrNull() ?: throw IOException("endereço inválido")
            if (!"https".equals(uri.scheme, ignoreCase = true)) throw IOException("só HTTPS é permitido")
            if (uri.userInfo != null) throw IOException("endereço com usuário não é permitido")
            val host = uri.host?.lowercase()?.trim('[', ']') ?: throw IOException("endereço sem site")
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
                throw IOException("rede interna bloqueada")
            }
            val literal = host.contains(':') || Regex("[0-9.]+").matches(host)
            if (literal || resolve) {
                val addresses = InetAddress.getAllByName(host)
                if (addresses.any(::isInternal)) throw IOException("rede interna bloqueada")
            }
        }

        private fun isInternal(a: InetAddress): Boolean {
            if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return true
            val b = a.address
            return when (b.size) {
                4 -> (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64 // 100.64.0.0/10
                else -> (b[0].toInt() and 0xfe) == 0xfc // fc00::/7
            }
        }
    }
}
