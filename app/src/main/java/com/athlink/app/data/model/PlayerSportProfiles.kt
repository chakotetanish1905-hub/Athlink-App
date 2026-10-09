package com.athlink.app.data.model

/**
 * One optional, single-choice question about how the player plays a specific sport
 * (e.g. cricket "Playing role"). Answers are stored in `players/{uid}.sportProfile[key]`.
 */
data class SportAttribute(
    val key: String,
    val label: String,
    val options: List<String>
)

/**
 * Sport-specific questions, data-driven instead of a class per sport: adding a sport (or a new
 * question) is one entry here, with no model or Firestore migration. Every attribute is optional,
 * and only the attributes of the player's PRIMARY sport are shown.
 *
 * Sport names match [Sports.ALL] (the list coaches already use), so players and coaches share
 * one vocabulary for future matching.
 */
object SportProfiles {

    private val HAND = listOf("Right", "Left")

    private val bySport: Map<String, List<SportAttribute>> = mapOf(
        "Cricket" to listOf(
            SportAttribute("playerRole", "Playing role", listOf("Batsman", "Bowler", "All-Rounder", "Wicket-Keeper")),
            SportAttribute("battingStyle", "Batting style", listOf("Right Hand", "Left Hand")),
            SportAttribute(
                "bowlingStyle", "Bowling style",
                listOf("Right-arm Fast", "Right-arm Medium", "Right-arm Spin", "Left-arm Fast", "Left-arm Medium", "Left-arm Spin", "Don't bowl")
            )
        ),
        "Football" to listOf(
            SportAttribute("position", "Position", listOf("Goalkeeper", "Defender", "Midfielder", "Forward")),
            SportAttribute("preferredFoot", "Preferred foot", listOf("Right", "Left", "Both"))
        ),
        "Badminton" to listOf(
            SportAttribute("category", "Category", listOf("Singles", "Doubles", "Mixed Doubles")),
            SportAttribute("playingHand", "Playing hand", HAND)
        ),
        "Basketball" to listOf(
            SportAttribute("position", "Position", listOf("Point Guard", "Shooting Guard", "Small Forward", "Power Forward", "Center"))
        ),
        "Tennis" to listOf(
            SportAttribute("playingHand", "Playing hand", HAND),
            SportAttribute("preferredStyle", "Playing style", listOf("Baseliner", "Serve & Volley", "All-Court"))
        ),
        "Table Tennis" to listOf(
            SportAttribute("playingHand", "Playing hand", HAND),
            SportAttribute("grip", "Grip", listOf("Shakehand", "Penhold"))
        ),
        "Athletics" to listOf(
            SportAttribute(
                "discipline", "Discipline",
                listOf("Sprints", "Middle Distance", "Long Distance", "Hurdles", "Jumps", "Throws", "Race Walking", "Combined Events")
            )
        ),
        "Swimming" to listOf(
            SportAttribute("stroke", "Main stroke", listOf("Freestyle", "Backstroke", "Breaststroke", "Butterfly", "Individual Medley"))
        ),
        "Hockey" to listOf(
            SportAttribute("position", "Position", listOf("Goalkeeper", "Defender", "Midfielder", "Forward"))
        ),
        "Volleyball" to listOf(
            SportAttribute("position", "Position", listOf("Setter", "Outside Hitter", "Opposite", "Middle Blocker", "Libero"))
        ),
        "Kabaddi" to listOf(
            SportAttribute("position", "Position", listOf("Raider", "Defender (Corner)", "Defender (Cover)", "All-Rounder"))
        )
    )

    fun attributesFor(sport: String): List<SportAttribute> = bySport[sport].orEmpty()

    /** Keeps only answers that are valid for [sport] (used when the primary sport changes). */
    fun sanitise(sport: String, answers: Map<String, String>): Map<String, String> {
        val attributes = attributesFor(sport).associateBy { it.key }
        return answers.filter { (key, value) -> attributes[key]?.options?.contains(value) == true }
    }

    /** Human-readable "Playing role: Bowler" lines for profile screens. */
    fun describe(sport: String, answers: Map<String, String>): List<Pair<String, String>> =
        attributesFor(sport).mapNotNull { attr -> answers[attr.key]?.let { attr.label to it } }
}
