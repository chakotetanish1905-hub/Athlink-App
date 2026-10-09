import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { groupCoaches, coachFields, newCoachStatus, coachId, COACH_ID_PREFIX } from './coaches.js';
import { directoryId } from './directory.js';

const read = (f) => JSON.parse(readFileSync(new URL(f, import.meta.url), 'utf8'));
const academies = read('./seed/vadodara-surat-academies.json').organisations;
const rows = read('./seed/vadodara-surat-coaches.json').coaches;

test('the coach list maps to 20 coaches, each linked to existing academies', () => {
  assert.equal(rows.length, 32);
  const coaches = groupCoaches(rows, academies);
  assert.equal(coaches.length, 20);
  const academyIds = new Set(academies.map(directoryId));
  for (const c of coaches) {
    assert.ok(c.id.startsWith(COACH_ID_PREFIX));
    assert.ok(c.academies.length >= 1);
    for (const a of c.academies) assert.ok(academyIds.has(a.id), `${c.name}: ${a.id}`);
  }
});

test('the same coach at several centres is one profile; same name + other sport is another coach', () => {
  const coaches = groupCoaches(rows, academies);
  const vimal = coaches.find((c) => c.name === 'Vimal Upadhyay');
  assert.equal(vimal.academies.length, 5);
  const sunils = coaches.filter((c) => c.name === 'Sunil Sir').map((c) => c.sport).sort();
  assert.deepEqual(sunils, ['Cricket', 'Tennis']);
  assert.equal(coaches.find((c) => c.name === 'Balpreet Singh').academies[0].city, 'Surat');
});

test('"Table tennis" in the CSV is stored as the app sport "Table Tennis"', () => {
  const tt = groupCoaches(rows, academies).filter((c) => c.sport === 'Table Tennis');
  assert.equal(tt.length, 4);
});

test('coach fields link academies and never claim checks or ratings', () => {
  const coaches = groupCoaches(rows, academies);
  const vimal = coachFields(coaches.find((c) => c.name === 'Vimal Upadhyay'));
  assert.equal(vimal.academyIds.length, 5);
  assert.equal(vimal.location, '5 centres, Vadodara');
  assert.equal(vimal.listingSource, 'DIRECTORY_IMPORT');
  assert.ok(!('verificationStatus' in vimal), 'status is not part of a refresh');
  const s = newCoachStatus();
  assert.equal(s.verificationStatus, 'VERIFIED');
  assert.equal(s.profileStatus, 'ACTIVE');
  assert.equal(s.identityStatus, 'NOT_SUBMITTED');
  assert.equal(s.rating, 0);
});

test('bad rows are rejected with a readable list', () => {
  assert.throws(() => groupCoaches([{ coachName: 'X', sport: 'Polo', academyName: 'MDK Cricket Academy', city: 'Vadodara' }], academies), /unknown sport/);
  assert.throws(() => groupCoaches([{ coachName: 'X', sport: 'Cricket', academyName: 'Nowhere', city: 'Vadodara' }], academies), /not in the academy seed/);
  const dup = { coachName: 'X', sport: 'Cricket', academyName: 'MDK Cricket Academy', city: 'Vadodara' };
  assert.throws(() => groupCoaches([dup, dup], academies), /listed twice/);
  assert.equal(coachId({ coachName: 'Mr. Thomas', sport: 'Badminton' }), 'dircoach-mr-thomas-badminton');
});
