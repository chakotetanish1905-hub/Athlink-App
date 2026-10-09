// Security-rules tests for Athlink. Run with the Firestore + Storage emulators:
//   cd tests/rules && npm install && npm run emulators:test
// Each test maps to a numbered requirement (TEST n) from the organisation verification brief.

import { readFileSync } from 'node:fs';
import { test, before, after, beforeEach } from 'node:test';
import {
  initializeTestEnvironment, assertSucceeds, assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, Timestamp,
  collection, getDocs, query, where, Bytes,
} from 'firebase/firestore';
import { ref, uploadBytes, getBytes, deleteObject } from 'firebase/storage';

let env;
const ORG = 'orgA';
const OTHER = 'orgB';
const PLAYER = 'player1';

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-athlink',
    firestore: { rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') },
    storage: { rules: readFileSync(new URL('../../storage.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await env.clearStorage(); });

// ── Helpers ────────────────────────────────────────────────────────────────
const ctx = (uid, claims = {}) => env.authenticatedContext(uid, { email_verified: true, ...claims });
const db = (uid, claims) => ctx(uid, claims).firestore();
const unverifiedDb = (uid) => env.authenticatedContext(uid, { email_verified: false }).firestore();

function orgProfile(id, overrides = {}) {
  return {
    organisationId: id, ownerUid: id, legalName: 'Andheri Sports Academy', displayName: 'ASA', organisationType: 'SPORTS_ACADEMY',
    sports: ['Cricket'], description: 'A grassroots cricket academy in Mumbai for juniors.', logoUrl: '', website: '',
    organisationLevel: 'DISTRICT', country: 'India', state: 'MH', district: 'Mumbai', city: 'Mumbai', locality: 'Andheri',
    publicAddress: 'Andheri, Mumbai', latitude: null, longitude: null, yearEstablished: 2015,
    verificationStatus: 'UNVERIFIED', verificationLevel: 'LEVEL_0_UNVERIFIED', verifiedAt: null, verificationExpiresAt: null,
    createdAt: Timestamp.now(), updatedAt: Timestamp.now(), ...overrides,
  };
}
function verificationRecord(id, overrides = {}) {
  return {
    verificationId: id, organisationId: id, ownerUid: id, officialEmail: 'x@asa.org', officialPhone: '9876543210',
    registrationNumber: '', registrationType: 'Sports business / academy', issuingAuthority: '', pan: '', gstRegistered: false,
    gstin: '', registeredAddress: 'Somewhere', proprietorName: 'R K', governmentDepartmentName: '', governmentLevel: '',
    ministryOrDepartment: '', authorisationReferenceNumber: '', facilityAddress: 'Ground', pincode: '400053',
    yearsActive: null, approxCoaches: null, approxAthletes: null, declarationAccepted: false,
    contactVerified: false, legalEntityVerified: false, representativeVerified: false, addressVerified: false,
    sportsCredentialVerified: false, status: 'UNVERIFIED', verificationLevel: 'LEVEL_0_UNVERIFIED', submittedAt: null,
    reviewedAt: null, reviewedBy: null, reviewNotes: null, rejectionReason: null, updatedAt: Timestamp.now(), ...overrides,
  };
}
// Shape written by FirebaseAuthSource.signUpPlayer.
function playerUser(id, overrides = {}) {
  return {
    uid: id, name: 'Asha Patel', email: `${id}@x.org`, role: 'PLAYER', profileImageUrl: '', organisationId: '',
    createdAt: Date.now(), dateOfBirth: '', phoneNumber: '', gender: '', profileStatus: 'INCOMPLETE',
    notificationPreferences: { eventNotifications: true, coachingNotifications: true, chatNotifications: true, contentNotifications: false },
    termsAcceptedAt: serverTimestamp(), privacyAcceptedAt: serverTimestamp(), termsVersion: '2026-10', updatedAt: serverTimestamp(),
    ...overrides,
  };
}
// Shape written by PlayerProfileForm.toPlayerFields (+ timestamps).
function playerPublic(id, overrides = {}) {
  return {
    uid: id, displayName: 'Asha Patel', photoUrl: '', country: 'India', state: 'Gujarat', city: 'Surat', region: 'West India',
    areaType: 'TIER_2', primarySport: 'Cricket', secondarySports: ['Football'], sportProfile: { playerRole: 'Bowler' },
    skillLevel: 'INTERMEDIATE', goals: ['FIND_COACH'], coachingPreferences: { format: 'GROUP', trainingTime: 'EVENING', maxDistanceKm: 10 },
    yearsOfExperience: 3, currentTeam: '', academy: '', achievements: [], bio: '', ranking: '',
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}
async function seedPlayer(id, userOverrides = {}, withProfile = false) {
  await seed(async (f) => {
    await setDoc(doc(f, 'users', id), playerUser(id, {
      termsAcceptedAt: Timestamp.now(), privacyAcceptedAt: Timestamp.now(), updatedAt: Timestamp.now(), ...userOverrides,
    }));
    if (withProfile) await setDoc(doc(f, 'players', id), playerPublic(id, { createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
  });
}

async function seed(fn) { await env.withSecurityRulesDisabled(async (c) => fn(c.firestore(), c.storage())); }
async function seedOrg(id, orgOverrides = {}, verificationOverrides = {}) {
  await seed(async (f) => {
    await setDoc(doc(f, 'users', id), { uid: id, name: 'ASA', email: `${id}@x.org`, role: 'ORGANISATION', organisationId: id });
    await setDoc(doc(f, 'organisations', id), orgProfile(id, orgOverrides));
    await setDoc(doc(f, 'organisationVerification', id), verificationRecord(id, verificationOverrides));
  });
}
function event(id, orgId, overrides = {}) {
  return {
    id, organisationId: orgId, organisationName: 'ASA', title: 'Junior Cup', description: '', sport: 'Cricket', date: '2026-12-01',
    location: 'Andheri', fees: 100, maxParticipants: 50, registeredCount: 0, imageUrl: '',
    organisationVerificationLevel: 'LEVEL_2_ORGANISATION_VERIFIED', publishedByUid: orgId, createdAt: Date.now(), ...overrides,
  };
}
const verified = { verificationStatus: 'VERIFIED', verificationLevel: 'LEVEL_2_ORGANISATION_VERIFIED' };

// ── TEST 1-3: signups ──────────────────────────────────────────────────────
test('TEST 1: player signup writes its own users doc (with server-time consent)', async () => {
  await assertSucceeds(setDoc(doc(db(PLAYER), 'users', PLAYER), playerUser(PLAYER)));
});

test('TEST 2: coach signup batch (users + coaches + coachPrivate) still works', async () => {
  const f = db('coach1');
  const b = writeBatch(f);
  b.set(doc(f, 'users', 'coach1'), { uid: 'coach1', name: 'C', email: 'c@x.org', role: 'COACH', organisationId: '' });
  b.set(doc(f, 'coaches', 'coach1'), { uid: 'coach1', name: 'C', verificationStatus: 'NOT_SUBMITTED', profileStatus: 'DRAFT',
    verificationLevel: 'LEVEL_0_REGISTERED', rating: 0, reviewCount: 0, totalEarnings: 0 });
  b.set(doc(f, 'coachPrivate', 'coach1'), { uid: 'coach1', email: 'c@x.org', phone: '9876543210' });
  await assertSucceeds(b.commit());
});

test('TEST 3: organisation signup creates users + UNVERIFIED organisation in one batch', async () => {
  const f = db(ORG);
  const b = writeBatch(f);
  b.set(doc(f, 'users', ORG), { uid: ORG, name: 'ASA', email: 'a@x.org', role: 'ORGANISATION', organisationId: ORG });
  b.set(doc(f, 'organisations', ORG), orgProfile(ORG, { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  await assertSucceeds(b.commit());
});

test('TEST 3b / 12: signup cannot create an organisation that is already VERIFIED', async () => {
  const f = db(ORG);
  const b = writeBatch(f);
  b.set(doc(f, 'users', ORG), { uid: ORG, name: 'ASA', email: 'a@x.org', role: 'ORGANISATION', organisationId: ORG });
  b.set(doc(f, 'organisations', ORG), orgProfile(ORG, { ...verified, createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  await assertFails(b.commit());
});

test('a player cannot create an organisation profile', async () => {
  await seed(async (f) => setDoc(doc(f, 'users', PLAYER), { uid: PLAYER, role: 'PLAYER' }));
  await assertFails(setDoc(doc(db(PLAYER), 'organisations', PLAYER), orgProfile(PLAYER, { createdAt: serverTimestamp(), updatedAt: serverTimestamp() })));
});

test('users cannot change their role or claim another organisation', async () => {
  await seed(async (f) => setDoc(doc(f, 'users', PLAYER), { uid: PLAYER, role: 'PLAYER', organisationId: '' }));
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER), { role: 'ORGANISATION' }));
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER), { organisationId: PLAYER }));
});

// ── TEST 4: save incomplete onboarding ─────────────────────────────────────
test('TEST 4: owner can save an incomplete draft', async () => {
  await seedOrg(ORG);
  await assertSucceeds(updateDoc(doc(db(ORG), 'organisations', ORG), { legalName: 'ASA Pvt', city: 'Pune', updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(db(ORG), 'organisationVerification', ORG), { pan: 'AAAPA1234A', updatedAt: serverTimestamp() }));
});

// ── TEST 12 / 19: no self-verification, no privilege escalation ────────────
test('TEST 12: owner cannot mark themselves verified or official', async () => {
  await seedOrg(ORG);
  const f = db(ORG);
  for (const s of ['VERIFIED', 'OFFICIAL_GOVERNMENT', 'SUSPENDED', 'REJECTED', 'EXPIRED']) {
    await assertFails(updateDoc(doc(f, 'organisations', ORG), { verificationStatus: s, updatedAt: serverTimestamp() }));
  }
  await assertFails(updateDoc(doc(f, 'organisations', ORG), { verificationLevel: 'LEVEL_3_OFFICIAL_GOVERNMENT', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(f, 'organisations', ORG), { verificationExpiresAt: Timestamp.fromMillis(Date.now() + 1e10), updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(f, 'organisations', ORG), { verifiedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
});

test('TEST 19: owner cannot set review fields on the private record', async () => {
  await seedOrg(ORG);
  const f = db(ORG);
  for (const field of ['contactVerified', 'legalEntityVerified', 'representativeVerified', 'addressVerified', 'sportsCredentialVerified']) {
    await assertFails(updateDoc(doc(f, 'organisationVerification', ORG), { [field]: true, updatedAt: serverTimestamp() }));
  }
  await assertFails(updateDoc(doc(f, 'organisationVerification', ORG), { reviewedBy: ORG, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(f, 'organisationVerification', ORG), { rejectionReason: '', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(f, 'organisationVerification', ORG), { status: 'VERIFIED', updatedAt: serverTimestamp() }));
});

test('contact verification needs a verified email', async () => {
  await seedOrg(ORG);
  const upd = { verificationStatus: 'CONTACT_VERIFIED', verificationLevel: 'LEVEL_1_CONTACT_VERIFIED', updatedAt: serverTimestamp() };
  await assertFails(updateDoc(doc(unverifiedDb(ORG), 'organisations', ORG), upd));
  await assertSucceeds(updateDoc(doc(db(ORG), 'organisations', ORG), upd));
  // ...and can't jump to level 2.
  await seedOrg(OTHER);
  await assertFails(updateDoc(doc(db(OTHER), 'organisations', OTHER), { ...upd, verificationLevel: 'LEVEL_2_ORGANISATION_VERIFIED' }));
});

// ── TEST 6 / 14: submit and resubmit ───────────────────────────────────────
async function submit(f, id, previous = 'UNVERIFIED') {
  const b = writeBatch(f);
  b.update(doc(f, 'organisations', id), { verificationStatus: 'UNDER_REVIEW', verificationLevel: 'LEVEL_1_CONTACT_VERIFIED', updatedAt: serverTimestamp() });
  b.update(doc(f, 'organisationVerification', id), { status: 'UNDER_REVIEW', verificationLevel: 'LEVEL_1_CONTACT_VERIFIED',
    submittedAt: serverTimestamp(), declarationAccepted: true, updatedAt: serverTimestamp() });
  const log = doc(collection(f, 'verificationAuditLogs'));
  b.set(log, { logId: log.id, organisationId: id, action: previous === 'REJECTED' ? 'RESUBMITTED' : 'SUBMITTED', previousStatus: previous,
    newStatus: 'UNDER_REVIEW', performedBy: id, performedAt: serverTimestamp(), reason: '', notes: '', source: 'APP' });
  return b.commit();
}

test('TEST 6: owner can submit for review (with audit entry)', async () => {
  await seedOrg(ORG);
  await assertSucceeds(submit(db(ORG), ORG));
});

test('submit requires a verified email', async () => {
  await seedOrg(ORG);
  await assertFails(submit(unverifiedDb(ORG), ORG));
});

test('details are locked while UNDER_REVIEW (except description / website / logo)', async () => {
  await seedOrg(ORG, { verificationStatus: 'UNDER_REVIEW', verificationLevel: 'LEVEL_1_CONTACT_VERIFIED' }, { status: 'UNDER_REVIEW' });
  const f = db(ORG);
  await assertFails(updateDoc(doc(f, 'organisations', ORG), { legalName: 'Something Else', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(f, 'organisationVerification', ORG), { pan: 'BBBPB1234B', updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(f, 'organisations', ORG), { description: 'Updated description of our academy programmes.', updatedAt: serverTimestamp() }));
  await assertFails(submit(f, ORG, 'UNDER_REVIEW')); // duplicate submission
});

test('TEST 14: a rejected organisation can correct and resubmit', async () => {
  await seedOrg(ORG, { verificationStatus: 'REJECTED', verificationLevel: 'LEVEL_1_CONTACT_VERIFIED' },
    { status: 'REJECTED', rejectionReason: 'Blurry certificate' });
  const f = db(ORG);
  await assertSucceeds(updateDoc(doc(f, 'organisationVerification', ORG), { registeredAddress: 'Corrected address', updatedAt: serverTimestamp() }));
  await assertSucceeds(submit(f, ORG, 'REJECTED'));
});

test('a suspended organisation cannot resubmit or edit', async () => {
  await seedOrg(ORG, { verificationStatus: 'SUSPENDED', verificationLevel: 'LEVEL_2_ORGANISATION_VERIFIED' }, { status: 'SUSPENDED' });
  await assertFails(submit(db(ORG), ORG, 'SUSPENDED'));
  await assertFails(updateDoc(doc(db(ORG), 'organisations', ORG), { legalName: 'New', updatedAt: serverTimestamp() }));
});

// ── TEST 13: cross-organisation isolation ──────────────────────────────────
test('TEST 13: an organisation cannot read or modify another organisation', async () => {
  await seedOrg(ORG);
  await seedOrg(OTHER);
  const f = db(OTHER);
  await assertFails(updateDoc(doc(f, 'organisations', ORG), { displayName: 'Hijacked', updatedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(f, 'organisationVerification', ORG)));
  await assertFails(updateDoc(doc(f, 'organisationVerification', ORG), { pan: 'X', updatedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(f, 'organisationRepresentatives', ORG)));
  // Public profile is readable by everyone signed in.
  await assertSucceeds(getDoc(doc(f, 'organisations', ORG)));
  await assertSucceeds(getDoc(doc(db(PLAYER), 'organisations', ORG)));
});

test('documents: owner creates PENDING metadata only; others cannot read it; review fields are admin-only', async () => {
  await seedOrg(ORG);
  const f = db(ORG);
  const id = 'doc1';
  const meta = {
    documentId: id, organisationId: ORG, ownerUid: ORG, documentType: 'ADDRESS_PROOF',
    storagePath: `firestore:organisationDocuments/${id}/chunks`, storageBackend: 'FIRESTORE', chunkCount: 1,
    fileName: 'bill.pdf', mimeType: 'application/pdf', sizeBytes: 2048,
    documentNumber: null, issuingAuthority: null, issuedDate: null, expiryDate: null, verificationStatus: 'PENDING_MANUAL_REVIEW',
    uploadedAt: serverTimestamp(), reviewedAt: null, reviewedBy: null, reviewNotes: null,
  };
  await assertFails(setDoc(doc(f, 'organisationDocuments', id), { ...meta, verificationStatus: 'VERIFIED' }));
  await assertFails(setDoc(doc(f, 'organisationDocuments', id), { ...meta, storagePath: `firestore:organisationDocuments/other/chunks` }));
  await assertFails(setDoc(doc(f, 'organisationDocuments', id), { ...meta, chunkCount: 9 }));
  await assertFails(setDoc(doc(f, 'organisationDocuments', id), { ...meta, sizeBytes: 4 * 1024 * 1024 }));
  await assertSucceeds(setDoc(doc(f, 'organisationDocuments', id), meta));
  await assertSucceeds(getDocs(query(collection(f, 'organisationDocuments'), where('ownerUid', '==', ORG))));
  await assertFails(getDoc(doc(db(OTHER), 'organisationDocuments', id)));
  await assertFails(updateDoc(doc(f, 'organisationDocuments', id), { verificationStatus: 'VERIFIED' }));
  await assertSucceeds(updateDoc(doc(f, 'organisationDocuments', id), { expiryDate: '2030-01-01' }));
});

test('audit logs: users cannot read, forge admin actions, edit or delete', async () => {
  await seedOrg(ORG);
  await seed(async (f) => setDoc(doc(f, 'verificationAuditLogs', 'l1'), { logId: 'l1', organisationId: ORG, action: 'REJECTED' }));
  const f = db(ORG);
  await assertFails(getDoc(doc(f, 'verificationAuditLogs', 'l1')));
  await assertFails(updateDoc(doc(f, 'verificationAuditLogs', 'l1'), { action: 'APPROVED' }));
  await assertFails(deleteDoc(doc(f, 'verificationAuditLogs', 'l1')));
  await assertFails(setDoc(doc(f, 'verificationAuditLogs', 'l2'), { logId: 'l2', organisationId: ORG, action: 'APPROVED', previousStatus: 'UNDER_REVIEW',
    newStatus: 'VERIFIED', performedBy: ORG, performedAt: serverTimestamp(), reason: '', notes: '', source: 'APP' }));
  // Admins can read the trail.
  await assertSucceeds(getDoc(doc(db('admin1', { admin: true }), 'verificationAuditLogs', 'l1')));
});

// ── Spark-plan document storage: file bytes in organisationDocuments/{id}/chunks ──
function chunkedUpload(f, id, owner, { parts = 2, partSize = 1000, chunkCount = parts } = {}) {
  const b = writeBatch(f);
  b.set(doc(f, 'organisationDocuments', id), {
    documentId: id, organisationId: owner, ownerUid: owner, documentType: 'AUTHORIZATION_LETTER',
    storagePath: `firestore:organisationDocuments/${id}/chunks`, storageBackend: 'FIRESTORE', chunkCount,
    fileName: 'letter.pdf', mimeType: 'application/pdf', sizeBytes: parts * partSize,
    documentNumber: null, issuingAuthority: null, issuedDate: null, expiryDate: null, verificationStatus: 'PENDING_MANUAL_REVIEW',
    uploadedAt: serverTimestamp(), reviewedAt: null, reviewedBy: null, reviewNotes: null,
  });
  for (let i = 0; i < parts; i++) {
    b.set(doc(f, 'organisationDocuments', id, 'chunks', String(i)), { index: i, data: Bytes.fromUint8Array(new Uint8Array(partSize).fill(i + 1)) });
  }
  return b.commit();
}

test('TEST 18b: chunked documents are stored atomically and readable only by the owner / admin', async () => {
  await seedOrg(ORG);
  await assertSucceeds(chunkedUpload(db(ORG), 'docA', ORG));
  await assertSucceeds(getDocs(collection(db(ORG), 'organisationDocuments', 'docA', 'chunks')));
  await assertFails(getDocs(collection(db(OTHER), 'organisationDocuments', 'docA', 'chunks')));
  await assertFails(getDoc(doc(db(PLAYER), 'organisationDocuments', 'docA', 'chunks', '0')));
  await assertSucceeds(getDocs(collection(db('admin1', { admin: true }), 'organisationDocuments', 'docA', 'chunks')));
  // Chunks can't be rewritten after upload.
  await assertFails(setDoc(doc(db(ORG), 'organisationDocuments', 'docA', 'chunks', '0'), { index: 0, data: Bytes.fromUint8Array(new Uint8Array(5)) }));
});

test('chunk limits: no extra chunks, no oversize chunks, no chunks for someone else', async () => {
  await seedOrg(ORG);
  await seedOrg(OTHER);
  await assertFails(chunkedUpload(db(ORG), 'd1', ORG, { parts: 3, chunkCount: 2 }));        // more chunks than declared
  await assertFails(chunkedUpload(db(ORG), 'd2', ORG, { parts: 1, partSize: 800 * 1024 })); // chunk > 700 KB
  await assertFails(chunkedUpload(db(OTHER), 'd3', ORG));                                    // owner mismatch
});

test('chunked documents can be removed while editable, not while under review', async () => {
  await seedOrg(ORG);
  await assertSucceeds(chunkedUpload(db(ORG), 'docB', ORG));
  const del = (f) => { const b = writeBatch(f); b.delete(doc(f, 'organisationDocuments', 'docB', 'chunks', '0'));
    b.delete(doc(f, 'organisationDocuments', 'docB', 'chunks', '1')); b.delete(doc(f, 'organisationDocuments', 'docB')); return b.commit(); };
  await seed(async (f) => updateDoc(doc(f, 'organisations', ORG), { verificationStatus: 'UNDER_REVIEW' }));
  await assertFails(del(db(ORG)));
  await assertFails(chunkedUpload(db(ORG), 'docC', ORG));
  await seed(async (f) => updateDoc(doc(f, 'organisations', ORG), { verificationStatus: 'REJECTED' }));
  await assertSucceeds(del(db(ORG)));
});

test('logo data URI is size-limited on the public profile', async () => {
  await seedOrg(ORG, { verificationStatus: 'UNDER_REVIEW' });
  const small = 'data:image/jpeg;base64,' + 'A'.repeat(40_000);
  await assertSucceeds(updateDoc(doc(db(ORG), 'organisations', ORG), { logoUrl: small, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db(ORG), 'organisations', ORG), { logoUrl: 'data:' + 'A'.repeat(200_000), updatedAt: serverTimestamp() }));
});

// ── TEST 5 / 15 / 16 / 17: event publishing ────────────────────────────────
test('TEST 5: an unverified organisation cannot publish but can save drafts', async () => {
  await seedOrg(ORG);
  const f = db(ORG);
  await assertFails(setDoc(doc(f, 'events', 'e1'), event('e1', ORG, { organisationVerificationLevel: 'LEVEL_0_UNVERIFIED' })));
  await assertSucceeds(setDoc(doc(f, 'eventDrafts', 'd1'), event('d1', ORG)));
  await assertFails(getDoc(doc(db(OTHER), 'eventDrafts', 'd1')));
});

test('TEST 15: a verified organisation can publish (and publish a draft in one batch)', async () => {
  await seedOrg(ORG, verified);
  await seed(async (f) => setDoc(doc(f, 'eventDrafts', 'd1'), event('d1', ORG)));
  const f = db(ORG);
  await assertSucceeds(setDoc(doc(f, 'events', 'e1'), event('e1', ORG)));
  const b = writeBatch(f);
  b.set(doc(f, 'events', 'e2'), event('e2', ORG));
  b.delete(doc(f, 'eventDrafts', 'd1'));
  await assertSucceeds(b.commit());
});

test('official government organisations can publish with level 3', async () => {
  await seedOrg(ORG, { verificationStatus: 'OFFICIAL_GOVERNMENT', verificationLevel: 'LEVEL_3_OFFICIAL_GOVERNMENT' });
  await assertSucceeds(setDoc(doc(db(ORG), 'events', 'e1'), event('e1', ORG, { organisationVerificationLevel: 'LEVEL_3_OFFICIAL_GOVERNMENT' })));
});

test('publishing cannot impersonate: name / level / owner must match the organisation', async () => {
  await seedOrg(ORG, verified);
  const f = db(ORG);
  await assertFails(setDoc(doc(f, 'events', 'e1'), event('e1', ORG, { organisationName: 'Sports Authority of India' })));
  await assertFails(setDoc(doc(f, 'events', 'e1'), event('e1', ORG, { organisationVerificationLevel: 'LEVEL_3_OFFICIAL_GOVERNMENT' })));
  await assertFails(setDoc(doc(f, 'events', 'e1'), event('e1', OTHER)));
  await assertFails(setDoc(doc(f, 'events', 'e1'), event('e1', ORG, { registeredCount: 500 })));
  // Another (unverified) org can't publish under the verified org's id either.
  await seedOrg(OTHER);
  await assertFails(setDoc(doc(db(OTHER), 'events', 'e9'), event('e9', ORG)));
});

test('TEST 16: a suspended organisation can neither publish nor draft', async () => {
  await seedOrg(ORG, { verificationStatus: 'SUSPENDED', verificationLevel: 'LEVEL_2_ORGANISATION_VERIFIED' });
  const f = db(ORG);
  await assertFails(setDoc(doc(f, 'events', 'e1'), event('e1', ORG)));
  await assertFails(setDoc(doc(f, 'eventDrafts', 'd1'), event('d1', ORG)));
});

test('TEST 17: expired verification blocks publishing and re-opens editing', async () => {
  await seedOrg(ORG, { ...verified, verificationExpiresAt: Timestamp.fromMillis(Date.now() - 60_000) });
  const f = db(ORG);
  await assertFails(setDoc(doc(f, 'events', 'e1'), event('e1', ORG)));
  await assertSucceeds(updateDoc(doc(f, 'organisationVerification', ORG), { registeredAddress: 'Renewed', updatedAt: serverTimestamp() }));
  await assertSucceeds(submit(f, ORG, 'EXPIRED'));
});

test('a verified organisation with a future expiry date can still publish', async () => {
  await seedOrg(ORG, { ...verified, verificationExpiresAt: Timestamp.fromMillis(Date.now() + 86_400_000) });
  await assertSucceeds(setDoc(doc(db(ORG), 'events', 'e1'), event('e1', ORG)));
});

test('TEST 20: existing (legacy) event documents stay readable', async () => {
  await seed(async (f) => setDoc(doc(f, 'events', 'old1'), { id: 'old1', organisationId: 'legacyOrg', organisationName: 'Old Club',
    title: 'Old Event', sport: 'Football', date: '2024-10-15', location: 'Delhi', fees: 1500, maxParticipants: 200, registeredCount: 165 }));
  await assertSucceeds(getDoc(doc(db(PLAYER), 'events', 'old1')));
  await assertSucceeds(getDocs(collection(db(PLAYER), 'events')));
});

// ── TEST 18: Storage ───────────────────────────────────────────────────────
const pdf = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d]); // "%PDF-"

test('TEST 18: verification documents are private and type/size limited', async () => {
  await seedOrg(ORG);
  const owner = ctx(ORG).storage();
  const path = `organisation_documents/${ORG}/doc1/cert.pdf`;
  await assertSucceeds(uploadBytes(ref(owner, path), pdf, { contentType: 'application/pdf' }));
  await assertFails(uploadBytes(ref(owner, `organisation_documents/${ORG}/doc2/run.exe`), pdf, { contentType: 'application/x-msdownload' }));
  await assertFails(uploadBytes(ref(owner, `organisation_documents/${ORG}/doc3/big.pdf`), new Uint8Array(5 * 1024 * 1024 + 1), { contentType: 'application/pdf' }));
  await assertSucceeds(getBytes(ref(owner, path)));
  await assertFails(getBytes(ref(ctx(OTHER).storage(), path)));
  await assertFails(getBytes(ref(ctx(PLAYER).storage(), path)));
  await assertFails(uploadBytes(ref(ctx(OTHER).storage(), `organisation_documents/${ORG}/x/evil.pdf`), pdf, { contentType: 'application/pdf' }));
  await assertSucceeds(getBytes(ref(ctx('admin1', { admin: true }).storage(), path)));
  await assertFails(getBytes(ref(env.unauthenticatedContext().storage(), path)));
});

test('documents cannot be uploaded or deleted while under review', async () => {
  await seedOrg(ORG);
  const owner = ctx(ORG).storage();
  const path = `organisation_documents/${ORG}/doc1/cert.pdf`;
  await assertSucceeds(uploadBytes(ref(owner, path), pdf, { contentType: 'application/pdf' }));
  await seed(async (f) => updateDoc(doc(f, 'organisations', ORG), { verificationStatus: 'UNDER_REVIEW' }));
  await assertFails(uploadBytes(ref(owner, `organisation_documents/${ORG}/doc2/new.pdf`), pdf, { contentType: 'application/pdf' }));
  await assertFails(deleteObject(ref(owner, path)));
});

// ── Other existing collections keep working ────────────────────────────────
// ── Pre-verified directory listings (admin tool `seed-directory`) ──────────
const DIR = 'dir-vadodara-arjun-badminton-academy';
const directoryListing = (overrides = {}) => orgProfile(DIR, {
  ownerUid: '', displayName: 'Arjun Badminton Academy', legalName: 'Arjun Badminton Academy', sports: ['Badminton'],
  city: 'Vadodara', locality: 'Tandalja', publicAddress: 'Tandalja, Vadodara', state: 'Gujarat', district: 'Vadodara',
  listingSource: 'DIRECTORY_IMPORT', venueCategory: 'INDOOR', googleRating: 5.0, googleReviewCount: 34, ratingSource: 'snapshot',
  ...verified, ...overrides,
});

test('DIRECTORY: signed-in users can read a listing; signed-out users cannot', async () => {
  await seed(async (f) => setDoc(doc(f, 'organisations', DIR), directoryListing()));
  await assertSucceeds(getDoc(doc(db(PLAYER), 'organisations', DIR)));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'organisations', DIR)));
});

test('DIRECTORY: nobody can edit, claim, delete or publish as a listing', async () => {
  await seed(async (f) => setDoc(doc(f, 'organisations', DIR), directoryListing()));
  await seedOrg(ORG, verified);
  for (const f of [db(ORG), db(PLAYER)]) {
    await assertFails(updateDoc(doc(f, 'organisations', DIR), { googleRating: 1, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(f, 'organisations', DIR), { ownerUid: ORG, updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(f, 'organisations', DIR)));
  }
  await assertFails(setDoc(doc(db(ORG), 'events', 'e-dir'), event('e-dir', DIR, { organisationName: 'Arjun Badminton Academy', publishedByUid: ORG })));
});

test('DIRECTORY: an organisation account cannot mark itself a listing or set a rating', async () => {
  const f = db(ORG);
  const b = writeBatch(f);
  b.set(doc(f, 'users', ORG), { uid: ORG, name: 'ASA', email: 'a@x.org', role: 'ORGANISATION', organisationId: ORG });
  b.set(doc(f, 'organisations', ORG), orgProfile(ORG, { listingSource: 'DIRECTORY_IMPORT', createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  await assertFails(b.commit());

  await seedOrg(ORG);
  await assertFails(updateDoc(doc(f, 'organisations', ORG), { googleRating: 5, googleReviewCount: 999, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(f, 'organisations', ORG), { listingSource: 'DIRECTORY_IMPORT', updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(f, 'organisations', ORG), { legalName: 'Andheri Sports Academy Pvt', updatedAt: serverTimestamp() }));
});

test('sessions: player books, coach updates status only', async () => {
  const s = { id: 's1', coachId: 'coach1', playerId: PLAYER, status: 'PENDING', price: 800 };
  await assertSucceeds(setDoc(doc(db(PLAYER), 'sessions', 's1'), s));
  await assertSucceeds(getDocs(query(collection(db('coach1'), 'sessions'), where('coachId', '==', 'coach1'))));
  await assertSucceeds(updateDoc(doc(db('coach1'), 'sessions', 's1'), { status: 'CONFIRMED' }));
  await assertFails(updateDoc(doc(db('coach1'), 'sessions', 's1'), { price: 1 }));
  await assertFails(getDoc(doc(db('stranger'), 'sessions', 's1')));
});

test('chat: only the two participants can read and write', async () => {
  const thread = `${PLAYER}_coach1`;
  await assertSucceeds(setDoc(doc(db(PLAYER), 'chats', thread, 'messages', 'm1'), { id: 'm1', senderId: PLAYER, receiverId: 'coach1', content: 'hi' }));
  await assertSucceeds(getDocs(collection(db('coach1'), 'chats', thread, 'messages')));
  await assertFails(getDocs(collection(db('stranger'), 'chats', thread, 'messages')));
  await assertFails(setDoc(doc(db('coach1'), 'chats', thread, 'messages', 'm2'), { id: 'm2', senderId: PLAYER, content: 'spoof' }));
});

test('coaches cannot raise their own rating or verification', async () => {
  await seed(async (f) => {
    await setDoc(doc(f, 'users', 'coach1'), { uid: 'coach1', role: 'COACH' });
    await setDoc(doc(f, 'coaches', 'coach1'), { uid: 'coach1', verificationStatus: 'NOT_SUBMITTED', profileStatus: 'DRAFT', rating: 0 });
  });
  const f = db('coach1');
  await assertFails(updateDoc(doc(f, 'coaches', 'coach1'), { rating: 5 }));
  await assertFails(updateDoc(doc(f, 'coaches', 'coach1'), { verificationStatus: 'VERIFIED' }));
  await assertSucceeds(updateDoc(doc(f, 'coaches', 'coach1'), { bio: 'New bio' }));
  await assertSucceeds(getDocs(collection(db(PLAYER), 'coaches')));
});

// ═════════════════════════════════════════════════════════════════════════════
// PLAYER signup + onboarding (players/{uid} public, users/{uid} private)
// ═════════════════════════════════════════════════════════════════════════════
const PLAYER2 = 'player2';

test('PLAYER: signup without Terms/Privacy consent is rejected', async () => {
  await assertFails(setDoc(doc(db(PLAYER), 'users', PLAYER), playerUser(PLAYER, { termsAcceptedAt: null })));
  await assertFails(setDoc(doc(db(PLAYER), 'users', PLAYER), playerUser(PLAYER, { privacyAcceptedAt: null })));
});

test('PLAYER: signup cannot start as COMPLETE or store a password', async () => {
  await assertFails(setDoc(doc(db(PLAYER), 'users', PLAYER), playerUser(PLAYER, { profileStatus: 'COMPLETE' })));
  await assertFails(setDoc(doc(db(PLAYER), 'users', PLAYER), playerUser(PLAYER, { password: 'secret123' })));
});

test('PLAYER: onboarding progress creates players/{uid} and updates users/{uid} in one batch', async () => {
  await seedPlayer(PLAYER);
  const f = db(PLAYER);
  const b = writeBatch(f);
  b.set(doc(f, 'players', PLAYER), playerPublic(PLAYER));
  b.update(doc(f, 'users', PLAYER), { name: 'Asha Patel', dateOfBirth: '2008-03-12', profileStatus: 'INCOMPLETE', updatedAt: serverTimestamp() });
  await assertSucceeds(b.commit());
});

test('PLAYER: completing the profile succeeds only together with players/{uid}', async () => {
  await seedPlayer(PLAYER, { dateOfBirth: '2008-03-12' });
  // status COMPLETE without the public profile: rejected
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER), { profileStatus: 'COMPLETE', updatedAt: serverTimestamp() }));
  const f = db(PLAYER);
  const b = writeBatch(f);
  b.set(doc(f, 'players', PLAYER), playerPublic(PLAYER));
  b.update(doc(f, 'users', PLAYER), { profileStatus: 'COMPLETE', updatedAt: serverTimestamp() });
  await assertSucceeds(b.commit());
});

test('PLAYER: a legacy player without consent cannot complete until consent is stamped', async () => {
  await seedPlayer(PLAYER, { termsAcceptedAt: null, privacyAcceptedAt: null, profileStatus: '' });
  const f = db(PLAYER);
  const noConsent = writeBatch(f);
  noConsent.set(doc(f, 'players', PLAYER), playerPublic(PLAYER));
  noConsent.update(doc(f, 'users', PLAYER), { profileStatus: 'COMPLETE', updatedAt: serverTimestamp() });
  await assertFails(noConsent.commit());
  const withConsent = writeBatch(f);
  withConsent.set(doc(f, 'players', PLAYER), playerPublic(PLAYER));
  withConsent.update(doc(f, 'users', PLAYER), {
    profileStatus: 'COMPLETE', termsAcceptedAt: serverTimestamp(), privacyAcceptedAt: serverTimestamp(), updatedAt: serverTimestamp(),
  });
  await assertSucceeds(withConsent.commit());
});

test('PLAYER: consent timestamps cannot be rewritten once set', async () => {
  await seedPlayer(PLAYER);
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER), { termsAcceptedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER), { privacyAcceptedAt: null }));
});

test('PLAYER: date of birth is locked once the profile is COMPLETE', async () => {
  await seedPlayer(PLAYER, { dateOfBirth: '2010-01-01', profileStatus: 'INCOMPLETE' });
  await assertSucceeds(updateDoc(doc(db(PLAYER), 'users', PLAYER), { dateOfBirth: '2009-01-01' }));
  await seedPlayer(PLAYER2, { dateOfBirth: '2010-01-01', profileStatus: 'COMPLETE' }, true);
  await assertFails(updateDoc(doc(db(PLAYER2), 'users', PLAYER2), { dateOfBirth: '1990-01-01' }));
  await assertSucceeds(updateDoc(doc(db(PLAYER2), 'users', PLAYER2), { phoneNumber: '9876543210' }));
});

test('PLAYER: invalid private values are rejected (bad DOB format, unknown status)', async () => {
  await seedPlayer(PLAYER);
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER), { dateOfBirth: '12/03/2008' }));
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER), { profileStatus: 'VERIFIED' }));
});

test('PLAYER: cannot change own role, nor another user\'s role', async () => {
  await seedPlayer(PLAYER);
  await seedPlayer(PLAYER2);
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER), { role: 'COACH' }));
  await assertFails(updateDoc(doc(db(PLAYER), 'users', PLAYER2), { role: 'COACH' }));
});

test('PLAYER: cannot create or modify another player\'s profile', async () => {
  await seedPlayer(PLAYER);
  await seedPlayer(PLAYER2, {}, true);
  await assertFails(setDoc(doc(db(PLAYER), 'players', PLAYER2), playerPublic(PLAYER2)));
  await assertFails(updateDoc(doc(db(PLAYER), 'players', PLAYER2), { bio: 'hacked', updatedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(db(PLAYER), 'players', PLAYER), playerPublic(PLAYER2))); // uid mismatch
});

test('PLAYER: private data (DOB, email, phone) can never be written to the public profile', async () => {
  await seedPlayer(PLAYER);
  await assertFails(setDoc(doc(db(PLAYER), 'players', PLAYER), playerPublic(PLAYER, { dateOfBirth: '2008-03-12' })));
  await assertFails(setDoc(doc(db(PLAYER), 'players', PLAYER), playerPublic(PLAYER, { email: 'p@x.org' })));
  await assertFails(setDoc(doc(db(PLAYER), 'players', PLAYER), playerPublic(PLAYER, { phoneNumber: '9876543210' })));
});

test('PLAYER: public profile values are bounded (skill level enum, sizes)', async () => {
  await seedPlayer(PLAYER);
  await assertFails(setDoc(doc(db(PLAYER), 'players', PLAYER), playerPublic(PLAYER, { skillLevel: 'GOAT' })));
  await assertFails(setDoc(doc(db(PLAYER), 'players', PLAYER), playerPublic(PLAYER, { bio: 'x'.repeat(501) })));
  await assertFails(setDoc(doc(db(PLAYER), 'players', PLAYER), playerPublic(PLAYER, { secondarySports: ['a', 'b', 'c', 'd', 'e', 'f'] })));
});

test('PLAYER: uid and createdAt of the public profile are immutable', async () => {
  await seedPlayer(PLAYER, {}, true);
  await assertFails(updateDoc(doc(db(PLAYER), 'players', PLAYER), { uid: PLAYER2, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db(PLAYER), 'players', PLAYER), { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(db(PLAYER), 'players', PLAYER), { bio: 'Left-arm spinner', updatedAt: serverTimestamp() }));
});

test('PLAYER: coaches and organisations cannot create a player profile', async () => {
  await seed(async (f) => setDoc(doc(f, 'users', 'coach1'), { uid: 'coach1', role: 'COACH' }));
  await assertFails(setDoc(doc(db('coach1'), 'players', 'coach1'), playerPublic('coach1')));
});

test('PLAYER: profiles are readable by signed-in users only; private users docs stay private', async () => {
  await seedPlayer(PLAYER, { dateOfBirth: '2010-05-05' }, true);
  await seed(async (f) => setDoc(doc(f, 'users', 'coach1'), { uid: 'coach1', role: 'COACH' }));
  await assertSucceeds(getDoc(doc(db('coach1'), 'players', PLAYER)));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'players', PLAYER)));
  await assertFails(getDoc(doc(db('coach1'), 'users', PLAYER))); // DOB / email / phone are never readable by others
  await assertSucceeds(getDoc(doc(db(PLAYER), 'users', PLAYER)));
});

test('PLAYER: coach and organisation signups are unaffected by player consent rules', async () => {
  const coachUser = { uid: 'coach9', name: 'C', email: 'c@x.org', role: 'COACH', organisationId: '', profileStatus: '',
    dateOfBirth: '', phoneNumber: '', gender: '', termsAcceptedAt: null, privacyAcceptedAt: null, notificationPreferences: {} };
  await assertSucceeds(setDoc(doc(db('coach9'), 'users', 'coach9'), coachUser));
});
