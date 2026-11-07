import { describe, expect, it } from "vitest";
import { INVITABLE_ROLES, ROLE_LABELS, can } from "@/lib/api/permissions";

const ALL_ROLES = [
  "BUSINESS_OWNER",
  "BUSINESS_ADMIN",
  "MARKETING_MANAGER",
  "SALES_MANAGER",
  "SALES_AGENT",
  "ANALYST",
] as const;

describe("permission matrix (mirrors backend Permission.java)", () => {
  it("every role can view the org and its members", () => {
    for (const role of ALL_ROLES) {
      expect(can(role, "ORG_VIEW"), role).toBe(true);
      expect(can(role, "MEMBER_VIEW"), role).toBe(true);
      expect(can(role, "WEBSITE_VIEW"), role).toBe(true);
    }
  });

  it("only owner and admin can update the org / manage members", () => {
    for (const role of ALL_ROLES) {
      const expected = role === "BUSINESS_OWNER" || role === "BUSINESS_ADMIN";
      expect(can(role, "ORG_UPDATE"), role).toBe(expected);
      expect(can(role, "MEMBER_MANAGE"), role).toBe(expected);
      expect(can(role, "INVITE_MANAGE"), role).toBe(expected);
    }
  });

  it("only the owner can archive the org", () => {
    for (const role of ALL_ROLES) {
      expect(can(role, "ORG_DELETE"), role).toBe(role === "BUSINESS_OWNER");
    }
  });

  it("website management includes marketing manager", () => {
    expect(can("MARKETING_MANAGER", "WEBSITE_MANAGE")).toBe(true);
    expect(can("SALES_MANAGER", "WEBSITE_MANAGE")).toBe(false);
    expect(can("ANALYST", "WEBSITE_MANAGE")).toBe(false);
  });
});

describe("role labels", () => {
  it("labels every role", () => {
    for (const role of ALL_ROLES) {
      expect(ROLE_LABELS[role]).toBeTruthy();
    }
    expect(Object.keys(ROLE_LABELS)).toHaveLength(ALL_ROLES.length);
  });
});

describe("invitable roles", () => {
  it("never includes BUSINESS_OWNER", () => {
    expect(INVITABLE_ROLES).not.toContain("BUSINESS_OWNER");
    expect(INVITABLE_ROLES).toHaveLength(5);
  });
});
