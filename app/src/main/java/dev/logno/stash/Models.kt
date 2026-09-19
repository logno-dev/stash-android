package dev.logno.stash

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.Locale
import kotlin.math.min

@Serializable
data class Bookmark(
    val id: Int,
    val url: String? = null,
    val title: String,
    val notes: String? = null,
    val tags: String? = null,
    val domain: String,
    val createdAt: String? = null,
)

@Serializable
data class Draft(val id: Int? = null, val url: String = "", val notes: String = "", val tags: String = "") {
    fun validate(): String? = when {
        url.isBlank() && notes.isBlank() -> "Add a link or some notes."
        url.isNotBlank() && !isWebUrl(url.trim()) -> "Enter a complete http:// or https:// link."
        else -> null
    }
}

fun isWebUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme.lowercase(Locale.ROOT) in listOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

/** Keep the original text, including additional links, so sharing never silently drops context. */
fun sharedDraft(text: String?, subject: String?): Draft? {
    val content = text.orEmpty().trim()
    val title = subject.orEmpty().trim()
    if (content.isBlank() && title.isBlank()) return null
    val link = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(content)
        .map { match ->
            var candidate = match.value.trimEnd('.', ',', ';', '!', '?', '"', '\'')
            while (candidate.endsWith(')') && candidate.count { it == ')' } > candidate.count { it == '(' }) {
                candidate = candidate.dropLast(1)
            }
            candidate
        }.firstOrNull(::isWebUrl).orEmpty()
    return Draft(url = link, notes = listOf(title, content).filter { it.isNotBlank() }.distinct().joinToString("\n\n"))
}

/** Substring and typo-tolerant word matching across the same fields as the web app. */
fun searchBookmarks(bookmarks: List<Bookmark>, query: String, notesOnly: Boolean): List<Bookmark> {
    val terms = query.lowercase(Locale.ROOT).trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return bookmarks.filter { !notesOnly || it.url.isNullOrBlank() }.mapNotNull { bookmark ->
        val fields = listOf(bookmark.title, bookmark.url.orEmpty(), bookmark.notes.orEmpty(), bookmark.tags.orEmpty())
            .map { it.lowercase(Locale.ROOT) }
        val words by lazy { fields.flatMap { it.split(Regex("[^\\p{L}\\p{N}]+")) } }
        var score = 0
        for (term in terms) {
            if (fields.any { term in it }) continue
            val tolerance = (term.length * 0.3).toInt().coerceAtMost(3)
            val distance = words.asSequence().filter { kotlin.math.abs(it.length - term.length) <= tolerance }
                .minOfOrNull { editDistance(term, it) } ?: Int.MAX_VALUE
            if (distance > tolerance) return@mapNotNull null
            score += distance
        }
        bookmark to score
    }.sortedBy { it.second }.map { it.first }
}

private fun editDistance(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    a.forEachIndexed { i, ca ->
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        b.forEachIndexed { j, cb ->
            current[j + 1] = min(min(current[j] + 1, previous[j + 1] + 1), previous[j] + if (ca == cb) 0 else 1)
        }
        previous = current
    }
    return previous[b.length]
}
