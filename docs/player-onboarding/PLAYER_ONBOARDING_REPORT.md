# Athlink: Player Signup + Profile Onboarding

**Branch:** `feature/player-onboarding` (from `feature/organisation-verification`) · **Date:** 2026-10-09

> **Core decision: ACCOUNT SIGNUP ≠ PLAYER PROFILE.** Signup asks only for what Firebase Auth needs
> plus consent. Everything about the player is collected afterwards in a 7-step onboarding, and
> the player graph keeps routing back to onboarding until the profile is COMPLETE.

---

## Audit (before any change)

| Area | Found |
|---|---|
| Signup | `SignupScreen` role chips. Player branch called the generic `AuthViewModel.register()` → `FirebaseAuthSource.signUp()`, which wrote only `users/{uid}` (uid, name, email, role). No consent, no validation beyond "fields not empty", raw Firebase error text shown |
| Role storage | `users/{uid}.role`; rules already made role immutable after signup |
| Player data | **None.** No `players` collection, no sport / level / location stored anywhere |
| Navigation | Signup/Login → `PLAYER_NAV` → straight to `PLAYER_HOME`. No onboarding, no profile-completion concept |
| Profile screen | `PlayerProfileScreen` was hardcoded: "Mumbai, India", "Cricket", "18–25", stats 12 / 3 / 4.8; Edit icon did nothing |
| Dummy data | Dashboard coach lists use the existing dummy fallback (unchanged); the profile itself had no real data at all |
| Images | No Cloud Storage (Spark plan). Organisation logos are small `data:` URIs via `FileBytesReader` |
| Login | Raw `e.message` shown to users; splash could route a signed-in user to Login on a slow network |
| Reusable UI | `AppTextField`, `PrimaryButton` / `OutlineButton`, `FormSection`, `DropdownField`, `MultiSelectChips`, `ToggleRow`, `InfoBanner`, `KeyValueRow`, `OrgLogoImage` |

## 1. Summary of what changed

- **Player signup** = name, email, password, confirm password, Terms ✔, Privacy ✔. Central validation, double-tap safe, human-readable errors (duplicate email, weak password, offline).
- **`users/{uid}`** (private) gets the player's account data: date of birth, optional phone and gender, consent timestamps, notification preferences, `profileStatus`.
- **New `players/{uid}`** (public to signed-in users) holds the discoverable profile: name, photo, coarse location + derived region, sports, sport-specific details, level, goals, coaching preferences, experience.
- **7-step onboarding** (About you → Sport → Your game → Level → Goals → Training → Ready). Progress bar, Back/Next, inline errors, "Finish later", keyboard-safe scrolling. Progress is saved on every Next; a player who leaves resumes at the first unfinished step after logging in.
- **Player gate**: the player graph now starts at `player_gate`, which sends any player whose profile isn't COMPLETE to onboarding, everyone else to the dashboard.
- **Real profile screen + Edit Profile** (same sections and validation as onboarding). No hardcoded values.
- **Security rules** for `players/{uid}` and new constraints on `users/{uid}`; 16 new emulator tests; 32 new JVM unit tests.
- Small fixes along the way: friendly login errors; splash waits for the stored-session check.

## 2. Files created

**Model** (`data/model/`): `Player.kt` (PlayerProfile, CoachingPreferences, SkillLevel, PlayerGoal, CoachingFormat, TrainingTime, TravelDistance, Gender, AreaType, PlayerProfileStatus, `enumOrNull`), `PlayerSignup.kt`, `PlayerProfileForm.kt` (form, steps, fields, validation, Firestore maps, `PlayerProfilePolicy`), `PlayerValidators.kt`, `PlayerSportProfiles.kt`, `PlayerLocation.kt`, `PlayerAge.kt`

**Data**: `data/remote/PlayerDataSource.kt`, `data/repository/PlayerRepository.kt`

**ViewModel**: `viewmodel/PlayerProfileViewModel.kt`

**UI**: `ui/components/PlayerFormComponents.kt` (PlayerAvatar, SingleChoiceChips, ChoiceCard, SportSelector, ConsentRow), `ui/screens/player/PlayerOnboardingScreen.kt` (gate, onboarding, edit profile), `ui/screens/player/PlayerProfileSections.kt`

**Tests**: `app/src/test/.../PlayerValidatorsTest.kt`, `PlayerProfileFormTest.kt`; player section in `tests/rules/rules.test.mjs`

## 3. Files modified

| File | Change |
|---|---|
| `data/model/User.kt` | + private player fields, all defaulted (old docs load unchanged) |
| `data/remote/FirebaseAuthSource.kt` | + `signUpPlayer` (consent stamps, rollback on failure) |
| `data/remote/FirestorePaths.kt` | + `PLAYERS` |
| `data/remote/FileBytesReader.kt` | + `prepareProfilePhotoDataUri` (reuses the logo compressor) |
| `data/repository/AuthRepository.kt` | + `registerPlayer` |
| `viewmodel/AuthViewModel.kt` | + `registerPlayer`, player field errors, `onUserUpdated`, friendly login errors, `isCheckingSession`; generic `register` deprecated |
| `utils/ErrorMessages.kt` | + `auth()` mapping for signup/login |
| `ui/screens/auth/SignupScreen.kt` | Player branch only: consent checkboxes, inline errors, calls `registerPlayer` |
| `ui/screens/player/PlayerProfileScreen.kt` | Rewritten on real data |
| `navigation/NavRoutes.kt`, `AthlinkNavHost.kt` | + gate / onboarding / edit routes; player graph starts at the gate; splash waits for session |
| `firestore.rules`, `tests/rules/rules.test.mjs` | Player rules + tests (TEST 1 updated: player signup now needs consent) |
| `.github/workflows/ci.yml` | Build failures are reported as an annotation |

Coach and organisation signup code paths, screens and data were **not** changed.

## 4. Firestore schema

`users/{uid}` — **private** (owner + admin)
```
uid, name, email, role, profileImageUrl, organisationId, createdAt   (existing)
dateOfBirth        "yyyy-MM-dd"   source of truth for age (age is never stored)
phoneNumber        optional
gender             optional: MALE | FEMALE | NON_BINARY | PREFER_NOT_TO_SAY | ""
profileStatus      INCOMPLETE | COMPLETE   ("" on older accounts = incomplete)
notificationPreferences { eventNotifications, coachingNotifications, chatNotifications, contentNotifications }
termsAcceptedAt, privacyAcceptedAt   server timestamps, set once
termsVersion       "2026-10"
updatedAt          server timestamp
```

`players/{uid}` — **public** (any signed-in user; owner writes)
```
uid, displayName, photoUrl (small data: URI)
country, state, city, region (derived from state), areaType (optional METRO | TIER_2 | TIER_3 | RURAL)
primarySport, secondarySports[], sportProfile{ key: value }    e.g. { playerRole: "Bowler" }
skillLevel         BEGINNER | INTERMEDIATE | ADVANCED | COMPETITIVE | PROFESSIONAL
goals[]            FIND_COACH, IMPROVE_SKILLS, ...
coachingPreferences { format, trainingTime, maxDistanceKm (0 = any) }
yearsOfExperience, currentTeam, academy, achievements[], bio, ranking
createdAt, updatedAt   server timestamps
```

Why two documents: `users/{uid}` was already owner-only, so it is the right home for date of birth, phone and consents. Coaches need to read a player's sport and level, so that lives in a separate public document. `name` / `displayName` and the photo are the only overlap: `users/{uid}` is private, so the public copy is needed, and both are written in the same batch. `latitude`, `longitude` and `geohash` can be added to `players/{uid}` later without a migration.

## 5. Player signup flow

```
Splash ─▶ (signed in?) ─no─▶ Login ─▶ Signup ─▶ "Player" ─▶ Create Account (name, email, password, Terms, Privacy)
                                                   │ Auth user + users/{uid} (INCOMPLETE, consent stamped)
                                                   ▼
                                  player_gate ─▶ Onboarding 1-7 ─▶ "Continue to Athlink"
                                       │            (saved on every Next)     │ players/{uid} + users COMPLETE (one batch)
                                       │                                      ▼
                                       └──── profile COMPLETE ────────▶ Player Dashboard
Left midway ─▶ log in later ─▶ player_gate ─▶ onboarding at first unfinished step
Profile ─▶ Edit ─▶ Edit Profile (same sections) ─▶ Save ─▶ Profile
Coach / Organisation signup ─▶ unchanged existing flows
```

## 6. Validation rules (`PlayerValidators.kt`, enforced in ViewModel AND repository)

| Field | Rule |
|---|---|
| Full name | required, trimmed, 2–60 chars, letters / space / `.'-` |
| Email | required, format check; Firebase Auth is the final authority |
| Password | required + confirm must match; strength enforced by Firebase (mapped to a friendly message) |
| Terms, Privacy | must be accepted (signup; or Ready step for accounts created before this feature) |
| Date of birth | required, real ISO date, not in the future, age 5–100 |
| Country | required (defaults to India) |
| State | required; in India must be one of the 36 states/UTs |
| City | required, 2–60 letters (no pin codes) |
| Phone | optional; 8–15 digits |
| Primary sport | required, from the shared `Sports.ALL` list |
| Secondary sports | optional, ≤ 5, never the primary sport |
| Sport details | optional; only answers valid for the primary sport are kept |
| Skill level | required |
| Goals | at least one |
| Coaching format, training time, travel distance | required (each has a "flexible / either / any" option) |
| Experience years | optional, 0–80, not more than the player's age |
| Team, academy (≤80), ranking (≤60), bio (≤500), achievements (≤10 × 100) | optional |

## 7. Security rule changes (`firestore.rules`)

- **`players/{uid}`**: read by signed-in users; create/update only by the owner, only if their `users` role is PLAYER, `uid`/`createdAt` immutable, `updatedAt == request.time`, values bounded (enums, sizes), and **private keys (dateOfBirth, email, phoneNumber, gender, password, consents, notificationPreferences, role, profileStatus) are rejected**. No deletes.
- **`users/{uid}`** (still owner-only):
  - player signup must stamp `termsAcceptedAt` and `privacyAcceptedAt` with the server time; nobody can sign up as COMPLETE;
  - consent stamps can be set once and never rewritten;
  - `profileStatus` can only be COMPLETE together with an existing `players/{uid}` and recorded consent (same batch);
  - date of birth is locked once the profile is COMPLETE (a minor can't edit their way to "adult"; support corrects mistakes);
  - `password` keys are rejected; DOB format, gender and status values are checked;
  - role remains immutable (existing rule) — the role selector is UI only.
- Coach and organisation rules unchanged; their signups were re-tested.

## 8. Migration / backward compatibility

- Nothing renamed or removed; every new field has a default, so old `users` docs deserialize unchanged. Unknown stored enum values are ignored (`enumOrNull`), never a crash.
- **Existing players** (no `players` doc, `profileStatus` empty) are routed through onboarding once, with their name pre-filled, and accept Terms/Privacy on the last step (stamped then). No automatic or destructive migration.
- Coach / organisation accounts: untouched. Their `users` docs now also serialize the new defaulted fields (empty strings / nulls), which the rules accept.
- **Rules must be deployed** (`firebase deploy --only firestore:rules`). Until then, writes to `players/{uid}` are denied by the currently deployed rules and onboarding will show "You don't have permission…".
- Older app builds can no longer sign up players once these rules are deployed (they don't send consent); their logins keep working.

## 9. Build result

GitHub Actions (JDK 17, dummy `google-services.json`) on commit `d6911a2`:
`./gradlew testDebugUnitTest` ✅ and `./gradlew assembleDebug` ✅. Security-rules job ✅.
(The cloud workspace used for this change has no Android SDK, so CI is the build of record. Build locally in Android Studio with your real `google-services.json` to install it.)

## 10. Test results

| Suite | Result |
|---|---|
| JVM unit tests: 90 (58 existing + 32 new player tests) | ✅ all pass |
| Security rules: 51 emulator tests (35 existing, updated TEST 1, + 16 player) | ✅ all pass |

Brief scenario mapping:
- **1–6 account**: valid signup, invalid email, Terms/Privacy unchecked → `PlayerProfileFormTest.signup*`, rules `TEST 1`, `PLAYER: signup without consent`; duplicate email / weak password → mapped Firebase exceptions in `AuthViewModel.registerPlayer` (needs a live Firebase project to exercise end to end)
- **7–11 profile validation** → `requiredFieldsPerStep`, `PlayerValidatorsTest.*`
- **12 old documents** → `legacyPlayerWithNoProfileLoadsWithDefaults`, `unknownStoredValuesAreIgnoredNotCrashing`
- **13–14 save/update**, **23–24 data** → rules `onboarding progress…`, `completing the profile…`, `uid and createdAt immutable`
- **17–19 navigation** → `PlayerProfilePolicy` tests (`completeStatusAloneIsNotEnough`, `roundTripThroughStoredModel`, `resumesAtFirstIncompleteStep`)
- **20–22 coach/org/login** → existing rules TEST 2/3 + `coach and organisation signups are unaffected`
- **25 no password** → rules `cannot … store a password`, `privateMapHoldsDobAndNeverAPassword`
- **26 DOB not public** → rules `private data … can never be written to the public profile`, `private users docs stay private`
- **27–30 ownership/role** → rules `cannot create or modify another player's profile`, `cannot change own role…`

Not run: an on-device walkthrough (no emulator in the build environment). Steps for one are below.

## 11. Known limitations

- **Minors**: under-18 players are detected from DOB and shown a "complete with a parent or guardian" notice, and DOB is never exposed. There is **no verifiable guardian consent** yet. India's DPDP Act 2023 requires verifiable parental consent before processing a child's (under-18) data; this needs a product/legal decision before public launch.
- **Discoverability**: any signed-in user can read `players/{uid}` (needed for coaches). There is no per-player visibility setting yet, and minors' city is visible like adults'. Consider hiding city for minors when coach-side player discovery is built.
- **Terms / Privacy documents**: the checkboxes record consent, but the app has no in-app Terms or Privacy Policy page to link to yet.
- **Photo** is stored as a ~80 KB data URI on the public document (Spark plan, no Cloud Storage). Fine for profiles; move to Storage on Blaze.
- **Notification preferences** are stored only; Android notification permission and FCM aren't wired yet (by design).
- **Offline saves**: Firestore only confirms a write once the server has it, so saves wait at most 15 s, then tell the player to check their connection (answers stay on screen). Firestore keeps the write queued and sends it when the connection returns; the next Next / Save writes the same data again (idempotent). Onboarding moves on after a timed-out progress save; finishing the profile does not until the write is confirmed.
- **Changing DOB** after completion requires support (Admin SDK); there is no admin tool command for it yet.

## Testing on a device

1. Deploy rules: `firebase deploy --only firestore:rules`.
2. Signup → Player → fill name/email/password, tick both boxes → Create Account → onboarding step 1.
3. Firestore: `users/{uid}` has `profileStatus: INCOMPLETE`, consent timestamps, no password.
4. Fill step 1, Next → `players/{uid}` appears. Tap **Finish later → Save & log out**; log in again → you resume at step 2.
5. Pick Cricket → step 3 shows role / batting / bowling; switch to Football → position / foot.
6. Finish all steps → **Continue to Athlink** → dashboard; `users/{uid}.profileStatus` is `COMPLETE`.
7. Profile tab shows your real data; Edit → change bio → Save. DOB is read-only now.
8. Log out / in → straight to the dashboard. Coach and organisation signups behave as before.
