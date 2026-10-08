package com.athlink.app.navigation

object NavRoutes {
    // Auth
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"

    // Player
    const val PLAYER_NAV = "player_nav"
    const val PLAYER_HOME = "player_home"
    const val PLAYER_SEARCH = "player_search"
    const val PLAYER_BOOK = "player_book/{coachId}"
    const val PLAYER_CHAT = "player_chat"
    const val PLAYER_CHAT_THREAD = "player_chat_thread/{threadId}/{receiverName}"
    const val PLAYER_PROFILE = "player_profile"

    // Coach
    const val COACH_NAV = "coach_nav"
    const val COACH_HOME = "coach_home"
    const val COACH_SESSIONS = "coach_sessions"
    const val COACH_PROFILE = "coach_profile"

    // Organisation
    const val ORG_NAV = "org_nav"
    const val ORG_HOME = "org_home"
    const val ORG_EVENTS = "org_events"
    const val ORG_CREATE_EVENT = "org_create_event"
    const val ORG_PROFILE = "org_profile"
    /** Start of the org graph: routes by verification status (onboarding / status / dashboard). */
    const val ORG_GATE = "org_gate"
    const val ORG_ONBOARDING = "org_onboarding"
    const val ORG_VERIFICATION_STATUS = "org_verification_status"
    const val ORG_EDIT_DRAFT = "org_edit_draft/{draftId}"

    fun playerBook(coachId: String) = "player_book/$coachId"
    fun chatThread(threadId: String, receiverName: String) = "player_chat_thread/$threadId/$receiverName"
    fun orgEditDraft(draftId: String) = "org_edit_draft/$draftId"
}
