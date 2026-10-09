import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  checkSeed, directoryId, sportsFor, organisationTypeFor, listingFields, verifiedFields, validateEntry, ID_PREFIX,
} from './directory.js';

const seed = JSON.parse(readFileSync(new URL('./seed/vadodara-surat-academies.json', import.meta.url), 'utf8'));
const entry = (o = {}) => ({
  city: 'Vadodara', section: 'Badminton', venueCategory: 'INDOOR', name: 'Arjun Badminton Academy',
  locality: 'Tandalja', googleRating: 5.0, googleReviewCount: 34, ...o,
});

test('the Vadodara / Surat seed file is valid and matches the landscape scan totals', () => {
  const list = checkSeed(seed);
  assert.equal(list.length, 96);
  const count = (city, cat) => list.filter((e) => e.city === city && e.venueCategory === cat).length;
  assert.equal(count('Vadodara', 'INDOOR'), 21);
  assert.equal(count('Vadodara', 'OUTDOOR'), 38);
  assert.equal(count('Surat', 'INDOOR'), 15);
  assert.equal(count('Surat', 'OUTDOOR'), 22);
  assert.equal(new Set(list.map(directoryId)).size, 96, 'ids are unique');
});

test('ids are stable, prefixed and can never be an Auth uid', () => {
  assert.equal(directoryId(entry()), 'dir-vadodara-arjun-badminton-academy');
  assert.equal(directoryId(entry()), directoryId(entry({ googleRating: 4.0 })));
  assert.ok(directoryId(entry({ name: 'Blackk&One Sports Foundation' })).startsWith(ID_PREFIX));
  assert.match(directoryId(entry({ name: 'Ravindra\'s Lawn Tennis Academy' })), /^dir-[a-z0-9-]+$/);
});

test('sports: section first, extra sports from the name, Table Tennis is not Tennis', () => {
  assert.deepEqual(sportsFor(entry({ name: 'Nurture Sports Academy (Tennis & Badminton)' })), ['Badminton', 'Tennis']);
  assert.deepEqual(sportsFor(entry({ section: 'Table Tennis', name: 'Starlet Table Tennis Academy' })), ['Table Tennis']);
  assert.deepEqual(sportsFor(entry({ section: 'Basketball / Pickleball', name: 'BallersZone Academy' })), ['Basketball']);
  assert.throws(() => sportsFor(entry({ section: 'Padel' })), /Unknown sport/);
});

test('organisation type from the listing name', () => {
  assert.equal(organisationTypeFor('Kickjack Football Club'), 'SPORTS_CLUB');
  assert.equal(organisationTypeFor('Akota Sports Complex'), 'SPORTS_TRAINING_CENTRE');
  assert.equal(organisationTypeFor('MDK Cricket Academy'), 'SPORTS_ACADEMY');
});

test('listing fields: public data only, unclaimed, rating snapshot kept', () => {
  const f = listingFields(entry(), 'src');
  assert.equal(f.ownerUid, '');
  assert.equal(f.listingSource, 'DIRECTORY_IMPORT');
  assert.equal(f.publicAddress, 'Tandalja, Vadodara');
  assert.equal(f.state, 'Gujarat');
  assert.equal(f.googleRating, 5.0);
  assert.equal(f.googleReviewCount, 34);
  assert.equal(f.ratingSource, 'src');
  assert.ok(f.description.length <= 600);
  assert.ok(!('verificationStatus' in f), 'status fields are never part of a refresh');
  const unrated = listingFields(entry({ googleRating: null, googleReviewCount: null }), 'src');
  assert.equal(unrated.googleRating, null);
  assert.equal(unrated.ratingSource, '');
});

test('new listings are VERIFIED level 2 for 12 months', () => {
  const now = new Date('2026-10-09T00:00:00Z');
  const v = verifiedFields(now);
  assert.equal(v.verificationStatus, 'VERIFIED');
  assert.equal(v.verificationLevel, 'LEVEL_2_ORGANISATION_VERIFIED');
  assert.equal(v.verificationExpiresAt.toISOString(), '2027-10-09T00:00:00.000Z');
});

test('invalid entries and duplicates are rejected with a readable list', () => {
  assert.deepEqual(validateEntry(entry()), []);
  assert.ok(validateEntry(entry({ city: 'Pune' })).length > 0);
  assert.ok(validateEntry(entry({ googleRating: 6 })).length > 0);
  assert.ok(validateEntry(entry({ googleReviewCount: null })).length > 0);
  assert.throws(() => checkSeed({ organisations: [entry(), entry()] }), /duplicate id/);
  assert.throws(() => checkSeed({}), /no "organisations"/);
});
