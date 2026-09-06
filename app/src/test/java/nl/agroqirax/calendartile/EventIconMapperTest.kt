package nl.agroqirax.calendartile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventIconMapperTest {

    private fun icon(title: String?, location: String? = null) =
        EventIconMapper.iconNameFor(title, location)

    @Test
    fun `matches english titles`() {
        assertEquals("flight", icon("Flight to Berlin"))
        assertEquals("dentistry", icon("Dentist"))
        assertEquals("restaurant", icon("Dinner with Sam"))
        assertEquals("cake", icon("Anna's birthday"))
    }

    @Test
    fun `matches titles in the other supported languages`() {
        assertEquals("dentistry", icon("Tandarts"))            // nl
        assertEquals("medical_services", icon("Arzt"))         // de
        assertEquals("beach_access", icon("Vacances d'été"))   // fr
        assertEquals("cake", icon("Cumpleaños de Ana"))        // es
        assertEquals("restaurant", icon("Pranzo con Marco"))   // it
        assertEquals("sports_soccer", icon("Jogo de futebol")) // pt
    }

    @Test
    fun `folds diacritics so accented and unaccented spellings agree`() {
        assertEquals("local_cafe", icon("Café"))
        assertEquals("local_cafe", icon("Cafe"))
        assertEquals("church", icon("Église"))
    }

    @Test
    fun `expands german sharp s, which has no decomposed form`() {
        assertEquals("sports_soccer", icon("Fußball"))
        assertEquals("sports_soccer", icon("Fussball"))
    }

    @Test
    fun `more specific rules win over more general ones`() {
        // dentistry precedes medical_services
        assertEquals("dentistry", icon("Tandarts controle"))
        // videocam precedes groups
        assertEquals("videocam", icon("Teams meeting"))
        // grocery precedes shopping_cart
        assertEquals("grocery", icon("Supermarket shopping"))
    }

    @Test
    fun `title is matched before location`() {
        // Both would match something; the title has to win.
        assertEquals("restaurant", icon(title = "Lunch", location = "Sportschool"))
    }

    @Test
    fun `location is used only when the title matches nothing`() {
        assertEquals("fitness_center", icon(title = "Afspraak", location = "Sportschool"))
    }

    @Test
    fun `matches whole words, not substrings`() {
        // "bar" is a keyword; "Bartender" must not match it.
        assertNull(icon("Bartender"))
        // "work" is a keyword; "Networking" must not match it.
        assertNull(icon("Networking"))
    }

    @Test
    fun `common short words do not hijack a match`() {
        // "the" once meant tea (fr) and swallowed every English title.
        assertEquals("groups", icon("The big sync"))
        // "les" once meant lesson (nl) and matched the French article.
        assertEquals("beach_access", icon("Les vacances"))
        // "mis" once meant mass (nl) and matched the Spanish possessive.
        assertEquals("beach_access", icon("Mis vacaciones"))
    }

    @Test
    fun `returns null when nothing matches`() {
        assertNull(icon("asdf"))
        assertNull(icon("Project Zephyr"))
    }

    @Test
    fun `handles null and blank input`() {
        assertNull(icon(null, null))
        assertNull(icon("", ""))
        assertNull(icon("   ", "   "))
    }

    @Test
    fun `matches motorsport the way people actually write it`() {
        assertEquals("sports_motorsports", icon("F1 qualifying"))
        assertEquals("sports_motorsports", icon("Formula 1"))
        assertEquals("sports_motorsports", icon("Grand Prix"))
        assertEquals("sports_motorsports", icon("MotoGP"))
        assertEquals("sports_motorsports", icon("Grand Prix Zandvoort"))
    }

    @Test
    fun `the ambiguous halves of a motorsport keyword no longer match alone`() {
        // These used to hit the racing icon because "Grand Prix" could only be
        // expressed as the squashed "grandprix" plus a bare "prix".
        assertEquals("restaurant", icon("Prix fixe dinner"))
        assertNull(icon("Formula for the report"))
    }

    @Test
    fun `built-in rules support multi-word keywords`() {
        assertEquals("recycling", icon("Oud papier en karton"))
        assertEquals("recycling", icon("Glas container buitenzetten"))
        // A keyword only has to be a subset, so word order does not matter.
        assertEquals("recycling", icon("Karton en oud papier"))
    }

    @Test
    fun `recycling needs its qualifying word, not just papier or karton`() {
        // The keyword is "oud papier", so "papier" without "oud" matches nothing —
        // bare "papier", "karton" and "glas" are too ordinary to list on their own.
        assertNull(icon("Papier en karton"))
        assertNull(icon("Papier bestellen"))
        assertNull(icon("Karton"))
        assertNull(icon("Glas"))
    }

    @Test
    fun `single-word recycling keywords still work`() {
        assertEquals("recycling", icon("Afval buitenzetten"))
        assertEquals("recycling", icon("Recycling"))
    }

    @Test
    fun `matches rocket launches`() {
        assertEquals("rocket_launch", icon("Starship launch"))
        assertEquals("rocket_launch", icon("SpaceX"))
        assertEquals("rocket_launch", icon("Raket kijken"))
    }

    @Test
    fun `matches an IATA route pair`() {
        assertEquals("flight", icon("AMS-JFK"))
        assertEquals("flight", icon("AMS/JFK"))
        assertEquals("flight", icon("AMS → JFK"))
        assertEquals("flight", icon("Booking AMS-JFK confirmed"))
    }

    @Test
    fun `a lone IATA-looking code does not match, only a route pair does`() {
        assertNull(icon("AMS"))
        assertNull(icon("Notes about AMS"))
    }

    @Test
    fun `IATA route matching is case-sensitive and does not fold lowercase`() {
        assertNull(icon("ams-jfk"))
    }

    @Test
    fun `words joined by the word to do not look like an IATA route`() {
        // Two three-letter acronyms joined by "to" is a plausible ordinary title,
        // e.g. handing off a role — "to" is deliberately not a route separator.
        assertNull(icon("CEO to COO"))
    }

    @Test
    fun `custom rules beat the built-in table`() {
        val custom = listOf(CustomIconRule("lunch", TileIcons.GROUPS))
        // "lunch" is a built-in restaurant keyword; the custom rule has to win.
        assertEquals("restaurant", EventIconMapper.iconNameFor("Lunch", null))
        assertEquals("groups", EventIconMapper.iconNameFor("Lunch", null, custom))
    }

    @Test
    fun `a multi-word custom keyword needs every word present`() {
        val custom = listOf(CustomIconRule("team lunch", TileIcons.GROUPS))
        assertEquals("groups", EventIconMapper.iconNameFor("Team lunch Friday", null, custom))
        // Only half the keyword — falls through to the built-in table.
        assertEquals("restaurant", EventIconMapper.iconNameFor("Lunch Friday", null, custom))
    }

    @Test
    fun `custom rules are matched per field, so the title still wins`() {
        val custom = listOf(CustomIconRule("gym", TileIcons.FITNESS_CENTER))
        // The custom keyword is in the location, but the title matches a built-in.
        // Title priority has to outrank custom priority.
        assertEquals(
            "restaurant",
            EventIconMapper.iconNameFor(title = "Lunch", location = "Gym", customRules = custom)
        )
    }

    @Test
    fun `custom rules are normalised like everything else`() {
        val custom = listOf(CustomIconRule("Café", TileIcons.LOCAL_BAR))
        assertEquals("local_bar", EventIconMapper.iconNameFor("cafe", null, custom))
    }

    @Test
    fun `a words keyword full of regex metacharacters is taken literally`() {
        // The whole point of WORDS being the default: this would be a broken pattern.
        val custom = listOf(CustomIconRule("*important* review", TileIcons.WORK))
        assertEquals(
            "work",
            EventIconMapper.iconNameFor("*important* review tomorrow", null, custom)
        )
    }

    @Test
    fun `regex rules match against the raw text`() {
        val custom = listOf(
            CustomIconRule("^oud papier", TileIcons.RECYCLING, MatchMode.REGEX)
        )
        assertEquals("recycling", EventIconMapper.iconNameFor("Oud papier en karton", null, custom))
        // Anchored, so the same words elsewhere in the title do not match; it falls
        // through to the built-in table, which does match them.
        assertEquals("recycling", EventIconMapper.iconNameFor("Ophalen oud papier", null, custom))
    }

    @Test
    fun `a broken regex rule is inert rather than fatal`() {
        val custom = listOf(CustomIconRule("*broken", TileIcons.WORK, MatchMode.REGEX))
        // Falls straight through to the built-in table.
        assertEquals("restaurant", EventIconMapper.iconNameFor("Lunch", null, custom))
        assertNull(EventIconMapper.iconNameFor("*broken", null, custom))
    }

    @Test
    fun `both modes see the location, not just the title`() {
        // The help text claims title-or-location for both modes; pin that so the
        // wording and the behaviour cannot drift apart again.
        val words = listOf(CustomIconRule("zandvoort", TileIcons.SPORTS_MOTORSPORTS))
        val regex = listOf(
            CustomIconRule("zandvoort$", TileIcons.SPORTS_MOTORSPORTS, MatchMode.REGEX)
        )
        for (rules in listOf(words, regex)) {
            assertEquals(
                "sports_motorsports",
                EventIconMapper.iconNameFor(title = "Uitje", location = "Circuit Zandvoort", customRules = rules)
            )
        }
    }

    @Test
    fun `regex anchors apply per field, not to the two joined`() {
        // Each field is matched separately, so ^ means "start of the title" or
        // "start of the location" — never the start of some combined string.
        val rules = listOf(CustomIconRule("^circuit", TileIcons.SPORTS_MOTORSPORTS, MatchMode.REGEX))
        assertEquals(
            "sports_motorsports",
            EventIconMapper.iconNameFor(title = "Uitje", location = "Circuit Zandvoort", customRules = rules)
        )
        assertNull(
            EventIconMapper.iconNameFor(title = "Uitje", location = "Naar circuit", customRules = rules)
        )
    }

    @Test
    fun `exact means the same thing in words mode as in regex mode`() {
        val words = CustomIconRule("Café", TileIcons.LOCAL_CAFE, MatchMode.WORDS, exact = true)
        val regex = CustomIconRule("Café", TileIcons.LOCAL_CAFE, MatchMode.REGEX, exact = true)

        // Both respect case and accents...
        assertTrue(EventIconMapper.matches(words, "Café met Anna"))
        assertTrue(EventIconMapper.matches(regex, "Café met Anna"))
        assertFalse(EventIconMapper.matches(words, "cafe met Anna"))
        assertFalse(EventIconMapper.matches(regex, "cafe met Anna"))
        assertFalse(EventIconMapper.matches(words, "CAFÉ met Anna"))
        assertFalse(EventIconMapper.matches(regex, "CAFÉ met Anna"))
    }

    @Test
    fun `not-exact folds case and accents in both modes`() {
        val words = CustomIconRule("cafe", TileIcons.LOCAL_CAFE, MatchMode.WORDS, exact = false)
        val regex = CustomIconRule("cafe", TileIcons.LOCAL_CAFE, MatchMode.REGEX, exact = false)

        for (rule in listOf(words, regex)) {
            assertTrue(EventIconMapper.matches(rule, "Café met Anna"))
            assertTrue(EventIconMapper.matches(rule, "CAFE"))
            assertTrue(EventIconMapper.matches(rule, "cafe"))
        }
    }

    @Test
    fun `exact still tokenises, so word alignment survives`() {
        // Turning off folding must not turn off word alignment — that is structural,
        // not a normalisation.
        val rule = CustomIconRule("bar", TileIcons.LOCAL_BAR, MatchMode.WORDS, exact = true)
        assertTrue(EventIconMapper.matches(rule, "The bar"))
        assertFalse(EventIconMapper.matches(rule, "Bartender"))
    }

    @Test
    fun `an exact regex can distinguish what a folded one cannot`() {
        // The point of the toggle: this is unexpressible with folding on.
        val rule = CustomIconRule("résumé", TileIcons.MENU_BOOK, MatchMode.REGEX, exact = true)
        assertTrue(EventIconMapper.matches(rule, "Update résumé"))
        assertFalse(EventIconMapper.matches(rule, "Update resume"))
    }

    @Test
    fun `rules default to not exact`() {
        assertFalse(CustomIconRule("x", TileIcons.WORK).exact)
        assertTrue(EventIconMapper.matches(CustomIconRule("cafe", TileIcons.LOCAL_CAFE), "CAFÉ"))
    }

    @Test
    fun `the editor test helper runs the same matcher as the tile`() {
        val words = CustomIconRule("oud papier", TileIcons.RECYCLING)
        assertTrue(EventIconMapper.matches(words, "Oud papier en karton"))
        assertFalse(EventIconMapper.matches(words, "Papier bestellen"))

        val regex = CustomIconRule("^f1\\b", TileIcons.SPORTS_MOTORSPORTS, MatchMode.REGEX)
        assertTrue(EventIconMapper.matches(regex, "F1 qualifying"))
        assertFalse(EventIconMapper.matches(regex, "Watch the F1"))
    }

    @Test
    fun `every icon the mapper can return has a drawable`() {
        // Catches a rule naming an icon the fetch script never downloaded, which
        // would otherwise show up only as a silent fallback to the date icon.
        val missing = EventIconMapper.iconNames.filter { TileIcons.resIdFor(it) == null }
        assertEquals(emptyList<String>(), missing)
    }
}
