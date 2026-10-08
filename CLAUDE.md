# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Athlink is a native Android app (Kotlin, Jetpack Compose, MVVM, Hilt DI, Firebase) connecting players, coaches, and sports organisations. Single Gradle module (`app`), package `com.athlink.app`.

## Commands

Build and run from Android Studio (Hedgehog+), or via Gradle wrapper from the project root:

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

```bash
./gradlew lint
```

```bash
./gradlew test
```

JVM unit tests live in `app/src/test` (JUnit 4) and cover the pure-Kotlin coach model, validation, verification policy and profile-completion logic. There are no instrumented (`androidTest`) tests yet.

## Architecture

**Layering:** `ui/screens` (Compose screens, per role) → `viewmodel` (`@HiltViewModel`, one per feature area, expose a single `StateFlow<XState>`) → `data/repository` → `data/remote` (`FirebaseAuthSource`, `FirestoreSource`). Dependencies are provided via `utils/AppModule.kt` (Hilt `@Module`), which supplies the raw `FirebaseAuth` / `FirebaseFirestore` / `FirebaseStorage` singletons consumed by the remote sources.

**Firebase-with-dummy-data fallback:** Repositories (e.g. `CoachRepository`) call the Firestore source first and fall back to canned data in `data/model/DummyData.kt` when Firestore returns an error or an empty result. This lets the UI be developed/demoed without live Firebase data seeded — keep this fallback pattern when adding new repository methods that read collections.

**Navigation:** `navigation/NavRoutes.kt` defines route string constants (and helper functions for parameterized routes like `playerBook(coachId)`). `navigation/AthlinkNavHost.kt` wires an outer `NavHost` (splash → login/signup → one of three role graphs) and a separate nested `NavHost` per role (`PLAYER_NAV`, `COACH_NAV`, `ORG_NAV`), each with its own `rememberNavController()`. The root `AuthViewModel` (obtained once via `hiltViewModel()` in `AthlinkNavHost`) is passed down into auth screens and its `state.user` is threaded into every role screen; post-login destination is chosen by switching on `user.role` (`UserRole.COACH` / `UserRole.ORGANISATION` / else player).

**Three user roles drive almost everything:** screens, nav graphs, and bottom nav bars are split by role (`player`, `coach`, `organisation`) under `ui/screens/<role>/`. When adding a feature, check whether it needs a screen/state per role or is shared (shared screens live directly under `ui/screens` or are reused across role graphs, e.g. `PlayerProfileScreen` is reused for the organisation profile route).

**Real-time chat:** `FirestoreSource.getMessages(threadId)` is the one place using a Firestore snapshot listener wrapped in `callbackFlow` (vs. the one-shot `.await()` suspend calls used everywhere else); follow this pattern for any other collection that needs live updates.

**Theming:** brand colors/gradients live in `ui/theme/Color.kt`, typography in `Type.kt`, and both are composed into the Material 3 theme in `Theme.kt` (light/dark). Primary brand color is Athlink Orange `#FF6B35` gradating to `#FF3B86`.

## Extending the app

- New screen: add the composable under `ui/screens/<role>/`, add a route constant (and helper if parameterized) in `NavRoutes.kt`, wire a `composable(...)` entry into the relevant nav graph in `AthlinkNavHost.kt`, and add/extend a `@HiltViewModel` in `viewmodel/` if it needs state.
- New Firestore collection: add the model in `data/model/`, add CRUD methods to `FirestoreSource.kt`, wrap them in a repository under `data/repository/` (consider the dummy-data fallback pattern above), and inject the repository into the relevant ViewModel.

**Organisation verification (ROLE ≠ VERIFICATION):** an ORGANISATION account gets trust only from `organisations/{uid}.verificationStatus/verificationLevel`, which only the admin tool (`tools/admin`, Admin SDK) can raise. Rules for every transition live in `OrganisationVerificationPolicy.kt`, `firestore.rules` and `tools/admin/policy.js`; change all three together. Per-type fields/documents come from `OrganisationRequirements.kt`. The org nav graph starts at `org_gate`, which routes by status. Organisation screens use real Firestore data only (no dummy fallback). Full write-up: `docs/organisation-verification/ORG_VERIFICATION_REPORT.md`.

## Firebase setup

`app/google-services.json` must correspond to a Firebase project for package `com.athlink.app` with Email/Password Auth and Firestore enabled. The project runs on the free Spark plan: Cloud Storage is not used. Organisation verification files are stored privately as ≤700 KB chunks in `organisationDocuments/{id}/chunks` (`DocumentChunks.kt`, `FileBytesReader.kt`) and logos as small data URIs.

Security rules are versioned in the repo: `firestore.rules`, `storage.rules`, `firestore.indexes.json` (deploy with `firebase deploy --only firestore:rules,firestore:indexes`; `storage.rules` is kept only for a future Blaze move). The old development rule (`allow read, write: if request.auth != null`) must not be used any more. Rules tests: `cd tests/rules && npm install && npm run emulators:test` (needs Java 21). Admin tasks (grant admin claim, approve/reject/suspend organisations, expire-due, migrate-legacy): `tools/admin/admin.js help`.

CI (`.github/workflows/ci.yml`) runs `testDebugUnitTest`, `assembleDebug` (with a dummy google-services.json) and the rules tests on every push.
