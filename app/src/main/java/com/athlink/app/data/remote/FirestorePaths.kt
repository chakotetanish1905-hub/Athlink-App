package com.athlink.app.data.remote

/**
 * Single place for Firestore collection names and Cloud Storage paths, matched 1:1 by
 * `firestore.rules` / `storage.rules`. See ATHLINK_COACH_FIREBASE_SCHEMA.md for visibility.
 */
object FirestorePaths {
    const val USERS = "users"                 // private account record (owner + admin)

    // ── Player ──────────────────────────────────────────────────────────
    const val PLAYERS = "players"             // players/{uid}: public player profile (signed-in read)

    // ── Coach: public profile + public subcollections ───────────────────
    const val COACHES = "coaches"
    const val QUALIFICATIONS = "qualifications"   // coaches/{uid}/qualifications/{id}
    const val SERVICES = "services"               // coaches/{uid}/services/{id}
    const val AVAILABILITY = "availability"       // coaches/{uid}/availability/{id}
    const val REFERENCES = "references"           // coaches/{uid}/references/{id}  (owner + admin only)

    // ── Coach: private ──────────────────────────────────────────────────
    const val COACH_PRIVATE = "coachPrivate"            // coachPrivate/{uid}
    const val COACH_VERIFICATION = "coachVerification"  // coachVerification/{uid}
    const val ADMIN = "admin"                           // coachVerification/{uid}/admin/review
    const val REVIEW_DOC = "review"

    // ── Organisation (organisationId == owner uid) ──────────────────────
    const val ORGANISATIONS = "organisations"                              // public profile + admin-only status
    const val ORGANISATION_VERIFICATION = "organisationVerification"       // private legal / contact evidence
    const val ORGANISATION_REPRESENTATIVES = "organisationRepresentatives" // private, id = organisationId
    const val ORGANISATION_AFFILIATIONS = "organisationAffiliations"       // private, id = organisationId
    const val ORGANISATION_DOCUMENTS = "organisationDocuments"             // private metadata, auto id
    const val CHUNKS = "chunks"                                            // organisationDocuments/{id}/chunks/{n}: file bytes
    const val VERIFICATION_AUDIT_LOGS = "verificationAuditLogs"            // append-only

    // ── Events ──────────────────────────────────────────────────────────
    const val EVENTS = "events"              // published (verified organisations only)
    const val EVENT_DRAFTS = "eventDrafts"   // private drafts (owner only)

    // ── Bookings ────────────────────────────────────────────────────────
    const val SESSIONS = "sessions"                  // coach bookings, id = {coachId}_{date}_{HHmm}
    const val ACADEMY_REQUESTS = "academyRequests"   // player -> academy session requests
}

/** Cloud Storage paths. Public and private assets are under different roots. */
object StoragePaths {
    /** PUBLIC (any signed-in user can read): coach profile photo. */
    fun coachProfilePhoto(uid: String) = "coaches/$uid/public/profile.jpg"

    /** PRIVATE (owner + admin only): verification documents. */
    fun identityDocument(uid: String, fileName: String) = "coachVerification/$uid/identity/$fileName"
    fun qualificationDocument(uid: String, qualificationId: String, fileName: String) =
        "coachVerification/$uid/qualifications/$qualificationId/$fileName"
    fun safeguardingDocument(uid: String, kind: String, fileName: String) =
        "coachVerification/$uid/safeguarding/$kind/$fileName"

    /** PRIVATE (owner + admin only): organisation verification documents. Never given a public URL. */
    fun organisationDocument(organisationId: String, documentId: String, fileName: String) =
        "organisation_documents/$organisationId/$documentId/$fileName"

    /** PUBLIC (any signed-in user can read): organisation logo. */
    fun organisationLogo(organisationId: String) = "organisation_logos/$organisationId/logo"
}
