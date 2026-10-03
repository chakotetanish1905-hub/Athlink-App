package com.athlink.app.data.remote

/**
 * Single place for Firestore collection names and Cloud Storage paths, matched 1:1 by
 * `firestore.rules` / `storage.rules`. See ATHLINK_COACH_FIREBASE_SCHEMA.md for visibility.
 */
object FirestorePaths {
    const val USERS = "users"

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
}
