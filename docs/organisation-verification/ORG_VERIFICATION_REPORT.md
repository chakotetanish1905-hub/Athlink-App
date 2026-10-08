# Athlink: Organisation Signup + Verification

**Branch:** `feature/organisation-verification` (from `feature/coach-verified-profile`) · **Date:** 2026-10-08

> **Core principle: ROLE ≠ VERIFICATION.** Picking "Organisation" at signup creates an organisation
> *account*. Trust (`verificationStatus` / `verificationLevel` on `organisations/{id}`) can only be
> raised by an Athlink admin through the server-side admin tool. Security rules enforce this.

---

## Phase A audit (before any change)

| Area | Found |
|---|---|
| Stack | Kotlin 2.2.10, AGP 9.0.1, Compose BOM 2024.08 / Material 3, Hilt 2.59 (KSP), Firebase BoM 33.2 (Auth, Firestore, Storage), Navigation-Compose 2.7.7, single `:app` module, package `com.athlink.app` |
| Pattern | `ui/screens/<role>` → `@HiltViewModel` (single `StateFlow`) → `data/repository` → `data/remote`; DI in `utils/AppModule.kt` |
| Auth / role flow | `SignupScreen` role chips → `AuthViewModel.register()` → `FirebaseAuthSource.signUp()` wrote **only** `users/{uid}` (`role = ORGANISATION`). The Auth UID is the `users` doc id. Routing switched on `user.role` → `ORG_NAV` |
| Organisation model | **None.** No `organisations` collection and no verification state of any kind |
| Org dashboard | Showed **all** organisations' events, falling back to `DummyData.events`. "Revenue" was computed from those |
| Org profile | Reused `PlayerProfileScreen`, which showed a "Player" chip and hardcoded stats 12 / 3 / 4.8 |
| Events tab | Bottom bar linked to `org_events`, but the route wasn't registered, so tapping it **crashed** |
| Event system | `events/{auto}` written by `createEvent` with no checks. Any signed-in user could publish under any organisation name |
| Storage | `FirebaseStorage` injected but unused. The project is on the **Spark** plan, and Storage now needs Blaze |
| Rules | No rules files in the repo. `CLAUDE.md` documented `allow read, write: if request.auth != null` |
| Admin | None in the app. The coach plan had already chosen an Auth **custom claim `admin: true`** set by a script |
| Naming | `VerificationStatus` / `VerificationLevel` already exist for coaches (different values), so the org versions are separate `Organisation…` enums |
| Reusable UI | `AppTextField`, `PrimaryButton` / `OutlineButton`, `AthlinkBottomBar`, orange gradient theme, rounded 20 dp cards |

---

## 1. Summary of what changed

- **Separate organisation model** with a verification lifecycle (8 statuses, 4 levels), a private legal/contact record, a representative, an affiliation, document metadata and an append-only audit log.
- **Organisation signup** now writes `users/{uid}` (role ORGANISATION, `organisationId = uid`) **and** an `UNVERIFIED` `organisations/{uid}` in one batch. It rolls back the Auth account on failure (same as coach signup) and sends a verification email.
- **7-step onboarding**: Identity → Legal → Representative → Sports → Location → Documents → Review & Submit. It has a progress indicator, Back/Next, Save draft, inline errors, "Why do we need this?" notes and *Required \* / (optional)* labels. Fields and documents change with the organisation type. The draft is saved on every Next, so the user can leave and resume.
- **Private document uploads** (PDF/JPG/PNG ≤ 5 MB) with progress, replace, remove and optional details (number, issuer, dates). Every document starts as `PENDING_MANUAL_REVIEW`.
- **Login gate**: an incomplete org goes to onboarding, under review / rejected / suspended / expired goes to the status screen, and verified goes to the dashboard.
- **Real Firebase data** on the org dashboard (own events only, drafts, badge, "Complete Verification" call-to-action), on a new **Events tab** (published + drafts; fixes the crash) and on a new **Org Profile**. No dummy fallback anywhere in the organisation area.
- **Event gating**: every organisation (except suspended) can save private drafts. Only VERIFIED / OFFICIAL_GOVERNMENT, unexpired, level ≥ 2 organisations can publish, and the rules enforce it.
- **Security**: full `firestore.rules` + `storage.rules` replacing the open dev rule, 31 emulator tests.
- **Admin tool** (`tools/admin`): approve / reject / suspend / reactivate / expire-due / migrate-legacy, each in one transaction with an audit entry.
- **CI** (GitHub Actions): `assembleDebug`, JVM unit tests, and rules tests on every push.

## 2. Files created

**Model** (`app/src/main/java/com/athlink/app/data/model/`)
`OrganisationStatus.kt` (status/level/type/level enums), `Organisation.kt` (Organisation, OrganisationVerification, OrganisationRepresentative, OrganisationAffiliation, VerificationAuditLog, OrganisationSnapshot), `OrganisationDocument.kt`, `OrganisationVerificationPolicy.kt` (state machine, expiry, badges, publishing, landing), `OrganisationRequirements.kt` (per-type fields and documents), `OrganisationValidators.kt`, `OrganisationDraft.kt` (form model, step validation, Firestore field maps)

**Data**: `data/remote/OrganisationDataSource.kt`, `data/repository/OrganisationRepository.kt`, `utils/ErrorMessages.kt`

**ViewModels**: `viewmodel/OrganisationViewModel.kt`, `OrganisationOnboardingViewModel.kt`, `OrganisationEventsViewModel.kt`

**UI**: `ui/components/OrganisationVerificationBadge.kt`, `OrgFormComponents.kt`, `DocumentUploadCard.kt`; `ui/screens/organisation/` `OrgGateScreen.kt`, `OrganisationOnboardingScreen.kt`, `OnboardingStepContext.kt`, `OrganisationIdentityScreen.kt`, `OrganisationLegalScreen.kt`, `OrganisationRepresentativeScreen.kt`, `OrganisationSportsScreen.kt`, `OrganisationLocationScreen.kt`, `OrganisationDocumentsScreen.kt`, `OrganisationReviewScreen.kt`, `OrganisationVerificationStatusScreen.kt`, `OrgEventsScreen.kt`, `OrgProfileScreen.kt`

**Tests**: `app/src/test/.../OrganisationVerificationPolicyTest.kt`, `OrganisationDraftTest.kt`, `OrganisationValidatorsTest.kt`; `tests/rules/rules.test.mjs` (+ `package.json`)

**Firebase / tooling**: `firestore.rules`, `storage.rules`, `firebase.json`, `firestore.indexes.json`, `tools/admin/{admin.js, policy.js, policy.test.js, package.json}`, `.github/workflows/ci.yml`, `.github/ci/google-services.json` (dummy, CI only)

## 3. Files modified (only where needed)

| File | Change |
|---|---|
| `data/model/User.kt` | + `organisationId` (default `""`) |
| `data/model/EventMessage.kt` | `Event` + `organisationVerificationLevel`, `publishedByUid` (defaults; old docs unaffected) |
| `data/remote/FirestorePaths.kt` | Organisation, event and draft collections; Storage paths |
| `data/remote/FirebaseAuthSource.kt` | + `signUpOrganisation` (atomic, rollback, verification email) |
| `data/remote/FirestoreSource.kt` | + own events, drafts, publish (draft → event in one batch) |
| `data/repository/AuthRepository.kt`, `EventChatRepository.kt` | Pass-through methods |
| `viewmodel/AuthViewModel.kt` | + `registerOrganisation` with friendly errors |
| `ui/screens/auth/SignupScreen.kt` | Organisation: "Organisation Name" label, explainer, calls `registerOrganisation` |
| `ui/components/AppTextField.kt` | + optional `enabled`, `supportingText` (defaults keep old behaviour) |
| `ui/screens/organisation/OrgDashboard.kt` | Real org header, badge, verification CTA, own events, drafts count |
| `ui/screens/organisation/CreateEventScreen.kt` | Save draft / gated Publish, edit drafts, org sports first |
| `navigation/NavRoutes.kt`, `AthlinkNavHost.kt` | Org graph starts at `org_gate`; + onboarding, status, events, edit-draft, real profile |

Player, coach, chat, booking and auth screens were **not** changed.

## 4. Firestore data model

`organisationId` = owner's Auth uid (one organisation per account, so it can't be impersonated).

| Collection / doc | Visibility | Contents |
|---|---|---|
| `users/{uid}` | owner | existing fields + `organisationId` |
| `organisations/{id}` | **public** (signed-in) | organisationId, ownerUid, legalName, displayName, organisationType, sports[], description, logoUrl, website, organisationLevel, country, state, district, city, locality, publicAddress, latitude, longitude, yearEstablished, **verificationStatus, verificationLevel, verifiedAt, verificationExpiresAt** (admin-only), createdAt, updatedAt |
| `organisationVerification/{id}` | owner + admin | officialEmail, officialPhone, registrationNumber, registrationType, issuingAuthority, pan, gstRegistered, gstin, registeredAddress, proprietorName, government fields (department, level, ministry, reference no.), facilityAddress, pincode, yearsActive, approxCoaches, approxAthletes, declarationAccepted; **admin-only:** contactVerified, legalEntityVerified, representativeVerified, addressVerified, sportsCredentialVerified, status, verificationLevel, submittedAt*, reviewedAt, reviewedBy, reviewNotes, rejectionReason |
| `organisationRepresentatives/{id}` | owner + admin | fullName, designation, department, officialEmail, officialPhone, relationshipToOrganisation, authorisationEvidenceType, authorizationDocumentId, verificationStatus (admin), createdAt, updatedAt |
| `organisationAffiliations/{id}` | owner + admin | hasFederationAffiliation, governingBodyName, affiliationNumber, accreditationDetails, verificationStatus (admin) |
| `organisationDocuments/{docId}` | owner + admin | documentId, organisationId, ownerUid, documentType, storagePath, fileName, mimeType, sizeBytes, documentNumber?, issuingAuthority?, issuedDate?, expiryDate?, verificationStatus (admin), uploadedAt, reviewedAt, reviewedBy, reviewNotes |
| `verificationAuditLogs/{logId}` | admin read; append-only | logId, organisationId, action, previousStatus, newStatus, performedBy, performedAt, reason, notes, source (APP / ADMIN) |
| `events/{id}` | public | existing fields + organisationVerificationLevel, publishedByUid |
| `eventDrafts/{id}` | owner | same shape as `Event` |

\* `submittedAt` is set by the owner's submit, and only to the server time.

**Deviation from the brief, on purpose:** the brief listed officialEmail/Phone, pincode and registeredAddress on the Organisation document. Firestore can't hide individual fields, and `organisations/{id}` must be publicly readable, so those fields live in the private `organisationVerification` doc instead. Players see `publicAddress` (e.g. "Andheri West, Mumbai") only.

**Indexes:** one composite index, `verificationAuditLogs (organisationId ASC, performedAt DESC)`, used by the admin `show` command. Every app query is single-field (`ownerUid ==`, `organisationId ==`) and needs no index.

## 5. Document storage (works on the free Spark plan)

Cloud Storage needs the Blaze plan, so verification files are kept **inside Firestore**, privately:

```
organisationDocuments/{documentId}                 metadata: type, fileName, mimeType, sizeBytes, chunkCount,
                                                   storageBackend: "FIRESTORE",
                                                   storagePath: "firestore:organisationDocuments/{id}/chunks"
organisationDocuments/{documentId}/chunks/{i}      { index: i, data: <bytes ≤ 700 KB> }, i = 0..chunkCount-1
organisations/{id}.logoUrl                         small logo as data:image/jpeg;base64 (320 px, ≤ 150 000 chars)
```

- PDFs up to **3 MB**. JPG/PNG photos of any size up to 25 MB are shrunk on the phone (max 2200 px, JPEG) to fit 3 MB.
- Metadata, every chunk and the DOCUMENT_UPLOADED audit entry are written in **one batch**: an upload is all-or-nothing.
- Chunks: readable only by the owner and admins; created only together with their own metadata (index < chunkCount, ≤ 700 KB, ≤ 5 chunks); never updated; deleted only while verification is editable.
- Reviewers download files with `node admin.js show <orgId> --download <dir>` (chunks are joined in order and checked for completeness).
- `storage.rules` stays in the repo for a future move to Cloud Storage (Blaze). The rules and admin tool accept both backends.

## 6. Security rule changes

`allow read, write: if request.auth != null` is **gone**. Highlights:
- **users:** read/write own doc only. `role` is immutable, and `organisationId` can only be set once, to your own uid.
- **organisations:** create only by an ORGANISATION account for its own uid, and only as `UNVERIFIED` / level 0. The owner can never touch `verificationStatus`, `verificationLevel`, `verifiedAt`, `verificationExpiresAt`, except for two moves:
  - `UNVERIFIED → CONTACT_VERIFIED` (L1), only with `request.auth.token.email_verified`
  - editable → `UNDER_REVIEW` (L1), only with a verified email and with the private record moving to UNDER_REVIEW + `submittedAt = request.time` in the same batch.
  Identity and location fields lock while under review or verified. Description, website and logo stay editable.
- **Private records** (verification, representative, affiliation, documents): owner + admin only. All check flags, review fields and `rejectionReason` are unwritable by the owner. Edits are only allowed while verification is editable.
- **Audit logs:** no client reads, updates or deletes. The app may only *append* its own SUBMITTED / RESUBMITTED / DOCUMENT_UPLOADED entries, which are validated field by field.
- **events:** create only by an organisation account for itself, when the org is VERIFIED / OFFICIAL_GOVERNMENT, level ≥ 2 and not expired. `organisationName` and `organisationVerificationLevel` must equal the org document (no impersonation) and `registeredCount` must be 0. Existing events stay readable.
- **eventDrafts:** owner only. Disabled while suspended.
- **Existing collections** got matching rules so nothing breaks:
  - **coaches:** public read; owner can't change rating, earnings or any verification level/status beyond submitting.
  - **coachPrivate:** owner only.
  - **sessions:** only the player and coach; only `status` can change.
  - **chats:** only the two participants in `{player}_{coach}`; `senderId` must be you.
- **Admins** (custom claim `admin: true`) get read access everywhere. **All admin writes go through the Admin SDK**; no client-side admin write path exists.

## 7. Organisation verification flow

```
Signup (role ORGANISATION) ─▶ users/{uid} + organisations/{uid} UNVERIFIED L0 + verification email
   │ login ─▶ org_gate ─▶ onboarding (UNVERIFIED / CONTACT_VERIFIED)
   ▼
7-step onboarding (draft saved each step; resumable)
   │ verify email ("I've verified") ─▶ CONTACT_VERIFIED L1
   ▼ Submit (email verified + all required fields/docs + declaration)
UNDER_REVIEW L1 ─▶ status screen; details locked; drafts allowed
   │ admin: `node admin.js approve <id>` (or --official for government bodies)
   ├─▶ VERIFIED L2 / OFFICIAL_GOVERNMENT L3 (+ verifiedAt, expiresAt = min(1 yr, earliest doc expiry)) ─▶ dashboard, can publish
   └─▶ REJECTED (reason shown) ─▶ correct ─▶ resubmit ─▶ UNDER_REVIEW
VERIFIED ─admin─▶ SUSPENDED (no publishing, no drafts) ─admin─▶ VERIFIED / REJECTED
VERIFIED past verificationExpiresAt ─▶ treated as EXPIRED everywhere ─▶ re-verify ─▶ UNDER_REVIEW
```
Documents are required by type (e.g. *company*: incorporation certificate + PAN + authorisation + sports credential; *academy*: PAN **or** any registration + address proof + declaration/authorisation + any sports credential; *government*: government order / official letter; no PAN/CIN/GST). GST certificate is optional, and only shown if the org is GST-registered.

## 8. Event publishing rule

| State | Save draft | Publish |
|---|---|---|
| UNVERIFIED / CONTACT_VERIFIED / UNDER_REVIEW | ✅ | ❌ |
| VERIFIED (L2) / OFFICIAL_GOVERNMENT (L3), unexpired | ✅ | ✅ |
| REJECTED | ✅ (and edit + resubmit) | ❌ |
| EXPIRED (status or by date) | ✅ | ❌ until re-verified |
| SUSPENDED | ❌ | ❌ |

Enforced three times: the UI (buttons disabled with a reason), the ViewModel (clear message), and **firestore.rules** (the actual guarantee).

## 9. Migration / backward compatibility

- No collection renamed, no field removed. New fields all have defaults, so old `users` and `events` docs deserialize unchanged.
- **Existing organisation accounts** (no `organisations` doc): the gate treats them as UNVERIFIED and sends them to onboarding. The first "Next" creates their docs and links `users.organisationId`. Nothing is ever auto-verified. Optional bulk fix: `node admin.js migrate-legacy --dry-run`, then without `--dry-run`.
- **Existing events** stay readable and listed. Nothing filters on the new fields.
- **Player / coach**: signup batches and all their queries were re-tested against the new rules (rules tests 1, 2, sessions, chat, coaches).
- Legacy `EventViewModel` / `EventRepository.getEvents()` (with the dummy fallback) are untouched. The organisation screens no longer use them.

## 10. Test results

| Suite | Where | Result |
|---|---|---|
| JVM unit tests: 54 (28 coach + 26 organisation: policy, requirements per type, validators, draft mapping) | here + CI `testDebugUnitTest` | ✅ all pass |
| Security rules: 31 emulator tests (Firestore + Storage) | CI | ✅ all pass |
| Admin policy: 5 | here + CI | ✅ all pass |

Brief test mapping:
- **1–3** signups → rules tests
- **4** draft save → rules + `resumeFromSnapshotPrefills`
- **5, 15, 16, 17** publishing → rules + policy tests
- **6, 14** submit / resubmit → rules
- **7** required docs → `requiredDocumentsEnforced`
- **8–11** dynamic forms → draft tests
- **12, 19** privilege escalation → rules + `mappedFieldsNeverContainAdminFields`
- **13** cross-org isolation → rules
- **18** storage → rules
- **20** legacy events → rules

## 11. Build result

`./gradlew testDebugUnitTest assembleDebug` **succeeds** on GitHub Actions (JDK 17, ubuntu-latest, dummy `google-services.json`) for every commit on this branch. Build on your laptop in Android Studio with your real `google-services.json` to install it.

## 12. Manual Firebase console steps

1. **Deploy rules + indexes:** paste `firestore.rules` into Firestore → Rules and publish (or `firebase deploy --only firestore:rules,firestore:indexes`).
2. **No Blaze plan needed.** Documents and logos are stored in Firestore (section 5). Skip `storage.rules` unless you later upgrade and switch to Cloud Storage.
3. Spark free quota (1 GiB stored, 20k writes/day) covers roughly 300+ organisations' document sets.
4. **Authentication → Templates:** check the email-verification template / sender. Email/Password provider must stay enabled.
5. **Make yourself admin:** download a service-account key (Project settings → Service accounts), then:
   `cd tools/admin && npm install && export GOOGLE_APPLICATION_CREDENTIALS=key.json ATHLINK_ADMIN=you@x.com && node admin.js set-admin you@x.com`
6. Optional: `node admin.js migrate-legacy`. Schedule `node admin.js expire-due` (e.g. weekly). Spark has no scheduled functions, so run it yourself or from a cron/GitHub Action with the key as a secret.

## 13. Remaining security risks

- **Contact verification covers only the account email.** The official org email/phone and the representative's contacts are checked manually by the reviewer, not by OTP (no SMS on Spark). The admin tool flags free-mail domains on government applications.
- **Documents can be forged.** Athlink has no registry API, so a human must cross-check PAN / CIN / GSTIN / society numbers with MCA, GST and state portals before `approve`. The `--official` badge must only be granted after confirming with the official government website/phone.
- **Expiry is enforced by date in rules and UI**, but the stored status only flips to EXPIRED when `expire-due` runs. Run it regularly.
- **Event fields** (title, fees, date) are validated lightly in rules. `registeredCount` is locked at 0 for organisations, so a future player-registration flow needs a server-maintained counter (Cloud Function / Admin SDK).
- **The audit log has client-appended entries** (SUBMITTED / DOCUMENT_UPLOADED). They're strictly validated and immutable, but the cleanest design is Cloud Functions (Blaze) writing all entries server-side.
- **Coach collections** got protective rules but no dedicated coach rules suite yet. Extend `tests/rules` when coach onboarding writes subcollections.
- **Service-account keys** grant full access. Keep them off the repo (`.gitignore` covers `*service-account*.json` and `tools/admin/*.json`).

## 14. Testing organisation signup from a fresh account

1. Deploy rules (step 12.1). Build and run from Android Studio. The Spark (free) plan is enough.
2. **Signup:** Create Account → pick **Organisation** → organisation name, a real email you can open, a password → **Register Organisation**. You land on **Organisation verification, step 1**.
3. **Firestore check:** `users/{uid}` has `role: ORGANISATION, organisationId: uid`. `organisations/{uid}` has `verificationStatus: UNVERIFIED`.
4. **Step 1:** fill legal/display name, choose **Sports Academy**, primary sport, a description of 30+ characters, official email/phone. Tap **Next** (the draft is saved). Close (✕) and reopen the app: you resume at step 2 with everything filled in.
5. **Step 2:** note no CIN; PAN optional; GST off; proprietor name required. Switch the type to *Company / Private Limited* and back to see the fields change.
6. **Steps 3–5:** representative + relationship + proof type; level; location + PIN.
7. **Step 6:** upload an address proof, a PAN or registration certificate, an authorisation letter and any sports credential (PDF/JPG/PNG ≤ 5 MB). Watch the progress bar. Try replacing and removing one.
8. **Step 7:** open the email link, tap **I've verified** (status → CONTACT_VERIFIED), tick the declaration, **Submit for review**. The status screen shows *Under review*.
9. **Dashboard:** badge "Under review" plus a verification card. **New Draft** works. **Publish** is disabled with the reason.
10. **Admin:** `node admin.js list` → `node admin.js show <uid> --links` → `node admin.js approve <uid>`. Tap ⟳ on the dashboard: badge "Verified organisation". Publish a draft and it appears in Events → Published.
11. **Reject path:** use a second account and run `node admin.js reject <uid> --reason "Address proof unreadable"`. The app shows the reason, **Correct & resubmit** works.
12. **Suspend:** `node admin.js suspend <uid> --reason test` → no Publish, no New Draft. Then `reactivate`.
13. **Government:** pick *Government Sports Authority*: government fields appear, website is required, PAN/GST are hidden, and the proof is a government order. `approve <uid> --official` gives the gold "Official government organisation" badge. `approve --official` on a non-government type is refused.
