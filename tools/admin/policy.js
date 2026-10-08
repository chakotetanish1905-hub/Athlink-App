// Organisation verification policy for the admin tool.
// MUST stay in sync with app/.../OrganisationVerificationPolicy.kt and firestore.rules.

export const Status = Object.freeze({
  UNVERIFIED: 'UNVERIFIED',
  CONTACT_VERIFIED: 'CONTACT_VERIFIED',
  UNDER_REVIEW: 'UNDER_REVIEW',
  VERIFIED: 'VERIFIED',
  OFFICIAL_GOVERNMENT: 'OFFICIAL_GOVERNMENT',
  REJECTED: 'REJECTED',
  SUSPENDED: 'SUSPENDED',
  EXPIRED: 'EXPIRED',
});

export const Level = Object.freeze({
  L0: 'LEVEL_0_UNVERIFIED',
  L1: 'LEVEL_1_CONTACT_VERIFIED',
  L2: 'LEVEL_2_ORGANISATION_VERIFIED',
  L3: 'LEVEL_3_OFFICIAL_GOVERNMENT',
});

export const GOVERNMENT_TYPES = new Set([
  'GOVERNMENT_SPORTS_DEPARTMENT', 'GOVERNMENT_SPORTS_AUTHORITY', 'MUNICIPAL_SPORTS_BODY',
]);

const ADMIN_TRANSITIONS = {
  [Status.UNDER_REVIEW]: [Status.VERIFIED, Status.OFFICIAL_GOVERNMENT, Status.REJECTED],
  [Status.VERIFIED]: [Status.SUSPENDED, Status.EXPIRED],
  [Status.OFFICIAL_GOVERNMENT]: [Status.SUSPENDED, Status.EXPIRED],
  [Status.SUSPENDED]: [Status.VERIFIED, Status.OFFICIAL_GOVERNMENT, Status.REJECTED],
  [Status.EXPIRED]: [Status.SUSPENDED],
};

export function adminMayTransition(from, to) {
  return (ADMIN_TRANSITIONS[from] ?? []).includes(to);
}

/** Throws a readable error when a decision is not allowed. */
export function assertTransition(from, to, organisationType) {
  if (!adminMayTransition(from, to)) {
    throw new Error(`Not allowed: ${from} -> ${to}. Allowed from ${from}: ${(ADMIN_TRANSITIONS[from] ?? []).join(', ') || 'none'}`);
  }
  if (to === Status.OFFICIAL_GOVERNMENT && !GOVERNMENT_TYPES.has(organisationType)) {
    throw new Error(`OFFICIAL_GOVERNMENT can only be granted to government organisation types (this one is ${organisationType || 'unset'}).`);
  }
}

export function levelFor(status) {
  switch (status) {
    case Status.OFFICIAL_GOVERNMENT: return Level.L3;
    case Status.VERIFIED: return Level.L2;
    default: return Level.L1;
  }
}

/** Audit action name for a status decision. */
export function auditActionFor(from, to) {
  if (to === Status.VERIFIED || to === Status.OFFICIAL_GOVERNMENT) {
    if (from === Status.SUSPENDED) return 'REACTIVATED';
    return 'APPROVED';
  }
  if (to === Status.REJECTED) return 'REJECTED';
  if (to === Status.SUSPENDED) return 'SUSPENDED';
  if (to === Status.EXPIRED) return 'EXPIRED';
  return 'DOCUMENT_REVIEWED';
}

/**
 * Verification expiry: the earliest of (now + defaultMonths) and the earliest document expiry date.
 * Returns a Date. Dates are yyyy-MM-dd strings on documents.
 */
export function computeExpiry(now, documents, defaultMonths = 12, explicit = null) {
  if (explicit) return new Date(`${explicit}T23:59:59Z`);
  const byDefault = new Date(now);
  byDefault.setUTCMonth(byDefault.getUTCMonth() + defaultMonths);
  const docDates = documents
    .map((d) => d.expiryDate)
    .filter((d) => typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d))
    .map((d) => new Date(`${d}T23:59:59Z`));
  return [byDefault, ...docDates].reduce((a, b) => (b < a ? b : a));
}

/** Is this trusted organisation due to expire (by date or by an expired, verified document)? */
export function isDue(org, documents, now) {
  if (![Status.VERIFIED, Status.OFFICIAL_GOVERNMENT].includes(org.verificationStatus)) return false;
  const expiresAt = org.verificationExpiresAt?.toDate ? org.verificationExpiresAt.toDate() : org.verificationExpiresAt;
  if (expiresAt && expiresAt <= now) return true;
  const today = now.toISOString().slice(0, 10);
  return documents.some((d) => d.verificationStatus === 'VERIFIED' && d.expiryDate && d.expiryDate < today);
}
