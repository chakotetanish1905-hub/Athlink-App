package com.athlink.app.data.model

/**
 * Coarse player location: country, state/UT, city and a derived region. Never an address,
 * pin code or GPS position (those are only needed once the map feature exists).
 *
 * Region = the zonal council zone of the state, so it is derived automatically and is always
 * consistent (the player never types it). Outside India the region is "International".
 */
object PlayerLocation {

    const val DEFAULT_COUNTRY = "India"
    const val INTERNATIONAL = "International"

    private const val NORTH = "North India"
    private const val CENTRAL = "Central India"
    private const val EAST = "East India"
    private const val WEST = "West India"
    private const val SOUTH = "South India"
    private const val NORTH_EAST = "North-East India"

    /** States and union territories of India with their zone (Zonal Councils grouping). */
    private val ZONES: Map<String, String> = linkedMapOf(
        "Andaman and Nicobar Islands" to SOUTH,
        "Andhra Pradesh" to SOUTH,
        "Arunachal Pradesh" to NORTH_EAST,
        "Assam" to NORTH_EAST,
        "Bihar" to EAST,
        "Chandigarh" to NORTH,
        "Chhattisgarh" to CENTRAL,
        "Dadra and Nagar Haveli and Daman and Diu" to WEST,
        "Delhi" to NORTH,
        "Goa" to WEST,
        "Gujarat" to WEST,
        "Haryana" to NORTH,
        "Himachal Pradesh" to NORTH,
        "Jammu and Kashmir" to NORTH,
        "Jharkhand" to EAST,
        "Karnataka" to SOUTH,
        "Kerala" to SOUTH,
        "Ladakh" to NORTH,
        "Lakshadweep" to SOUTH,
        "Madhya Pradesh" to CENTRAL,
        "Maharashtra" to WEST,
        "Manipur" to NORTH_EAST,
        "Meghalaya" to NORTH_EAST,
        "Mizoram" to NORTH_EAST,
        "Nagaland" to NORTH_EAST,
        "Odisha" to EAST,
        "Puducherry" to SOUTH,
        "Punjab" to NORTH,
        "Rajasthan" to NORTH,
        "Sikkim" to NORTH_EAST,
        "Tamil Nadu" to SOUTH,
        "Telangana" to SOUTH,
        "Tripura" to NORTH_EAST,
        "Uttar Pradesh" to CENTRAL,
        "Uttarakhand" to CENTRAL,
        "West Bengal" to EAST
    )

    val INDIAN_STATES: List<String> = ZONES.keys.toList()

    fun isIndia(country: String): Boolean = country.trim().equals(DEFAULT_COUNTRY, ignoreCase = true)

    /** Region for a country/state pair, or "" when it can't be derived yet (state not chosen). */
    fun regionFor(country: String, state: String): String = when {
        country.isBlank() -> ""
        !isIndia(country) -> INTERNATIONAL
        else -> ZONES[state.trim()].orEmpty()
    }
}
