/**
 * Types mirroring the backend DTO records (src/main/java/com/bizgrow/<module>/dto).
 * These are the ONLY shapes the UI ever sees — entities never cross the wire.
 * Keep this file in sync with the API; the integration tests are the contract.
 */

/* ------------------------------- enums ------------------------------- */

export type OrganizationStatus = "ACTIVE" | "SUSPENDED" | "ARCHIVED";
export type MembershipStatus = "ACTIVE" | "SUSPENDED" | "REMOVED";
export type WebsiteStatus = "ACTIVE" | "INACTIVE";
export type UserStatus = "ACTIVE" | "PENDING_VERIFICATION" | "SUSPENDED" | "DEACTIVATED";

export type MembershipRole =
  | "BUSINESS_OWNER"
  | "BUSINESS_ADMIN"
  | "MARKETING_MANAGER"
  | "SALES_MANAGER"
  | "SALES_AGENT"
  | "ANALYST";

export type InvitationState = "PENDING" | "ACCEPTED" | "REVOKED" | "EXPIRED";

/* ------------------------------ auth/user ------------------------------ */

export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string | null;
  status: UserStatus;
  emailVerified: boolean;
  createdAt: string;
}

/** register/login/refresh body. accessToken is held in memory ONLY. */
export interface AuthSession {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  csrfToken: string;
  user: User;
}

export interface MembershipBrief {
  organizationId: string;
  name: string;
  slug: string;
  status: OrganizationStatus;
  role: MembershipRole;
}

export interface MeResponse {
  user: User;
  memberships: MembershipBrief[];
}

/* ----------------------------- organization ----------------------------- */

export interface Organization {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  industry: string | null;
  websiteUrl: string | null;
  countryCode: string;
  currencyCode: string;
  timezone: string;
  status: OrganizationStatus;
  createdBy: string;
  memberCount: number;
  websiteCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface OnboardingProgress {
  websiteRegistered: boolean;
  hasAdditionalMember: boolean;
}

export interface Dashboard {
  organization: Organization;
  recentWebsites: Website[];
  onboarding: OnboardingProgress;
}

/* -------------------------------- websites -------------------------------- */

export interface Website {
  id: string;
  organizationId: string;
  name: string;
  domain: string;
  url: string;
  status: WebsiteStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

/* -------------------------------- members -------------------------------- */

export interface Member {
  id: string;
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  role: MembershipRole;
  status: MembershipStatus;
  joinedAt: string;
}

export interface Invitation {
  id: string;
  email: string;
  role: MembershipRole;
  state: InvitationState;
  expiresAt: string | null;
  acceptedAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  /** Dev-only raw token (expose-dev-token); null in production responses. */
  devAcceptToken: string | null;
}

export interface InvitationAcceptance {
  organizationId: string;
  name: string;
  slug: string;
  role: MembershipRole;
}

/* ------------------------------ envelopes ------------------------------ */

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/** Uniform error envelope produced by GlobalExceptionHandler. */
export interface ErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
  traceId?: string;
}
