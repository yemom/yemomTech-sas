/**
 * Client-side validation schemas.
 *
 * These MIRROR the backend constraints (RegisterRequest, CreateOrganizationRequest,
 * CreateWebsiteRequest, …) for instant feedback — the server remains the
 * authority and its fieldErrors are displayed verbatim when they disagree.
 */
import { z } from "zod";

/** Backend policy: 8-128 chars with upper, lower, digit and symbol. */
export const PASSWORD_RULES = [
  { test: (v: string) => v.length >= 8, label: "at least 8 characters" },
  { test: (v: string) => v.length <= 128, label: "at most 128 characters" },
  { test: (v: string) => /[A-Z]/.test(v), label: "an uppercase letter" },
  { test: (v: string) => /[a-z]/.test(v), label: "a lowercase letter" },
  { test: (v: string) => /[0-9]/.test(v), label: "a digit" },
  { test: (v: string) => /[^A-Za-z0-9]/.test(v), label: "a symbol" },
];

export const passwordSchema = z
  .string()
  .min(1, "Password is required.")
  .refine((v) => PASSWORD_RULES.every((r) => r.test(v)), {
    message:
      "Use 8-128 characters including an uppercase letter, a lowercase letter, a digit and a symbol.",
  });

export const emailSchema = z
  .string()
  .min(1, "Email is required.")
  .max(255, "Email must be at most 255 characters.")
  .refine((v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()), "Enter a valid email address.");

const requiredName = (max: number, label: string) =>
  z
    .string()
    .refine((v) => v.trim().length > 0, `${label} is required.`)
    .max(max, `${label} must be at most ${max} characters.`);

/* --------------------------------- auth --------------------------------- */

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Password is required."),
});
export type LoginInput = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  firstName: requiredName(100, "First name"),
  lastName: requiredName(100, "Last name"),
  email: emailSchema,
  password: passwordSchema,
  /** Optional; empty input is dropped before submit (backend rejects ""). */
  phoneNumber: z
    .string()
    .optional()
    .refine(
      (v) => !v || /^\+?[0-9][0-9 ()-]{6,19}$/.test(v.trim()),
      "Phone number must look like +251911234567.",
    ),
});
export type RegisterInput = z.infer<typeof registerSchema>;

export const forgotPasswordSchema = z.object({
  email: emailSchema,
});
export type ForgotPasswordInput = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z.object({
  token: z.string().min(1, "Reset token is required."),
  newPassword: passwordSchema,
});
export type ResetPasswordInput = z.infer<typeof resetPasswordSchema>;

export const verifyEmailSchema = z.object({
  token: z.string().min(1, "Verification token is required."),
});
export type VerifyEmailInput = z.infer<typeof verifyEmailSchema>;

/* ----------------------------- organization ----------------------------- */

export const organizationSchema = z.object({
  name: requiredName(200, "Organization name"),
  industry: z.string().max(100, "Industry must be at most 100 characters.").optional(),
  description: z.string().max(2000, "Description must be at most 2000 characters.").optional(),
  websiteUrl: z
    .string()
    .optional()
    .refine((v) => !v || /^https?:\/\/.+/i.test(v.trim()), "Must start with http:// or https://")
    .refine((v) => !v || v.trim().length <= 500, "URL must be at most 500 characters."),
});
export type OrganizationInput = z.infer<typeof organizationSchema>;

/* -------------------------------- website -------------------------------- */

export const websiteSchema = z.object({
  name: requiredName(200, "Website name"),
  url: z
    .string()
    .min(1, "Website URL is required.")
    .max(2048, "URL must be at most 2048 characters.")
    .refine((v) => /^https?:\/\/.+/i.test(v.trim()), "URL must start with http:// or https://"),
});
export type WebsiteInput = z.infer<typeof websiteSchema>;

/* ------------------------------ invitations ------------------------------ */

export const inviteSchema = z.object({
  email: emailSchema,
  role: z.enum([
    "BUSINESS_ADMIN",
    "MARKETING_MANAGER",
    "SALES_MANAGER",
    "SALES_AGENT",
    "ANALYST",
  ]),
});
export type InviteInput = z.infer<typeof inviteSchema>;

export const acceptInviteSchema = z.object({
  token: z.string().min(1, "Invitation token is required."),
});
export type AcceptInviteInput = z.infer<typeof acceptInviteSchema>;


/* ---------------------------------- crm ---------------------------------- */

export const leadSchema = z.object({
  firstName: requiredName(100, "First name"),
  lastName: z.string().max(100, "Last name must be at most 100 characters.").optional(),
  email: z
    .string()
    .max(255, "Email must be at most 255 characters.")
    .refine((v) => !v || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()), "Enter a valid email address.")
    .optional(),
  phoneNumber: z.string().max(50, "Phone number must be at most 50 characters.").optional(),
  companyName: z.string().max(200, "Company name must be at most 200 characters.").optional(),
  source: z.enum(["DIRECT", "WEBSITE_FORM", "CHATBOT", "CAMPAIGN", "REFERRAL", "IMPORT", "OTHER"]).optional(),
  stage: z.enum(["NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON", "LOST"]).optional(),
  estimatedValueEtb: z.string().optional(),
  notes: z.string().max(2000, "Notes must be at most 2000 characters.").optional(),
});
export type LeadInput = z.infer<typeof leadSchema>;

export const leadActivitySchema = z.object({
  activityType: z.enum(["STAGE_CHANGE", "NOTE", "CALL", "EMAIL", "MEETING", "FORM_SUBMISSION", "ASSIGNMENT"]),
  description: z.string().min(1, "Description is required.").max(2000, "Description must be at most 2000 characters."),
});
export type LeadActivityInput = z.infer<typeof leadActivitySchema>;
