package nl.agroqirax.calendartile

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Runs user-supplied regular expressions safely enough to call from the tile.
 *
 * Two hazards, both handled here so callers never have to think about them:
 *
 * 1. **An invalid pattern must be inert.** compilation failure means "never matches", not a crash.
 *
 * 2. **Catastrophic backtracking would be an ANR.** a pattern like `(a+)+$` against the wrong title would
 *    hang SystemUI, not just this app.
 */
object RegexMatching {
    private const val READ_BUDGET = 200_000

    /** Cap on cached compiled patterns, so live-as-you-type validation in the dialog can't grow this unbounded. */
    private const val CACHE_CAPACITY = 64

    /** Compiled patterns, kept because the tile re-matches every rule on every refresh. LRU-evicted at [CACHE_CAPACITY]. */
    private val cache = object : LinkedHashMap<CacheKey, Pattern?>(CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Pattern?>): Boolean =
            size > CACHE_CAPACITY
    }

    private data class CacheKey(val pattern: String, val caseSensitive: Boolean)

    /** True if [pattern] compiles — used by the dialog to validate as you type. */
    fun isValid(pattern: String): Boolean = compile(pattern, caseSensitive = false) != null

    /**
     * Whether [pattern] matches anywhere in [text].
     *
     * The caller decides what [text] is: a rule that folds case and accents passes
     * the folded form and [caseSensitive] false, and an exact rule passes the raw
     * title and true. Folding here would be wrong — it would strip the very accents
     * an exact pattern is trying to match.
     */
    fun containsMatchIn(pattern: String, text: String, caseSensitive: Boolean = false): Boolean {
        val compiled = compile(pattern, caseSensitive) ?: return false
        return try {
            compiled.matcher(BudgetedCharSequence(text, READ_BUDGET)).find()
        } catch (e: BudgetExhaustedException) {
            false
        } catch (e: StackOverflowError) {
            false
        }
    }

    @Synchronized
    private fun compile(pattern: String, caseSensitive: Boolean): Pattern? =
        cache.getOrPut(CacheKey(pattern, caseSensitive)) {
            val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            try {
                Pattern.compile(pattern, flags)
            } catch (e: PatternSyntaxException) {
                null
            }
        }

    private class BudgetExhaustedException : RuntimeException(null, null, false, false)

    /** Mutable read counter shared by a [BudgetedCharSequence] and every subSequence taken from it. */
    private class Budget(var remaining: Int)

    // The regex engine reads through `charAt` as it backtracks, so capping reads caps total work.
    private class BudgetedCharSequence(
        private val inner: CharSequence,
        private val budget: Budget
    ) : CharSequence {

        constructor(inner: CharSequence, budget: Int) : this(inner, Budget(budget))

        override val length: Int get() = inner.length

        override fun get(index: Int): Char {
            if (budget.remaining-- <= 0) throw BudgetExhaustedException()
            return inner[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            BudgetedCharSequence(inner.subSequence(startIndex, endIndex), budget)

        override fun toString(): String = inner.toString()
    }
}
