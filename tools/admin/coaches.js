// Pre-verified coaches of the directory academies (coach list for Vadodara / Surat).
//
// Like the academies, these coaches have NO Athlink account. The admin tool writes them as
// `coaches/dircoach-*` with:
//   - an id starting with "dircoach-" -> can never equal a Firebase Auth uid, so nobody can sign in
//                                         as, edit or answer for them (rules only allow
//                                         coaches/{yourOwnUid})
//   - profileStatus ACTIVE + verificationStatus VERIFIED  -> listed and bookable by players
//   - every verification CHECK left NOT_SUBMITTED / level 0 -> the app shows no "ID verified" /
//     "Coach verified" badges, because Athlink has not checked their documents
//   - academyIds -> the academies (organisations/dir-*) where they coach
//   - listingSource DIRECTORY_IMPORT
// Players request a session with them through the academy (academyRequests with coachId).
//
// The same coach (name + sport) listed at several academies becomes ONE profile linked to all
// of them; the same name with a different sport is a different coach.

import { slugify, directoryId, KNOWN_SPORTS, LISTING_SOURCE } from './directory.js';

export const COACH_ID_PREFIX = 'dircoach-';

export function coachKey(row) {
  return `${slugify(row.coachName)}|${row.sport}`;
}

export function coachId(row) {
  return `${COACH_ID_PREFIX}${slugify(row.coachName)}-${slugify(row.sport)}`.slice(0, 120);
}

/**
 * Groups seed rows into coach profiles and checks every academy exists in the academy seed.
 * `academies` is the academy seed list (vadodara-surat-academies.json `organisations`).
 * Returns [{ id, name, sport, academies: [{ id, name, city, locality }] }], sorted by id.
 */
export function groupCoaches(rows, academies) {
  if (!Array.isArray(rows) || rows.length === 0) throw new Error('Coach seed has no "coaches" array.');
  const errors = [];
  const byKey = new Map();
  rows.forEach((r, i) => {
    const where = `#${i + 1} ${r.coachName ?? ''}`;
    if (!r.coachName?.trim()) { errors.push(`${where}: coach name missing`); return; }
    if (r.coachName.length > 60) errors.push(`${where}: name longer than 60`);
    if (!KNOWN_SPORTS.includes(r.sport)) { errors.push(`${where}: unknown sport "${r.sport}"`); return; }
    const academy = academies.find((a) => a.name === r.academyName && a.city === r.city);
    if (!academy) { errors.push(`${where}: academy "${r.academyName}" (${r.city}) is not in the academy seed`); return; }
    const key = coachKey(r);
    const coach = byKey.get(key) ?? { id: coachId(r), name: r.coachName.trim(), sport: r.sport, academies: [] };
    const aid = directoryId(academy);
    if (coach.academies.some((a) => a.id === aid)) errors.push(`${where}: listed twice at ${academy.name}`);
    else coach.academies.push({ id: aid, name: academy.name, city: academy.city, locality: academy.locality });
    byKey.set(key, coach);
  });
  if (errors.length) throw new Error(`Coach seed is invalid:\n  ${errors.join('\n  ')}`);
  const coaches = [...byKey.values()].sort((a, b) => a.id.localeCompare(b.id));
  const ids = new Set(coaches.map((c) => c.id));
  if (ids.size !== coaches.length) throw new Error('Two different coaches map to the same id.');
  return coaches;
}

/** Public coach document fields (no status fields). Written on create and on every re-import. */
export function coachFields(coach) {
  const first = coach.academies[0];
  const cities = [...new Set(coach.academies.map((a) => a.city))];
  const many = coach.academies.length > 1;
  return {
    uid: coach.id,
    name: coach.name,
    sport: coach.sport,
    bio: many
      ? `${coach.sport} coach at ${coach.academies.length} centres in ${cities.join(' & ')}. Request a session and choose the centre that suits you.`
      : `${coach.sport} coach at ${first.name}, ${first.locality}, ${first.city}.`,
    currentOrganisation: first.name,
    academyIds: coach.academies.map((a) => a.id),
    academyNames: coach.academies.map((a) => a.name),
    city: first.city,
    state: 'Gujarat',
    country: 'India',
    coachingArea: many ? '' : first.locality,
    location: many ? `${coach.academies.length} centres, ${cities.join(' & ')}` : `${first.locality}, ${first.city}`,
    listingSource: LISTING_SOURCE,
    hourlyRate: 0,
  };
}

/** Status / stats fields for a NEW listing. Checks stay NOT_SUBMITTED, so no badges are shown. */
export function newCoachStatus() {
  return {
    profileStatus: 'ACTIVE',
    verificationStatus: 'VERIFIED',
    verificationLevel: 'LEVEL_0_REGISTERED',
    identityStatus: 'NOT_SUBMITTED',
    qualificationStatus: 'NOT_SUBMITTED',
    experienceStatus: 'NOT_SUBMITTED',
    safeguardingStatus: 'NOT_SUBMITTED',
    rating: 0,
    reviewCount: 0,
    totalEarnings: 0,
    available: true,
    experience: 0,
  };
}
