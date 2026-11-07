/**
 * Client-side mirror of the backend permission matrix (Permission.java).
 *
 * Purpose: hide actions the current role cannot perform (better UX than a
 * server 403). The backend re-checks every call — this table is a hint,
 * never an authorization boundary. Keep in sync with Permission.java.
 */
import type { MembershipRole } from "./types";

export type Capability =
  | "ORG_VIEW"
  | "ORG_UPDATE"
  | "ORG_DELETE"
  | "MEMBER_VIEW"
  | "MEMBER_MANAGE"
  | "INVITE_MANAGE"
  | "WEBSITE_VIEW"
  | "WEBSITE_MANAGE";

const ALL_ROLES: MembershipRole[] = [
  "BUSINESS_OWNER",
  "BUSINESS_ADMIN",
  "MARKETING_MANAGER",
  "SALES_MANAGER",
  "SALES_AGENT",
  "ANALYST",
];

const OWNER_ADMIN: MembershipRole[] = ["BUSINESS_OWNER", "BUSINESS_ADMIN"];

const MATRIX: Record<Capability, MembershipRole[]> = {
  ORG_VIEW: ALL_ROLES,
  ORG_UPDATE: OWNER_ADMIN,
  ORG_DELETE: ["BUSINESS_OWNER"],
  MEMBER_VIEW: ALL_ROLES,
  MEMBER_MANAGE: OWNER_ADMIN,
  INVITE_MANAGE: OWNER_ADMIN,
  WEBSITE_VIEW: ALL_ROLES,
  WEBSITE_MANAGE: [...OWNER_ADMIN, "MARKETING_MANAGER"],
};

export function can(role: MembershipRole, capability: Capability): boolean {
  return MATRIX[capability].includes(role);
}

/** Display labels for the role select boxes. */
export const ROLE_LABELS: Record<MembershipRole, string> = {
  BUSINESS_OWNER: "Business Owner",
  BUSINESS_ADMIN: "Business Admin",
  MARKETING_MANAGER: "Marketing Manager",
  SALES_MANAGER: "Sales Manager",
  SALES_AGENT: "Sales Agent",
  ANALYST: "Analyst",
};

/** Roles an inviter may grant (cannot grant owner via invite). */
export const INVITABLE_ROLES: MembershipRole[] = [
  "BUSINESS_ADMIN",
  "MARKETING_MANAGER",
  "SALES_MANAGER",
  "SALES_AGENT",
  "ANALYST",
];
