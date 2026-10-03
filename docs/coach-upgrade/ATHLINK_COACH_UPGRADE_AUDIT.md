# Athlink: Coach Upgrade Audit (Phase 1)

**Date:** 2026-10-03 · **Scope:** read-only audit. No code was changed.
**Baseline audited:** branch **`feature/coach-registration`** @ `1d6af55` ("Add coach registration: form model, validation and Firestore create") on GitHub `chakotetanish1905-hub/Athlink-App`. It matches the laptop's `C:\Athlink` working copy (sizes differ only by Windows line endings). `main` (`123f2f6`) is the older initial commit.

---

## 0. What was audited

| Source | Status |
|---|---|
| Root/Gradle config, `AndroidManifest.xml`, `CLAUDE.md`, `.gitignore` | ✅ |
| All 44 Kotlin source files on `feature/coach-registration` | ✅ |
| `firestore.rules` / `storage.rules` / `firebase.json` | Not in repo (rules documented only in `CLAUDE.md`) |
| Tests | None exist |

The branch changes 8 files relative to `main`: `Coach.kt`, **new** `CoachRegistration.kt`, **new** `FirestorePaths.kt`, `FirebaseAuthSource.kt`, `FirestoreSource.kt`, `AuthRepository.kt`, `AuthViewModel.kt`, `SignupScreen.kt`.

---

## 1. Existing architecture

- **Stack:** Kotlin 2.2.10, AGP 9.0.1, compileSdk/targetSdk 35, minSdk 26, Compose BOM 2024.08, Material 3, Navigation-Compose 2.7.7, Hilt 2.59 (KSP), Firebase BoM 33.2.0 (Auth, Firestore, Storage KTX), Coil 2.7, coroutines 1.8.1. Single `:app` module.
- **Layering:** `ui/screens/<role>` → `viewmodel/*` (`@HiltViewModel`, single `StateFlow<XState>`) → `data/repository/*` → `data/remote/{FirebaseAuthSource, FirestoreSource}`.
- **DI:** `utils/AppModule.kt` provides the `FirebaseAuth`, `FirebaseFirestore` and `FirebaseStorage` singletons.
- **Dummy-data fallback:** repositories return `DummyData.*` whenever Firestore errors **or returns empty**.
- **Tests:** none (no `src/test` or `src/androidTest`).
- **Admin module / admin role:** **does not exist.** `UserRole` = `PLAYER, COACH, ORGANISATION`.

## 2. Existing coach flow

1. `SignupScreen` shows role chips (Player / Coach / Organisation). Choosing **Coach** expands an inline `CoachProfileSection` on the same screen (account, phone, city/state, sport, comma-separated specializations, experience, coaching levels, comma-separated certifications, hourly rate, bio).
2. Coach submit → `AuthViewModel.registerCoach(CoachRegistration)`, which runs `CoachRegistration.validate()` and maps errors to `coachFieldErrors: Map<CoachField,String>`.
3. → `AuthRepository.registerCoach` → `FirebaseAuthSource.signUpCoach`:
   - creates the Auth user (email lower-cased);
   - writes `users/{uid}` (`toUser`) **and** `coaches/{uid}` (`toCoach`) in **one batch**;
   - if the batch fails, **deletes the Auth user** so the user can retry. This is good and should be kept.
4. Auth errors are mapped to field errors (collision, weak password, invalid email, network).
5. `state.isLoggedIn` → `onSignupSuccess()` → root `NavHost` routes on `user.role` → `COACH_NAV` → `COACH_HOME`. **No onboarding step exists yet.**
6. Players and organisations still use `register()` → `signUp()`, which writes only `users/{uid}`.

**Where the coach object is created:** `CoachRegistration.toCoach(uid, now)`, called only from `FirebaseAuthSource.signUpCoach`.

## 3. Existing coach model

```kotlin
data class Coach(
  uid, name, email, phone, sport, bio, experience: Int, rating: Float, reviewCount: Int,
  hourlyRate: Double, profileImageUrl, city, state, location /* "City, State" */,
  specializations: List<String>, certifications: List<String> /* free text */,
  coachingLevels: List<String> /* CoachingLevel names */,
  verificationStatus: String = "PENDING", totalEarnings: Double, isAvailable: Boolean,
  createdAt: Long, updatedAt: Long /* client millis */)

enum class VerificationStatus { PENDING, VERIFIED, REJECTED }   // in CoachRegistration.kt
enum class CoachingLevel { BEGINNER, INTERMEDIATE, ADVANCED, PROFESSIONAL }
object Sports { ALL = 15 sports }
```
Notes:
- `verificationStatus` is stored as a **String**, so unknown or legacy values won't crash deserialisation. That makes it easy to extend.
- `isAvailable`: Firestore's Java mapper serialises Kotlin `is*` booleans as field **`available`**. Don't rename it blindly.
- Timestamps are **client** `Long` millis, which rules can't trust.
- `certifications` are free-text strings with no issuer, number, dates or document.

## 4. Existing Firestore structure

| Collection | Written by | Read by |
|---|---|---|
| `users/{uid}` | `signUp` / `signUpCoach` | login / splash (`getCurrentUserData`) |
| `coaches/{uid}` | `signUpCoach` (batch with `users`) | `getCoaches()` (**unfiltered `.get()` on the whole collection**), `getCoachById` |

Fields in `coaches/{uid}` today: `uid, name, email, phone, sport, bio, experience, rating, reviewCount, hourlyRate, profileImageUrl, city, state, location, specializations, certifications, coachingLevels, verificationStatus("PENDING"), totalEarnings, available, createdAt, updatedAt`.
Collection names are centralised in `FirestorePaths` (`USERS`, `COACHES`). The rest of `FirestoreSource` still hard-codes `"sessions"`, `"events"` and `"chats"`.
| `sessions/{auto}` | `bookSession` (player) | `whereEqualTo(playerId/coachId)` |
| `events/{auto}` | org `createEvent` | everyone |
| `chats/{player_coach}/messages/{auto}` | `sendMessage` | snapshot listener |

## 5. Existing Storage structure

**None.** `FirebaseStorage` is provided by Hilt but never injected or used. `profileImageUrl` is never written. The manifest already declares `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`.

## 6. Existing navigation

`NavRoutes`: `splash, login, signup`; player graph (`home, search, book/{coachId}, chat, chat_thread/{threadId}/{receiverName}, profile`); coach graph (`coach_home, coach_sessions, coach_profile`); org graph (`org_home, org_events, org_create_event, org_profile`). Each role has a nested `NavHost` with its own `rememberNavController()`.
There is **no player-facing coach profile screen**. Tapping a coach goes straight to `BookSessionScreen`.

## 7. Existing security rules

There is no `firestore.rules` or `storage.rules` in the repo. `CLAUDE.md` documents the rules in use:

```
match /{document=**} { allow read, write: if request.auth != null; }
```

**Critical:** any signed-in user can read or overwrite **any** document. That includes other users' `role`, any coach's rating/earnings, any session's status, and any chat.

## 8. Defects found that affect this upgrade

| # | Where | Problem |
|---|---|---|
| D1 | `CoachProfileScreen` | Shows `DummyData.coaches.first()` (Rajiv Sharma's bio, rate and rating) for **every** coach. The edit button is a no-op. |
| D2 | `CoachRepository.getCoachById` | Fallback `DummyData.coaches.first { it.uid == coachId }` **throws `NoSuchElementException`** for any real coach whose doc is missing → crash in `BookSessionScreen`. |
| D3 | All repositories | Firestore **permission-denied** is masked by dummy data. Once real rules land, failures will look like "success with fake coaches". |
| D4 | `FirestoreSource.getCoaches` | Unfiltered collection read. It can't be restricted by rules without adding a `where profileStatus == ACTIVE` query. |
| D5 | `Coach.totalEarnings`, `rating`, `reviewCount` | Stored on the coach doc, so the coach could edit them. The dashboard actually computes earnings from completed sessions, so `totalEarnings` is unused. |
| D6 | Chat | `threadId = "${playerUid}_${coachUid}"`, no thread document, `PlayerChatListScreen` lists dummy coaches. Rules can only authorise on the ID string. |
| D7 | Brand colours | The brief specifies primary `#2563EB` / accent `#F97316` / dark `#0B1220`. **The code uses orange `#FF6B35` → `#FF3B86` with deep blue `#1A1F4E`** (`Color.kt`, `CLAUDE.md`). Needs a decision (§12). |
| D8 | Tests | None, so the regression checks in the brief need a test harness first. |
| **D9** | `coaches/{uid}` | **`email` and `phone` are in the public coach doc.** Firestore rules can't hide individual fields, so any reader of the doc gets them. They must move to a private doc, and existing docs need them removed. |
| **D10** | `VerificationStatus` enum | Already exists as `PENDING / VERIFIED / REJECTED`. The brief needs `NOT_SUBMITTED / UNDER_REVIEW / ACTION_REQUIRED / VERIFIED / REJECTED / SUSPENDED / EXPIRED`. **Extend this enum; don't add a second one.** Legacy `"PENDING"` maps to `NOT_SUBMITTED`, because nothing was ever actually submitted for review. |
| **D11** | Signup + onboarding overlap | Signup already collects sport, specializations, experience, levels, certifications, rate, bio, city and state. Onboarding steps 2–3 must **pre-fill** from the coach doc, not ask again. |
| D12 | `signUpCoach` client writes | The client sets `verificationStatus = "PENDING"` on create. The new rules must accept the create only with `NOT_SUBMITTED` (plus `PENDING` during the transition) and `rating = 0`, `reviewCount = 0`, `totalEarnings = 0`. |

## 9. Files that must be modified

| File | Change |
|---|---|
| `data/model/Coach.kt` | Add public profile, status and verification fields (all defaulted; old fields kept) |
| `data/model/CoachRegistration.kt` | Extend `VerificationStatus` (D10); `toCoach` stops writing email/phone and starts writing `profileStatus=DRAFT`, `verificationStatus=NOT_SUBMITTED`; add a `toPrivateProfile` for email/phone |
| `data/remote/FirestorePaths.kt` | Add subcollection, `coachPrivate` and `coachVerification` paths |
| `data/remote/FirestoreSource.kt` | Coach CRUD, subcollections, filtered discovery query, server timestamps |
| `data/remote/FirebaseAuthSource.kt` | `signUpCoach` batch also writes the private contact doc (3 docs, still atomic) |
| `data/repository/CoachRepository.kt` | New methods (brief's list), fix D2, don't mask permission errors (D3) |
| `viewmodel/AuthViewModel.kt` | No logic change. The coach dashboard routes incomplete profiles to onboarding |
| `ui/screens/auth/SignupScreen.kt` | Minimal change: keep the quick coach form as "Step 1"; the rest moves to onboarding |
| `navigation/NavRoutes.kt`, `AthlinkNavHost.kt` | Add `coach_onboarding`, `player_coach_profile/{coachId}` |
| `ui/screens/coach/CoachDashboardScreen.kt` | Completion/verification card (additive) |
| `ui/screens/coach/CoachProfileScreen.kt` | Replace dummy data with the real profile (D1) |
| `ui/screens/player/SearchCoachScreen.kt`, `ui/components/CoachCard.kt` | Badge; tap → profile instead of straight to booking |
| `ui/screens/player/BookSessionScreen.kt` | Pick a service (price/duration) rather than `hourlyRate` |
| `AndroidManifest.xml` | Photo-picker/document handling only if needed (no location permission in v1) |
| **New** `firestore.rules`, `storage.rules`, `firebase.json`, `firestore.indexes.json` | Production rules (§11) |

## 10. Files that should not be modified

`MainActivity.kt`, `AthlinkApp.kt`, `AppModule.kt`, `Theme.kt`/`Type.kt` (unless the D7 decision says so), `LoginScreen.kt`, `SplashScreen.kt`, everything under `ui/screens/organisation/`, `PlayerDashboard.kt`, `PlayerProfileScreen.kt`, `ChatScreen.kt`, `ChatViewModel.kt`, `EventViewModel.kt`, `SessionCard.kt`, `Session.kt`, `EventMessage.kt`, Gradle files (no new libraries are needed: the photo picker is in `activity-compose`, the date and time pickers are in Material 3, and geohash is about 30 lines of code rather than a dependency).

## 11. Migration risks and backward-compatibility strategy

**Risks**
1. **Rules rollout breaks working flows.** Locking down `/{document=**}` touches sessions, chats and events too. Every existing query must satisfy the new rules: list queries must carry the same constraint the rule checks.
2. **Discovery empties out.** If search filters `profileStatus == ACTIVE`, every existing coach (no such field) disappears and the repository falls back to dummy data. That is acceptable for the demo but must be intended.
3. **Role escalation.** Today anyone can rewrite their own `users/{uid}.role`. The new rules make `role` immutable after create.
4. **Storage on the Spark plan.** Firebase has been moving default Storage buckets to require the Blaze (pay-as-you-go) plan. **Check the project's plan in the console before relying on uploads.**
5. **Field renames.** None are planned (see the mapping below), so no data migration script is needed.

**Compatibility mapping (old field kept; new fields are added alongside)**

| Brief field | Existing field | Decision |
|---|---|---|
| `displayName` | `name` | Keep `name` as the display name; add `fullLegalName` only in the private `coachVerification` doc |
| `sports[]` / primary sport | `sport` | Keep `sport` = primary; add `secondarySports[]` |
| `experienceYears` | `experience` | Keep `experience` (Int) |
| `ratePerSession` | `hourlyRate` | Keep `hourlyRate` as a **denormalised "from" price** = lowest active service price, so `CoachCard` and booking keep working |
| `location` | `location` | Keep as the display string "Area, City"; add `city`, `state`, `country`, `coachingArea`, `lat`, `lng`, `geohash` |
| `photoUrl` | `profileImageUrl` | Keep |
| `rating`, `reviewCount`, `totalEarnings` | same | Kept, but **coach-write-protected** in rules |
| `skillLevels` | `coachingLevels` | Keep `coachingLevels` (same meaning; already `CoachingLevel` enum names) |
| `ageGroups` | none | Add |
| qualifications subcollection | `certifications: List<String>` | Keep the list read-only as legacy. Onboarding step 4 offers each entry as a pre-filled **unverified draft** qualification. It is never shown as verified. |
| `verificationStatus` | `"PENDING"` | Read `PENDING` as `NOT_SUBMITTED`; write only new values |
| `email`, `phone` in `coaches/{uid}` | public | **Move** to `coachPrivate/{uid}`. New signups stop writing them. Existing docs: the coach's next profile save deletes them (`FieldValue.delete()`), plus an optional one-off admin script for coaches who never log in again |
| `createdAt`, `updatedAt` (Long) | client millis | Keep the type for compatibility. **New** verification timestamps (`submittedAt`, `verifiedAt`, `rejectedAt`, `expiresAt`) are Firestore `Timestamp`s written with `serverTimestamp()` and checked against `request.time` in rules |

**Defaults for old docs** (applied in the model, with nothing written to them): `verificationStatus = NOT_SUBMITTED` (including legacy `PENDING`), `profileStatus = DRAFT`, `verificationLevel = LEVEL_0_REGISTERED`, sub-statuses `NOT_SUBMITTED`. Nobody is auto-verified.

## 12. Decisions (answered 2026-10-03)

1. **Brand colours:** **keep the existing orange system** (`AthlinkOrange #FF6B35` → `#FF3B86`, deep-blue surfaces). The brief's blue palette is not applied.
2. **Admin approval:** Firebase Auth **custom claim `admin: true`**, set with a small Admin-SDK Node script (`tools/admin/`). Rules check `request.auth.token.admin == true`. Approvals are done from that script, with no in-app admin UI. Custom claims work on the Spark plan.
3. **Discovery:** players see **only `profileStatus == ACTIVE` coaches**. A `BuildConfig.SHOW_UNVERIFIED_COACHES` flag (debug builds only, default `false`) shows the rest for demos, clearly labelled "Not verified".
4. **Firebase plan: Spark.** ⚠ Firebase's FAQ says Cloud Storage now requires the Blaze plan, and Spark projects get 402/403 errors on Storage calls. Blaze keeps a no-cost tier. **Document and photo uploads will not work until the project is on Blaze.** All other parts of the upgrade work on Spark. The upload code is written anyway and fails with a clear "Uploads unavailable" message instead of crashing.
5. **Government ID:** no ID number is stored. Only the ID type, the last 4 digits and the document (private path). Aadhaar must be the *masked* version.
6. **Coach minimum age:** 18.
7. **Qualification requirement:** at least 1 qualification with a document is needed to submit (constant `CoachProfileRules.REQUIRE_QUALIFICATION`, easy to relax for grassroots coaches).
8. **Safeguarding requirement:** child-protection and police verification are required **only if** the coach selects an under-18 age group. First aid is optional, but shown in completeness.

---

## 13. Implementation plan (Phases 2–10, after the decisions above)

| Phase | Deliverables | Done when |
|---|---|---|
| **2. Data model** | `Coach` + enums (`ProfileStatus`, `VerificationStatus`, `CheckStatus`, `VerificationLevel`), `CoachQualification`, `CoachService`, `CoachAvailability`, `CoachReference`, `CoachVerification` (private), `CoachProfileCompletion` (pure Kotlin calculator), validators | Models compile; JVM unit tests for completion % and validation pass |
| **3. Firebase** | `firestore.rules`, `storage.rules`, `firebase.json`, indexes; Storage paths `coaches/{uid}/public/profile.jpg`, `coachVerification/{uid}/{identity,qualifications,safeguarding}/…` | Rules unit tests (`@firebase/rules-unit-testing`) cover the brief's 16 rule cases, including self-verify attempts |
| **4. Repository + ViewModel** | Extend `FirestoreSource` + `CoachRepository` (the brief's method list), one new `CoachOnboardingViewModel` with draft autosave per step, upload with type/size checks, and `submitForVerification()` (sets only `SUBMITTED` / `UNDER_REVIEW` + `submittedAt`) | ViewModel unit tests with fake repository |
| **5. Onboarding UI** | 9-step flow (`ui/screens/coach/onboarding/`), reusable components (`CoachTextField`, `CoachMultiSelect`, `DocumentUploadCard`, `QualificationCard`, `ServiceCard`, `AvailabilityEditor`, `ProfileCompletionCard`, `VerificationStatusCard`) | Builds; manual walkthrough save → exit → resume |
| **6. Coach profile/dashboard** | Real data in `CoachProfileScreen` (fixes D1); dashboard card switching between Complete / Under review / Verified states | Earnings, sessions and logout unchanged |
| **7. Player view** | New `PlayerCoachProfileScreen` (public fields, verified qualifications only, services, availability, tiered badges with "What does this mean?"); search filtered to `ACTIVE`; booking picks a service | Player cannot see private fields (rules test) |
| **8. Security tests** | Run the rules suite; manual role-escalation attempts | All deny cases denied |
| **9. Build** | `./gradlew assembleDebug test lint` **on your laptop**. This workspace can't reach Google's Maven/SDK hosts | Green build, logs shared back |
| **10. Docs** | `ATHLINK_COACH_UPGRADE_IMPLEMENTATION.md`, `ATHLINK_COACH_FIREBASE_SCHEMA.md` (every field tagged PUBLIC / PRIVATE / ADMIN_ONLY), `ATHLINK_COACH_UPGRADE_FINAL_AUDIT.md` | Claims match the code |

**State machine (rules-enforced):** coach may move `NOT_SUBMITTED → UNDER_REVIEW` and `ACTION_REQUIRED → UNDER_REVIEW`, and `profileStatus` `DRAFT → SUBMITTED`. Every transition to `VERIFIED`, `REJECTED`, `SUSPENDED` or `EXPIRED`, every sub-status `VERIFIED`, any `verificationLevel` above 0, and `profileStatus = ACTIVE` require admin. While `UNDER_REVIEW`, the coach cannot edit qualification documents or the private verification doc.

**Working method:** each phase is committed to a new branch **`feature/coach-verified-profile`** cut from `feature/coach-registration` and pushed to GitHub. You `git pull` it in `C:\Athlink`, build in Android Studio, and paste back any errors. Nothing is merged to `main` without your go-ahead.
