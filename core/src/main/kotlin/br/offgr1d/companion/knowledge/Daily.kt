package br.offgr1d.companion.knowledge

import java.time.LocalDate
import java.time.ZoneOffset

/** Publicação do W4K3 PR0T0C0L: uma notícia real e a reflexão sobre ela. */
data class Reflection(
    val id: String,
    val date: String?,
    val title: String,
    val excerpt: String,
    val content: String,
    val newsTitle: String?,
    val newsSource: String?,
    val newsLink: String?,
)

data class Quote(val id: String, val date: String, val content: String, val author: String?)

data class Act(
    val id: String,
    val title: String,
    /** true: ato convocado; false: notícia sobre um ato. */
    val call: Boolean,
    val uf: String?,
    val states: List<String>,
    val city: String?,
    val date: String?,
    val hour: String?,
    val published: String?,
    val source: String?,
    val url: String?,
) {
    fun upcoming(today: LocalDate) = call && (date == null || date >= today.toString())

    /** Atos nacionais entram em qualquer estado, como no site e no bot do Telegram. */
    fun inStates(ufs: Collection<String>) =
        ufs.isEmpty() || uf in ufs || (uf == "BR" && (states.isEmpty() || states.any { it in ufs }))

    fun place() = when {
        uf == "BR" -> if (states.isEmpty()) "Nacional" else states.joinToString(", ")
        city != null -> "$city (${uf.orEmpty()})"
        else -> uf ?: "local na fonte"
    }

    fun whenText(today: LocalDate): String {
        val d = date ?: return "data na fonte"
        val day = when (d) {
            today.toString() -> "HOJE, ${brDate(d)}"
            today.plusDays(1).toString() -> "AMANHÃ, ${brDate(d)}"
            else -> brDate(d)
        }
        return if (hour == null) day else "$day às $hour"
    }
}

/** Conteúdo do dia do site, já na ordem de uso. */
class Daily(
    val reflections: List<Reflection> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val acts: List<Act> = emptyList(),
) {
    fun upcoming(ufs: Collection<String>, today: LocalDate = todayBR()): List<Act> =
        acts.filter { it.upcoming(today) && it.inStates(ufs) }
            .sortedWith(compareBy<Act> { it.date ?: "9" }.thenByDescending { it.published.orEmpty() })
}

/** Data de hoje em Brasília, como no site. */
fun todayBR(): LocalDate = LocalDate.now(ZoneOffset.ofHours(-3))

/** 2026-11-24 -> 24/11 */
fun brDate(iso: String?): String =
    if (iso != null && Regex("\\d{4}-\\d{2}-\\d{2}.*").matches(iso)) "${iso.substring(8, 10)}/${iso.substring(5, 7)}" else ""
