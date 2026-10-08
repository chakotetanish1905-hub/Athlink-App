import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Status, Level, adminMayTransition, assertTransition, levelFor, auditActionFor, computeExpiry, isDue } from './policy.js';

test('admin transitions mirror the app policy', () => {
  assert.ok(adminMayTransition(Status.UNDER_REVIEW, Status.VERIFIED));
  assert.ok(adminMayTransition(Status.UNDER_REVIEW, Status.OFFICIAL_GOVERNMENT));
  assert.ok(adminMayTransition(Status.UNDER_REVIEW, Status.REJECTED));
  assert.ok(adminMayTransition(Status.VERIFIED, Status.SUSPENDED));
  assert.ok(adminMayTransition(Status.SUSPENDED, Status.VERIFIED));
  assert.ok(!adminMayTransition(Status.UNVERIFIED, Status.VERIFIED));
  assert.ok(!adminMayTransition(Status.REJECTED, Status.VERIFIED));
  assert.ok(!adminMayTransition(Status.CONTACT_VERIFIED, Status.VERIFIED));
});

test('official government only for government types', () => {
  assert.throws(() => assertTransition(Status.UNDER_REVIEW, Status.OFFICIAL_GOVERNMENT, 'SPORTS_ACADEMY'));
  assert.throws(() => assertTransition(Status.UNDER_REVIEW, Status.OFFICIAL_GOVERNMENT, 'NATIONAL_SPORTS_FEDERATION'));
  assert.doesNotThrow(() => assertTransition(Status.UNDER_REVIEW, Status.OFFICIAL_GOVERNMENT, 'GOVERNMENT_SPORTS_AUTHORITY'));
  assert.throws(() => assertTransition(Status.UNVERIFIED, Status.VERIFIED, 'SPORTS_ACADEMY'));
});

test('levels and audit actions', () => {
  assert.equal(levelFor(Status.VERIFIED), Level.L2);
  assert.equal(levelFor(Status.OFFICIAL_GOVERNMENT), Level.L3);
  assert.equal(levelFor(Status.REJECTED), Level.L1);
  assert.equal(auditActionFor(Status.UNDER_REVIEW, Status.VERIFIED), 'APPROVED');
  assert.equal(auditActionFor(Status.SUSPENDED, Status.VERIFIED), 'REACTIVATED');
  assert.equal(auditActionFor(Status.VERIFIED, Status.EXPIRED), 'EXPIRED');
});

test('expiry is capped by the earliest document expiry', () => {
  const now = new Date('2026-10-08T00:00:00Z');
  assert.equal(computeExpiry(now, []).toISOString().slice(0, 10), '2027-10-08');
  assert.equal(computeExpiry(now, [{ expiryDate: '2027-03-31' }, { expiryDate: null }]).toISOString().slice(0, 10), '2027-03-31');
  assert.equal(computeExpiry(now, [], 12, '2026-12-31').toISOString().slice(0, 10), '2026-12-31');
});

test('due for expiry', () => {
  const now = new Date('2026-10-08T00:00:00Z');
  assert.ok(isDue({ verificationStatus: 'VERIFIED', verificationExpiresAt: new Date('2026-10-01') }, [], now));
  assert.ok(!isDue({ verificationStatus: 'VERIFIED', verificationExpiresAt: new Date('2027-10-01') }, [], now));
  assert.ok(isDue({ verificationStatus: 'VERIFIED' }, [{ verificationStatus: 'VERIFIED', expiryDate: '2026-01-01' }], now));
  assert.ok(!isDue({ verificationStatus: 'UNDER_REVIEW', verificationExpiresAt: new Date('2020-01-01') }, [], now));
});
