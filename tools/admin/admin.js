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
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { join, basename, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Status, assertTransition, levelFor, auditActionFor, computeExpiry, isDue } from './policy.js';
import { checkSeed, directoryId, listingFields, verifiedFields, LISTING_SOURCE, ID_PREFIX } from './directory.js';

const HELP = `
Athlink admin tool: organisation verification

  set-admin <email> [--remove]            Grant / remove the admin custom claim (user must log in again)
  list [--status UNDER_REVIEW]            List organisations (default: UNDER_REVIEW)
  show <orgId> [--download <dir>]         Everything a reviewer needs; --download saves the documents to <dir>
                                          (--links: 15-min links, only for files kept in Cloud Storage)
  review-doc <documentId> <VERIFIED|REJECTED|EXPIRED> [--notes "..."]
  approve <orgId> [--official] [--expires YYYY-MM-DD] [--notes "..."]
                                          UNDER_REVIEW -> VERIFIED (or OFFICIAL_GOVERNMENT for government bodies)
  reject <orgId> --reason "..." [--notes "..."]
  suspend <orgId> --reason "..."
  reactivate <orgId> [--official] [--notes "..."]   SUSPENDED -> VERIFIED / OFFICIAL_GOVERNMENT
  expire-due [--dry-run]                  Mark verified organisations whose verification or documents expired
  migrate-legacy [--dry-run]              Create UNVERIFIED organisations/{uid} for older ORGANISATION accounts
  seed-directory [--file <json>] [--dry-run]
                                          Import pre-verified academies (no Athlink account) as organisations/dir-*.
                                          Default file: seed/vadodara-surat-academies.json. Safe to re-run: existing
                                          listings get refreshed details/ratings but keep their verification status.
  unseed-directory [--dry-run]            Delete every organisations/dir-* listing (and nothing else)
  requests [--status PENDING] [--all]     Player session requests to directory academies (no owner account);
                                          --all includes academies that answer in the app
  answer-request <requestId> <ACCEPTED|DECLINED> --note "..."
                                          Record the academy's answer (after calling it) for a directory academy
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

/** Reassembles a document stored as organisationDocuments/{id}/chunks/{index} (Spark plan, no Cloud Storage). */
async function readChunkedFile(d) {
  const snap = await db.collection('organisationDocuments').doc(d.documentId).collection('chunks').get();
  const parts = snap.docs.map((c) => c.data()).sort((a, b) => a.index - b.index);
  if (parts.length !== d.chunkCount || parts.some((p, i) => p.index !== i)) {
    throw new Error(`Document ${d.documentId} is incomplete (${parts.length}/${d.chunkCount} chunks)`);
  }
  return Buffer.concat(parts.map((p) => Buffer.from(p.data)));
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
    const id = need(positional[0], 'Usage: show <orgId> [--download <dir>] [--links]');
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
      if (flags.download && d.storageBackend === 'FIRESTORE') {
        const dir = typeof flags.download === 'string' ? flags.download : `./${id}-documents`;
        mkdirSync(dir, { recursive: true });
        const out = join(dir, `${d.documentId}-${basename(d.fileName || 'document')}`);
        writeFileSync(out, await readChunkedFile(d));
        link = `\n    saved ${out}`;
      } else if (flags.links && d.storageBackend !== 'FIRESTORE') {
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
  async 'seed-directory'() {
    const here = dirname(fileURLToPath(import.meta.url));
    const file = flags.file || join(here, 'seed', 'vadodara-surat-academies.json');
    const seed = JSON.parse(readFileSync(file, 'utf8'));
    const entries = checkSeed(seed);
    const ratingSource = seed.source ? `Google rating snapshot via ${seed.source}` : 'Google rating snapshot';
    const now = new Date();
    let created = 0, updated = 0, skipped = 0;
    let batch = db.batch(), ops = 0;
    for (const e of entries) {
      const id = directoryId(e);
      const ref = orgRef(id);
      const snap = await ref.get();
      if (snap.exists && snap.data().listingSource !== LISTING_SOURCE) {
        console.warn(`skip ${id}: exists and is not a directory listing`); skipped++; continue;
      }
      const fields = listingFields(e, ratingSource);
      if (snap.exists) {
        updated++;
        if (!flags['dry-run']) { batch.update(ref, { ...fields, updatedAt: FieldValue.serverTimestamp() }); ops++; }
      } else {
        created++;
        if (flags['dry-run']) { console.log(`would create ${id}\t${e.name}\t${fields.sports.join('/')}\t${fields.publicAddress}`); continue; }
        batch.set(ref, {
          ...fields, logoUrl: '', ...verifiedFields(now),
          createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
        });
        const log = db.collection('verificationAuditLogs').doc();
        batch.set(log, {
          logId: log.id, organisationId: id, action: 'APPROVED', previousStatus: null, newStatus: Status.VERIFIED,
          performedBy: ADMIN, performedAt: FieldValue.serverTimestamp(),
          reason: 'Pre-verified directory listing', notes: seed.title ?? basename(file), source: 'ADMIN',
        });
        ops += 2;
      }
      if (ops >= 400) { await batch.commit(); batch = db.batch(); ops = 0; }
    }
    if (ops > 0) await batch.commit();
    const verb = flags['dry-run'] ? 'would be' : 'were';
    console.log(`${entries.length} listings in ${basename(file)}: ${created} ${verb} created, ${updated} ${verb} refreshed, ${skipped} skipped.`);
  },

  async 'unseed-directory'() {
    const snap = await db.collection('organisations').where('listingSource', '==', LISTING_SOURCE).get();
    const docs = snap.docs.filter((d) => d.id.startsWith(ID_PREFIX));
    if (flags['dry-run']) { docs.forEach((d) => console.log(`would delete ${d.id}\t${d.data().displayName}`)); }
    else {
      for (let i = 0; i < docs.length; i += 400) {
        const batch = db.batch();
        docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
        await batch.commit();
      }
    }
    console.log(`${docs.length} directory listing(s) ${flags['dry-run'] ? 'would be' : ''} deleted.`.replace('  ', ' '));
  },
  async requests() {
    const status = flags.status || 'PENDING';
    let q = db.collection('academyRequests').where('status', '==', status);
    if (!flags.all) q = q.where('organisationOwnerUid', '==', '');
    const snap = await q.get();
    if (snap.empty) { console.log(`No ${status} requests${flags.all ? '' : ' to directory academies'}.`); return; }
    const rows = snap.docs.map((d) => d.data()).sort((a, b) => fmt(a.createdAt).localeCompare(fmt(b.createdAt)));
    for (const r of rows) {
      console.log([
        r.requestId, r.organisationName, r.organisationLocation, r.sport, `${r.preferredDate} ${r.preferredTime}`,
        `player: ${r.playerName}${r.contactPhone ? ` (${r.contactPhone})` : ''}`, r.message ? `"${r.message}"` : '', `sent ${fmt(r.createdAt)}`,
      ].filter(Boolean).join('\t'));
    }
    console.log(`${rows.length} request(s).`);
  },

  async 'answer-request'() {
    const id = need(positional[0], 'Usage: answer-request <requestId> <ACCEPTED|DECLINED> --note "..."');
    const status = need(positional[1], 'Give ACCEPTED or DECLINED');
    if (!['ACCEPTED', 'DECLINED'].includes(status)) { console.error('Status must be ACCEPTED or DECLINED'); process.exit(1); }
    const note = need(typeof flags.note === 'string' ? flags.note.trim() : '', '--note is required (what the academy said: time, venue, fees...)');
    if (note.length > 300) { console.error('--note must be 300 characters or fewer'); process.exit(1); }
    const ref = db.collection('academyRequests').doc(id);
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) throw new Error(`No request ${id}`);
      const r = snap.data();
      if (r.status !== 'PENDING') throw new Error(`Request is ${r.status}, not PENDING`);
      if (r.organisationOwnerUid) throw new Error('This academy has an Athlink account and answers in the app.');
      tx.update(ref, { status, responseNote: note, updatedAt: FieldValue.serverTimestamp(), answeredBy: ADMIN });
    });
    console.log(`${id}: ${status}`);
  },
};

const run = commands[command];
if (!run) { console.error(`Unknown command "${command}".\n${HELP}`); process.exit(1); }
run().then(() => process.exit(0)).catch((e) => { console.error(`Error: ${e.message}`); process.exit(1); });
