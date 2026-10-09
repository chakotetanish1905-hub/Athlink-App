// Pre-verified organisation directory (e.g. the Vadodara / Surat academy landscape scan).
//
// These are real academies that do NOT have an Athlink account. They are written by the admin
// tool (Admin SDK) as `organisations/{id}` with:
//   - an id starting with "dir-"   -> can never equal a Firebase Auth uid, so no account can
//                                      claim, edit or publish as a directory listing (rules
//                                      only allow writes to organisations/{yourOwnUid})
//   - ownerUid ""                   -> unclaimed
//   - listingSource DIRECTORY_IMPORT
//   - VERIFIED / LEVEL_2 with a 12-month expiry, like an admin approval, so `expire-due`
//     flags stale listings for a refresh.
//
// Pure functions only (no Firebase) so they are unit-tested in directory.test.js.

export const LISTING_SOURCE = 'DIRECTORY_IMPORT';
export const ID_PREFIX = 'dir-';

/** Must match `Sports.ALL` in CoachRegistration.kt. */
export const KNOWN_SPORTS = [
  'Cricket', 'Football', 'Badminton', 'Tennis', 'Swimming',
  'Basketball', 'Kabaddi', 'Volleyball', 'Hockey', 'Athletics',
  'Table Tennis', 'Chess', 'Kho Kho', 'Wrestling', 'Boxing',
];

const CITY_DISTRICT = { Vadodara: 'Vadodara', Surat: 'Surat' };

export function slugify(s) {
  return s.toLowerCase().normalize('NFKD').replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

/** Stable document id: same listing -> same id, so re-running the import updates instead of duplicating. */
export function directoryId(entry) {
  return `${ID_PREFIX}${slugify(entry.city)}-${slugify(entry.name)}`.slice(0, 120);
}

/** Section sport first, then any other known sport named in the listing, e.g. "(Tennis & Badminton)". */
export function sportsFor(entry) {
  const primary = entry.section === 'Basketball / Pickleball' ? 'Basketball' : entry.section;
  if (!KNOWN_SPORTS.includes(primary)) throw new Error(`Unknown sport "${entry.section}" for ${entry.name}`);
  const sports = [primary];
  const name = entry.name.toLowerCase();
  for (const s of KNOWN_SPORTS) {
    if (sports.includes(s)) continue;
    // "Table Tennis" must not also count as "Tennis".
    const hay = s === 'Tennis' ? name.replace(/table tennis/g, '') : name;
    if (new RegExp(`\\b${s.toLowerCase()}\\b`).test(hay)) sports.push(s);
  }
  return sports;
}

export function organisationTypeFor(name) {
  if (/\bclub\b/i.test(name)) return 'SPORTS_CLUB';
  if (/\b(complex|arena|court|centre|center|studio|junction|turf)\b/i.test(name)) return 'SPORTS_TRAINING_CENTRE';
  return 'SPORTS_ACADEMY';
}

export function validateEntry(e) {
  const problems = [];
  if (!e.name?.trim()) problems.push('name missing');
  if (!e.locality?.trim()) problems.push('locality missing');
  if (!CITY_DISTRICT[e.city]) problems.push(`unsupported city "${e.city}"`);
  if (!['INDOOR', 'OUTDOOR'].includes(e.venueCategory)) problems.push('venueCategory must be INDOOR or OUTDOOR');
  if (e.googleRating != null && !(e.googleRating >= 0 && e.googleRating <= 5)) problems.push('rating out of range');
  if (e.googleReviewCount != null && !(Number.isInteger(e.googleReviewCount) && e.googleReviewCount >= 0)) problems.push('bad review count');
  if ((e.googleRating == null) !== (e.googleReviewCount == null)) problems.push('rating and review count must both be set or both null');
  if (e.name && e.name.length > 120) problems.push('name longer than 120');
  return problems;
}

/**
 * Public, descriptive fields of the organisations/{id} document (no status fields).
 * Written on create AND on every re-import.
 */
export function listingFields(entry, ratingSource) {
  const sports = sportsFor(entry);
  const publicAddress = `${entry.locality}, ${entry.city}`;
  const sportText = sports.join(' & ');
  return {
    organisationId: directoryId(entry),
    ownerUid: '',
    legalName: entry.name,
    displayName: entry.name,
    organisationType: organisationTypeFor(entry.name),
    sports,
    description: `${sportText} coaching in ${publicAddress}. ${entry.venueCategory === 'INDOOR' ? 'Indoor' : 'Outdoor'} sport. Listed in the Athlink academy directory.`,
    website: '',
    organisationLevel: 'LOCAL',
    country: 'India',
    state: 'Gujarat',
    district: CITY_DISTRICT[entry.city],
    city: entry.city,
    locality: entry.locality,
    publicAddress,
    latitude: null,
    longitude: null,
    yearEstablished: null,
    listingSource: LISTING_SOURCE,
    venueCategory: entry.venueCategory,
    googleRating: entry.googleRating ?? null,
    googleReviewCount: entry.googleReviewCount ?? null,
    ratingSource: entry.googleRating == null ? '' : ratingSource,
  };
}

/** Verification fields for a NEW listing: pre-verified, level 2, expires after `months`. */
export function verifiedFields(now, months = 12) {
  const expires = new Date(now);
  expires.setUTCMonth(expires.getUTCMonth() + months);
  return {
    verificationStatus: 'VERIFIED',
    verificationLevel: 'LEVEL_2_ORGANISATION_VERIFIED',
    verifiedAt: now,
    verificationExpiresAt: expires,
  };
}

/** Validates the whole file: every entry valid and every id unique. Throws with all problems listed. */
export function checkSeed(seed) {
  const list = seed?.organisations;
  if (!Array.isArray(list) || list.length === 0) throw new Error('Seed file has no "organisations" array.');
  const errors = [];
  const seen = new Map();
  list.forEach((e, i) => {
    for (const p of validateEntry(e)) errors.push(`#${i + 1} ${e.name ?? ''}: ${p}`);
    try { sportsFor(e); } catch (err) { errors.push(`#${i + 1}: ${err.message}`); }
    const id = e.name && e.city ? directoryId(e) : null;
    if (id && seen.has(id)) errors.push(`#${i + 1} ${e.name}: duplicate id ${id} (same as #${seen.get(id) + 1})`);
    if (id) seen.set(id, i);
  });
  if (errors.length) throw new Error(`Seed file is invalid:\n  ${errors.join('\n  ')}`);
  return list;
}
