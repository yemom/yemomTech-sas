import { describe, expect, it } from "vitest";
import {
  PASSWORD_RULES,
  acceptInviteSchema,
  inviteSchema,
  loginSchema,
  organizationSchema,
  passwordSchema,
  registerSchema,
  websiteSchema,
} from "@/lib/api/schemas";

describe("password policy (mirrors backend PasswordValidator)", () => {
  it("accepts a strong password", () => {
    expect(passwordSchema.safeParse("Correct-Horse-9!").success).toBe(true);
  });

  it("rejects passwords missing each required class", () => {
    expect(passwordSchema.safeParse("alllowercase1!").success).toBe(false); // no upper
    expect(passwordSchema.safeParse("ALLUPPERCASE1!").success).toBe(false); // no lower
    expect(passwordSchema.safeParse("NoDigitsHere!!").success).toBe(false); // no digit
    expect(passwordSchema.safeParse("NoSymbolsHere1").success).toBe(false); // no symbol
    expect(passwordSchema.safeParse("Short1!").success).toBe(false); // < 8
    expect(passwordSchema.safeParse("a".repeat(129) + "A1!").success).toBe(false); // > 128
  });

  it("exposes exactly the six backend rules", () => {
    expect(PASSWORD_RULES).toHaveLength(6);
    const labels = PASSWORD_RULES.map((r) => r.label);
    expect(labels).toContain("at least 8 characters");
    expect(labels).toContain("a symbol");
  });
});

describe("loginSchema", () => {
  it("requires both fields", () => {
    expect(loginSchema.safeParse({ email: "", password: "" }).success).toBe(false);
  });

  it("rejects malformed emails", () => {
    expect(
      loginSchema.safeParse({ email: "not-an-email", password: "x" }).success,
    ).toBe(false);
    expect(
      loginSchema.safeParse({ email: "a@b", password: "x" }).success,
    ).toBe(false);
  });
});

describe("registerSchema", () => {
  const base = {
    firstName: "Selam",
    lastName: "Bekele",
    email: "selam@example.et",
    password: "Correct-Horse-9!",
  };

  it("accepts a valid payload without a phone number", () => {
    expect(registerSchema.safeParse(base).success).toBe(true);
  });

  it("trims are NOT applied by the schema — blank names are rejected", () => {
    expect(registerSchema.safeParse({ ...base, firstName: "   " }).success).toBe(false);
  });

  it("validates the optional phone format", () => {
    expect(registerSchema.safeParse({ ...base, phoneNumber: "+251911234567" }).success).toBe(true);
    expect(registerSchema.safeParse({ ...base, phoneNumber: "abc" }).success).toBe(false);
    // Empty string is allowed (the register page drops it before submit).
    expect(registerSchema.safeParse({ ...base, phoneNumber: "" }).success).toBe(true);
  });
});

describe("websiteSchema", () => {
  it("requires an http(s) URL", () => {
    expect(
      websiteSchema.safeParse({ name: "Site", url: "https://example.et" }).success,
    ).toBe(true);
    expect(
      websiteSchema.safeParse({ name: "Site", url: "ftp://example.et" }).success,
    ).toBe(false);
    expect(websiteSchema.safeParse({ name: "Site", url: "example.et" }).success).toBe(false);
  });

  it("requires a non-blank name", () => {
    expect(websiteSchema.safeParse({ name: " ", url: "https://a.et" }).success).toBe(false);
  });
});

describe("organizationSchema", () => {
  it("accepts a minimal payload (name only)", () => {
    expect(organizationSchema.safeParse({ name: "Habesha Foods" }).success).toBe(true);
  });

  it("rejects a blank name and over-long description", () => {
    expect(organizationSchema.safeParse({ name: "" }).success).toBe(false);
    expect(
      organizationSchema.safeParse({ name: "Ok", description: "x".repeat(2001) }).success,
    ).toBe(false);
  });

  it("validates the optional profile URL", () => {
    expect(
      organizationSchema.safeParse({ name: "Ok", websiteUrl: "https://example.et" }).success,
    ).toBe(true);
    expect(
      organizationSchema.safeParse({ name: "Ok", websiteUrl: "example.et" }).success,
    ).toBe(false);
  });
});

describe("inviteSchema", () => {
  it("excludes BUSINESS_OWNER (owner cannot be invited)", () => {
    expect(
      inviteSchema.safeParse({ email: "a@b.et", role: "BUSINESS_ADMIN" }).success,
    ).toBe(true);
    expect(
      inviteSchema.safeParse({ email: "a@b.et", role: "BUSINESS_OWNER" }).success,
    ).toBe(false);
  });
});

describe("acceptInviteSchema", () => {
  it("requires a non-empty token", () => {
    expect(acceptInviteSchema.safeParse({ token: "" }).success).toBe(false);
    expect(acceptInviteSchema.safeParse({ token: "tok" }).success).toBe(true);
  });
});
