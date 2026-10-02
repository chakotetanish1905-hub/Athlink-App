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

There are no test sources in the project yet (no `app/src/test` or `app/src/androidTest` directories), so there is no test command to run.

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

## Firebase setup

`app/google-services.json` must correspond to a Firebase project for package `com.athlink.app` with Email/Password Auth, Firestore, and Storage enabled. Development Firestore rules used by this project:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```
