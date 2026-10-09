package com.athlink.app.data.model

/** Fields of the player account-creation form (the first, minimal step). */
enum class PlayerSignupField { NAME, EMAIL, PASSWORD, CONFIRM_PASSWORD, TERMS, PRIVACY }

/**
 * Player account creation: ONLY what Firebase Auth needs plus consent. Everything else is
 * collected afterwards in player onboarding ([PlayerProfileForm]), so signup stays short.
 * The password is passed to Firebase Auth and never stored in Firestore.
 */
data class PlayerSignupForm(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val termsAccepted: Boolean = false,
    val privacyAccepted: Boolean = false
) {
    fun validate(): Map<PlayerSignupField, String> = buildMap<PlayerSignupField, String> {
        PlayerValidators.fullName(name)?.let { put(PlayerSignupField.NAME, it) }
        PlayerValidators.email(email)?.let { put(PlayerSignupField.EMAIL, it) }
        PlayerValidators.password(password)?.let { put(PlayerSignupField.PASSWORD, it) }
        PlayerValidators.confirmPassword(password, confirmPassword)?.let { put(PlayerSignupField.CONFIRM_PASSWORD, it) }
        PlayerValidators.consent(termsAccepted, "Terms of Service")?.let { put(PlayerSignupField.TERMS, it) }
        PlayerValidators.consent(privacyAccepted, "Privacy Policy")?.let { put(PlayerSignupField.PRIVACY, it) }
    }

    val normalisedEmail: String get() = email.trim().lowercase()
    val normalisedName: String get() = name.trim().replace(Regex("\\s+"), " ")

    companion object {
        /** Bump when the Terms / Privacy text changes, so re-consent can be detected later. */
        const val TERMS_VERSION = "2026-10"
    }
}
