package nl.agroqirax.calendartile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexMatchingTest {

    @Test
    fun `matches anywhere in the text, case-insensitively`() {
        assertTrue(RegexMatching.containsMatchIn("^oud papier", "Oud papier en karton"))
        assertTrue(RegexMatching.containsMatchIn("karton$", "Oud papier en karton"))
        assertTrue(RegexMatching.containsMatchIn("PAPIER", "oud papier"))
    }

    @Test
    fun `sees raw text, so accents in a pattern still work`() {
        // The word matcher folds accents away; regex deliberately does not, or a
        // pattern containing an accent could never match anything.
        assertTrue(RegexMatching.containsMatchIn("café", "Café met Anna"))
    }

    @Test
    fun `an invalid pattern never throws and never matches`() {
        // Ordinary ways people write event titles that happen to be broken patterns.
        assertFalse(RegexMatching.isValid("*important*"))
        assertFalse(RegexMatching.containsMatchIn("*important*", "*important* meeting"))
        assertFalse(RegexMatching.isValid("Standup (daily"))
        assertFalse(RegexMatching.isValid("Meeting [EU"))
    }

    /**
     * The case for WORDS being the default, in one test.
     *
     * `C++ review` is a *valid* Java pattern — `++` is a possessive quantifier — so it
     * compiles happily and then means "one or more C". It matches "CC review" and not
     * the literal text the user typed. Silently wrong beats loudly broken for
     * unpleasantness, and it is exactly what happens if plain keywords are treated as
     * patterns.
     */
    @Test
    fun `plain text can be a valid pattern that means something else entirely`() {
        assertTrue(RegexMatching.isValid("C++ review"))
        assertFalse(RegexMatching.containsMatchIn("C++ review", "C++ review"))
        assertTrue(RegexMatching.containsMatchIn("C++ review", "CC review"))
    }

    @Test
    fun `a valid pattern reports as valid`() {
        assertTrue(RegexMatching.isValid("^oud papier"))
        assertTrue(RegexMatching.isValid("\\bf1\\b"))
    }

    /**
     * The important one. `(a+)+$` against a long non-matching string is the classic
     * exponential-backtracking case; unbounded it runs effectively forever and would
     * ANR SystemUI from the tile's main thread. The timeout makes a regression fail
     * the build instead of hanging CI.
     */
    @Test(timeout = 5_000)
    fun `a catastrophic pattern gives up instead of hanging`() {
        val evil = "(a+)+$"
        val input = "a".repeat(60) + "!"
        assertFalse(RegexMatching.containsMatchIn(evil, input))
    }

    @Test(timeout = 5_000)
    fun `nested alternation also gives up`() {
        assertFalse(RegexMatching.containsMatchIn("(a|aa)+$", "a".repeat(60) + "!"))
    }

    @Test
    fun `the budget does not break ordinary long text`() {
        // A realistic title must still match after the pathological guard is in place.
        val title = "Weekly ".repeat(200) + "standup"
        assertTrue(RegexMatching.containsMatchIn("standup", title))
    }
}
