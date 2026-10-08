#!/usr/bin/env node
// Athlink admin tool: the ONLY place organisation verification decisions are made.
// Runs on a trusted machine with a Firebase service-account key (Admin SDK bypasses security
// rules), so the Android app never holds any power to verify anyone.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
//   export ATHLINK_ADMIN=you@example.com          # recorded as reviewedBy / performedBy
//   node admin.js help
//
// Every decision updates organisations/{id} + organisationVerification/{id} and appends a
// verificationAuditLogs entry in ONE transaction.

import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore, FieldValue, Timestamp } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { getStorage } from 'firebase-admin/storage';
import { Status, assertTransition, levelFor, auditActionFor, computeExpiry, isDue } from './policy.js';

const HELP = `
Athlink admin tool: organisation verification

  set-admin <email> [--remove]            Grant / remove the admin custom claim (user must log in again)
  list [--status UNDER_REVIEW]            List organisations (default: UNDER_REVIEW)
  show <orgId> [--links]                  Everything a reviewer needs (+ 15-min private links to documents)
  review-doc <documentId> <VERIFIED|REJECTED|EXPIRED> [--notes "..."]
  approve <orgId> [--official] [--expires YYYY-MM-DD] [--notes "..."]
                                          UNDER_REVIEW -> VERIFIED (or OFFICIAL_GOVERNMENT for government bodies)
  reject <orgId> --reason "..." [--notes "..."]
  suspend <orgId> --reason "..."
  reactivate <orgId> [--official] [--notes "..."]   SUSPENDED -> VERIFIED / OFFICIAL_GOVERNMENT
  expire-due [--dry-run]                  Mark verified organisations whose verification or documents expired
  migrate-legacy [--dry-run]              Create UNVERIFIED organisations/{uid} for older ORGANISATION accounts
`;

// ── Args ────────────────────────────────────────────────────────────────────
const [, , command, ...rest] = process.argv;
const positional = [];
const flags = {};
for (let i = 0; i < rest.length; i++) {
  const a = rest[i];
  if (a.startsWith('--')) {
    const key = a.slice(2);
    const next = rest[i + 1];
    if (next !== undefined && !next.startsWith('--')) { flags[key] = next; i++; } else flags[key] = true;
  } else positional.push(a);
}

if (!command || command === 'help' || command === '--help') { console.log(HELP); process.exit(0); }

initializeApp({ credential: applicationDefault(), storageBucket: process.env.ATHLINK_STORAGE_BUCKET });
const db = getFirestore();
const ADMIN = process.env.ATHLINK_ADMIN || 'admin-tool';

const orgRef = (id) => db.collection('organisations').doc(id);
const verRef = (id) => db.collection('organisationVerification').doc(id);
const fmt = (v) => (v?.toDate ? v.toDate().toISOString() : v ?? '');

function need(value, message) { if (!value) { console.error(message); process.exit(1); } return value; }

async function documentsOf(orgId) {
  const snap = await db.collection('organisationDocuments').where('ownerUid', '==', orgId).get();
  return snap.docs.map((d) => d.data());
}

/** Applies a status decision + audit log atomically. */
async function decide(orgId, to, { reason = '', notes = '', expires = null, checks = null } = {}) {
  const docs = await documentsOf(orgId);
  await db.runTransaction(async (tx) => {
    const orgSnap = await tx.get(orgRef(orgId));
    if (!orgSnap.exists) throw new Error(`No organisation ${orgId}`);
    const org = orgSnap.data();
    const from = org.verificationStatus;
    assertTransition(from, to, org.organisationType);

    const now = Timestamp.now();
    const level = levelFor(to);
    const orgUpdate = { verificationStatus: to, verificationLevel: level, updatedAt: FieldValue.serverTimestamp() };
    const verUpdate = {
      status: to, verificationLevel: level, reviewedAt: FieldValue.serverTimestamp(), reviewedBy: ADMIN,
      reviewNotes: notes || null, updatedAt: FieldValue.serverTimestamp(),
    };
    if (to === Status.VERIFIED || to === Status.OFFICIAL_GOVERNMENT) {
      orgUpdate.verifiedAt = FieldValue.serverTimestamp();
      orgUpdate.verificationExpiresAt = Timestamp.fromDate(computeExpiry(now.toDate(), docs.filter((d) => d.verificationStatus !== 'REJECTED'), 12, expires));
      Object.assign(verUpdate, checks ?? {
        contactVerified: true, legalEntityVerified: true, representativeVerified: true,
        addressVerified: true, sportsCredentialVerified: true,
      });
      verUpdate.rejectionReason = null;
    }
    if (to === Status.REJECTED || to === Status.SUSPENDED) {
      verUpdate.rejectionReason = need(reason, `--reason is required to ${to.toLowerCase()} an organisation`);
    }
    tx.update(orgRef(orgId), orgUpdate);
    tx.set(verRef(orgId), verUpdate, { merge: true });
    const log = db.collection('verificationAuditLogs').doc();
    tx.set(log, {
      logId: log.id, organisationId: orgId, action: auditActionFor(from, to), previousStatus: from, newStatus: to,
      performedBy: ADMIN, performedAt: FieldValue.serverTimestamp(), reason, notes, source: 'ADMIN',
    });
    console.log(`${orgId}: ${from} -> ${to} (${level})`);
  });
}

// ── Commands ────────────────────────────────────────────────────────────────
const commands = {
  async 'set-admin'() {
    const email = need(positional[0], 'Usage: set-admin <email> [--remove]');
    const user = await getAuth().getUserByEmail(email);
    const claims = { ...(user.customClaims ?? {}) };
    if (flags.remove) delete claims.admin; else claims.admin = true;
    await getAuth().setCustomUserClaims(user.uid, claims);
    console.log(`${email} admin=${!flags.remove}. They must log out and back in.`);
  },

  async list() {
    const status = flags.status || Status.UNDER_REVIEW;
    const snap = await db.collection('organisations').where('verificationStatus', '==', status).get();
    if (snap.empty) { console.log(`No organisations with status ${status}.`); return; }
    for (const d of snap.docs) {
      const o = d.data();
      console.log(`${d.id}\t${o.displayName}\t${o.organisationType}\t${o.city}, ${o.state}\tupdated ${fmt(o.updatedAt)}`);
    }
  },

  async show() {
    const id = need(positional[0], 'Usage: show <orgId> [--links]');
    const [org, ver, rep, aff] = await Promise.all([
      orgRef(id).get(), verRef(id).get(),
      db.collection('organisationRepresentatives').doc(id).get(),
      db.collection('organisationAffiliations').doc(id).get(),
    ]);
    const docs = await documentsOf(id);
    const logs = await db.collection('verificationAuditLogs').where('organisationId', '==', id).orderBy('performedAt', 'desc').limit(30).get();
    const print = (title, snap) => { console.log(`\n== ${title}`); console.log(snap.exists ? JSON.stringify(snap.data(), (k, v) => (v?._seconds ? new Date(v._seconds * 1000).toISOString() : v), 2) : '(none)'); };
    print('Organisation (public)', org);
    print('Verification (private)', ver);
    print('Representative', rep);
    print('Affiliation', aff);
    console.log('\n== Documents');
    for (const d of docs) {
      let link = '';
      if (flags.links) {
        const [url] = await getStorage().bucket().file(d.storagePath).getSignedUrl({ action: 'read', expires: Date.now() + 15 * 60 * 1000 });
        link = `\n    ${url}`;
      }
      console.log(`  ${d.documentId}  ${d.documentType}  ${d.verificationStatus}  ${d.fileName}  expires=${d.expiryDate ?? '-'}${link}`);
    }
    console.log('\n== Audit history');
    for (const l of logs.docs.map((x) => x.data())) {
      console.log(`  ${fmt(l.performedAt)}  ${l.action}  ${l.previousStatus} -> ${l.newStatus}  by ${l.performedBy} (${l.source}) ${l.reason}`);
    }
    if (ver.exists && /@(gmail|yahoo|outlook|hotmail|rediffmail)\./i.test(ver.data().officialEmail ?? '') && String(org.data()?.organisationType).startsWith('GOVERNMENT')) {
      console.log('\n!! Government application using a free email domain: cross-check with the official website / phone before granting OFFICIAL_GOVERNMENT.');
    }
  },

  async 'review-doc'() {
    const [docId, status] = positional;
    need(docId && ['VERIFIED', 'REJECTED', 'EXPIRED'].includes(status), 'Usage: review-doc <documentId> <VERIFIED|REJECTED|EXPIRED> [--notes]');
    const ref = db.collection('organisationDocuments').doc(docId);
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) throw new Error(`No document ${docId}`);
      const d = snap.data();
      tx.update(ref, { verificationStatus: status, reviewedAt: FieldValue.serverTimestamp(), reviewedBy: ADMIN, reviewNotes: flags.notes || null });
      const log = db.collection('verificationAuditLogs').doc();
      tx.set(log, {
        logId: log.id, organisationId: d.organisationId, action: 'DOCUMENT_REVIEWED', previousStatus: d.verificationStatus,
        newStatus: status, performedBy: ADMIN, performedAt: FieldValue.serverTimestamp(), reason: '', notes: `${docId} ${flags.notes ?? ''}`.trim(), source: 'ADMIN',
      });
    });
    console.log(`${docId}: ${status}`);
  },

  async approve() {
    const id = need(positional[0], 'Usage: approve <orgId> [--official] [--expires YYYY-MM-DD]');
    const docs = await documentsOf(id);
    const rejected = docs.filter((d) => d.verificationStatus === 'REJECTED');
    if (rejected.length && !flags.force) {
      throw new Error(`Rejected documents present (${rejected.map((d) => d.documentId).join(', ')}). Ask for replacements, or use --force.`);
    }
    // The reviewer has checked the pending documents; record that explicitly.
    const batch = db.batch();
    for (const d of docs.filter((x) => x.verificationStatus === 'PENDING_MANUAL_REVIEW')) {
      batch.update(db.collection('organisationDocuments').doc(d.documentId), { verificationStatus: 'VERIFIED', reviewedAt: FieldValue.serverTimestamp(), reviewedBy: ADMIN });
    }
    await batch.commit();
    await decide(id, flags.official ? Status.OFFICIAL_GOVERNMENT : Status.VERIFIED, { notes: flags.notes || '', expires: flags.expires || null });
  },

  async reject() {
    const id = need(positional[0], 'Usage: reject <orgId> --reason "..."');
    await decide(id, Status.REJECTED, { reason: need(flags.reason, '--reason is required'), notes: flags.notes || '' });
  },

  async suspend() {
    const id = need(positional[0], 'Usage: suspend <orgId> --reason "..."');
    await decide(id, Status.SUSPENDED, { reason: need(flags.reason, '--reason is required'), notes: flags.notes || '' });
  },

  async reactivate() {
    const id = need(positional[0], 'Usage: reactivate <orgId> [--official]');
    await decide(id, flags.official ? Status.OFFICIAL_GOVERNMENT : Status.VERIFIED, { notes: flags.notes || 'Reactivated' });
  },

  async 'expire-due'() {
    const now = new Date();
    for (const status of [Status.VERIFIED, Status.OFFICIAL_GOVERNMENT]) {
      const snap = await db.collection('organisations').where('verificationStatus', '==', status).get();
      for (const d of snap.docs) {
        const docs = await documentsOf(d.id);
        if (!isDue(d.data(), docs, now)) continue;
        if (flags['dry-run']) { console.log(`would expire ${d.id} (${d.data().displayName})`); continue; }
        await decide(d.id, Status.EXPIRED, { reason: 'Verification or a verified credential expired', notes: 'expire-due' });
      }
    }
  },

  async 'migrate-legacy'() {
    const users = await db.collection('users').where('role', '==', 'ORGANISATION').get();
    let created = 0;
    for (const u of users.docs) {
      const uid = u.id;
      const org = await orgRef(uid).get();
      if (org.exists) continue;
      created++;
      if (flags['dry-run']) { console.log(`would create organisations/${uid} for ${u.data().email}`); continue; }
      const batch = db.batch();
      batch.set(orgRef(uid), {
        organisationId: uid, ownerUid: uid, legalName: '', displayName: u.data().name ?? '', organisationType: '', sports: [],
        description: '', logoUrl: '', website: '', organisationLevel: '', country: '', state: '', district: '', city: '',
        locality: '', publicAddress: '', latitude: null, longitude: null, yearEstablished: null,
        verificationStatus: Status.UNVERIFIED, verificationLevel: 'LEVEL_0_UNVERIFIED', verifiedAt: null, verificationExpiresAt: null,
        createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      });
      batch.update(u.ref, { organisationId: uid });
      await batch.commit();
      console.log(`created organisations/${uid} (UNVERIFIED) for ${u.data().email}`);
    }
    console.log(`${created} legacy organisation account(s) ${flags['dry-run'] ? 'found' : 'migrated'}.`);
  },
};

const run = commands[command];
if (!run) { console.error(`Unknown command "${command}".\n${HELP}`); process.exit(1); }
run().then(() => process.exit(0)).catch((e) => { console.error(`Error: ${e.message}`); process.exit(1); });
