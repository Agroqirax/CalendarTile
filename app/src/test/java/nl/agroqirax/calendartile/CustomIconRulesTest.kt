package nl.agroqirax.calendartile

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomIconRulesTest {

    @Test
    fun `a list of keywords expands to one rule per keyword`() {
        val expanded = CustomIconRule(listOf("nasa", "esa", "jaxa"), TileIcons.ROCKET_LAUNCH)
        assertEquals(
            listOf(
                CustomIconRule("nasa", TileIcons.ROCKET_LAUNCH),
                CustomIconRule("esa", TileIcons.ROCKET_LAUNCH),
                CustomIconRule("jaxa", TileIcons.ROCKET_LAUNCH)
            ),
            expanded
        )
    }

    @Test
    fun `a list of keywords carries mode and exact to every rule it expands to`() {
        val expanded = CustomIconRule(
            listOf("^f1\\b", "grand prix"),
            TileIcons.SPORTS_MOTORSPORTS,
            MatchMode.REGEX,
            exact = true
        )
        assertEquals(
            listOf(
                CustomIconRule("^f1\\b", TileIcons.SPORTS_MOTORSPORTS, MatchMode.REGEX, exact = true),
                CustomIconRule("grand prix", TileIcons.SPORTS_MOTORSPORTS, MatchMode.REGEX, exact = true)
            ),
            expanded
        )
    }

    @Test
    fun `round-trips through the stored format`() {
        val rules = listOf(
            CustomIconRule("standup", TileIcons.GROUPS),
            CustomIconRule("f1", TileIcons.SPORTS_MOTORSPORTS),
            CustomIconRule("team lunch", TileIcons.RESTAURANT)
        )
        assertEquals(rules, CustomIconRules.decode(CustomIconRules.encode(rules)))
    }

    @Test
    fun `empty input decodes to no rules`() {
        assertEquals(emptyList<CustomIconRule>(), CustomIconRules.decode(null))
        assertEquals(emptyList<CustomIconRule>(), CustomIconRules.decode(""))
        assertEquals(emptyList<CustomIconRule>(), CustomIconRules.decode("   "))
    }

    @Test
    fun `separators in a keyword cannot corrupt the record`() {
        val encoded = CustomIconRules.encode(
            listOf(CustomIconRule("stand\tup\nnow", TileIcons.GROUPS))
        )
        // Folded to spaces, so it stays exactly one rule on one line.
        val decoded = CustomIconRules.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals("stand up now", decoded[0].keyword)
    }

    @Test
    fun `rules pointing at an icon this build does not ship are dropped`() {
        val decoded = CustomIconRules.decode("standup\tgroups\tWORDS\tfalse\nfoo\tno_such_icon\tWORDS\tfalse")
        assertEquals(listOf(CustomIconRule("standup", TileIcons.GROUPS)), decoded)
    }

    @Test
    fun `malformed lines are skipped rather than throwing`() {
        val decoded = CustomIconRules.decode(
            "standup\tgroups\tWORDS\tfalse\ngarbage\n\n\tgroups\tWORDS\tfalse\nfoo\t\tWORDS\tfalse\nold\tgroups"
        )
        assertEquals(listOf(CustomIconRule("standup", TileIcons.GROUPS)), decoded)
    }

    @Test
    fun `an unrecognised mode falls back to words`() {
        val decoded = CustomIconRules.decode("standup\tgroups\tFUTURE_MODE\tfalse")
        assertEquals(listOf(CustomIconRule("standup", TileIcons.GROUPS, MatchMode.WORDS)), decoded)
    }

    @Test
    fun `mode and exact survive the round trip`() {
        val rules = listOf(
            CustomIconRule("standup", TileIcons.GROUPS, MatchMode.WORDS, exact = false),
            CustomIconRule("^oud papier", TileIcons.RECYCLING, MatchMode.REGEX, exact = true)
        )
        assertEquals(rules, CustomIconRules.decode(CustomIconRules.encode(rules)))
    }
}
