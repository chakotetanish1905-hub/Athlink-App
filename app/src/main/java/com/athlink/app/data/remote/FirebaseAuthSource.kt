package com.athlink.app.data.remote

import com.athlink.app.data.model.CoachRegistration
import com.athlink.app.data.model.User
import com.athlink.app.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    val currentUser get() = auth.currentUser

    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID null")
            val userDoc = firestore.collection(FirestorePaths.USERS).document(uid).get().await()
            val user = userDoc.toObject(User::class.java) ?: throw Exception("User not found")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        role: UserRole
    ): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID null")
            val user = User(
                uid = uid,
                name = name,
                email = email,
                role = role
            )
            firestore.collection(FirestorePaths.USERS).document(uid).set(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Coach registration ("POST" for the coach signup form).
     *
     * 1. Creates the Firebase Auth account (email + password).
     * 2. Writes `users/{uid}`, `coaches/{uid}` (public, DRAFT / NOT_SUBMITTED) and
     *    `coachPrivate/{uid}` (email + phone) in ONE batch, so all exist or none do.
     * 3. If the Firestore write fails, the just-created Auth account is deleted again, so the
     *    user is never left with a login that has no profile and can simply retry.
     *
     * The form must already have passed [CoachRegistration.validate].
     */
    suspend fun signUpCoach(registration: CoachRegistration): Result<User> {
        return try {
            val email = registration.email.trim().lowercase()
            val result = auth.createUserWithEmailAndPassword(email, registration.password).await()
            val firebaseUser = result.user ?: throw Exception("UID null")
            val uid = firebaseUser.uid
            val now = System.currentTimeMillis()
            val user = registration.toUser(uid, now)
            val coach = registration.toCoach(uid, now)
            val privateProfile = registration.toPrivateProfile(uid, now)

            try {
                firestore.batch()
                    .set(firestore.collection(FirestorePaths.USERS).document(uid), user)
                    .set(firestore.collection(FirestorePaths.COACHES).document(uid), coach)
                    .set(firestore.collection(FirestorePaths.COACH_PRIVATE).document(uid), privateProfile)
                    .commit()
                    .await()
            } catch (e: Exception) {
                runCatching { firebaseUser.delete().await() }
                throw e
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Organisation registration.
     *
     * Writes `users/{uid}` (role ORGANISATION, organisationId = uid) and a minimal
     * `organisations/{uid}` (UNVERIFIED, level 0) in ONE batch, deleting the Auth account again if
     * the batch fails. Choosing the Organisation role grants NO trust: the organisation must then
     * complete onboarding and be approved by an admin before it can publish events.
     * A verification email is sent (best effort) so the contact step can be completed.
     */
    suspend fun signUpOrganisation(name: String, email: String, password: String): Result<User> {
        return try {
            val normalisedEmail = email.trim().lowercase()
            val result = auth.createUserWithEmailAndPassword(normalisedEmail, password).await()
            val firebaseUser = result.user ?: throw Exception("UID null")
            val uid = firebaseUser.uid
            val user = User(
                uid = uid,
                name = name.trim(),
                email = normalisedEmail,
                role = UserRole.ORGANISATION,
                organisationId = uid
            )
            try {
                firestore.batch()
                    .set(firestore.collection(FirestorePaths.USERS).document(uid), user)
                    .set(
                        firestore.collection(FirestorePaths.ORGANISATIONS).document(uid),
                        OrganisationDocs.initialOrganisation(uid, name)
                    )
                    .commit()
                    .await()
            } catch (e: Exception) {
                runCatching { firebaseUser.delete().await() }
                throw e
            }
            runCatching { firebaseUser.sendEmailVerification().await() }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() = auth.signOut()

    suspend fun getCurrentUserData(): Result<User> {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val doc = firestore.collection(FirestorePaths.USERS).document(uid).get().await()
            val user = doc.toObject(User::class.java) ?: throw Exception("User data not found")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
