package com.athlink.app.data.remote

/**
 * Single place for Firestore collection names, so the paths can be finalised (and matched to
 * security rules) later without hunting through the code.
 */
object FirestorePaths {
    const val USERS = "users"
    const val COACHES = "coaches"
}
