package nl.agroqirax.calendartile

/** How a [CustomIconRule]'s keyword is compared against an event. */
enum class MatchMode {
    WORDS, // Every word of the keyword must appear, in any order.
    REGEX; // The keyword is a regular expression, matched against the raw text.

    companion object {
        fun fromKey(key: String?): MatchMode = entries.firstOrNull { it.name == key } ?: WORDS
    }
}

/**
 * A keyword the user has mapped to an icon themselves.
 *
 * Checked before the built-in table, so a custom rule always beats whatever
 * [EventIconMapper] would otherwise have picked for the same word.
 *
 * [exact] off, case and accents are folded away before comparing; on, they must match as typed.
 */
data class CustomIconRule(
    val keyword: String,
    val iconName: String,
    val mode: MatchMode = MatchMode.WORDS,
    val exact: Boolean = false
) {
    companion object {
        operator fun invoke(
            keywords: List<String>,
            iconName: String,
            mode: MatchMode = MatchMode.WORDS,
            exact: Boolean = false
        ): List<CustomIconRule> = keywords.map { CustomIconRule(it, iconName, mode, exact) }
    }
}

/**
 * Stores custom rules as a single preference string: one rule per line, fields
 * separated by tabs.
 *
 * Deliberately not JSON, a format this simple stays parseable without a dependency
 */
object CustomIconRules {

    private const val FIELD_SEPARATOR = "\t"

    fun encode(rules: List<CustomIconRule>): String =
        rules.joinToString("\n") {
            listOf(sanitize(it.keyword), it.iconName, it.mode.name, it.exact.toString())
                .joinToString(FIELD_SEPARATOR)
        }

    fun decode(raw: String?): List<CustomIconRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split(FIELD_SEPARATOR)
                if (parts.size != 4) return@mapNotNull null
                val keyword = parts[0].trim()
                val iconName = parts[1].trim()
                when {
                    keyword.isEmpty() || iconName.isEmpty() -> null
                    // Silently drop rules pointing at missing icons
                    TileIcons.resIdFor(iconName) == null -> null
                    else -> CustomIconRule(
                        keyword = keyword,
                        iconName = iconName,
                        mode = MatchMode.fromKey(parts[2].trim()),
                        exact = parts[3].trim().toBoolean()
                    )
                }
            }
            .toList()
    }

    private fun sanitize(keyword: String): String =
        keyword.replace('\t', ' ').replace('\n', ' ').trim()
}
