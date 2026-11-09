/**
 * Types mirroring the backend DTO records (src/main/java/com/YemomBizGrow/<module>/dto).
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

/* ------------------------------- audit logs ------------------------------- */

export interface AuditLog {
  id: string;
  organizationId: string | null;
  actorUserId: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

/* ------------------------------ envelopes ------------------------------ */

export interface MessageResponse {
  message: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
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


/* --------------------------------- seo --------------------------------- */

export type SeoAuditStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
export type SeoIssueSeverity = "CRITICAL" | "WARNING" | "INFO";
export type SeoIssueCategory = "TECHNICAL" | "ON_PAGE" | "CONTENT" | "ACCESSIBILITY" | "PERFORMANCE";

export interface SeoAudit {
  id: string;
  organizationId: string;
  websiteId: string;
  status: SeoAuditStatus;
  score: number | null;
  technicalScore: number | null;
  onPageScore: number | null;
  contentScore: number | null;
  accessibilityScore: number | null;
  pagesCrawled: number;
  criticalIssuesCount: number;
  warningIssuesCount: number;
  infoIssuesCount: number;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface SeoIssue {
  id: string;
  auditId: string;
  organizationId: string;
  websiteId: string;
  category: SeoIssueCategory;
  severity: SeoIssueSeverity;
  title: string;
  description: string;
  affectedUrl: string | null;
  evidence: string | null;
  explanation: string | null;
  recommendation: string | null;
  status: string;
  createdAt: string;
}

export interface SeoCrawledPage {
  id: string;
  auditId: string;
  organizationId: string;
  websiteId: string;
  url: string;
  statusCode: number | null;
  title: string | null;
  metaDescription: string | null;
  canonicalUrl: string | null;
  h1Count: number;
  h2Count: number;
  wordCount: number;
  internalLinksCount: number;
  externalLinksCount: number;
  imagesCount: number;
  missingAltImagesCount: number;
  loadTimeMs: number | null;
  hasViewportMeta: boolean;
  isHttps: boolean;
  createdAt: string;
}


/* ---------------------------------- crm ---------------------------------- */

export type LeadStage =
  | "NEW"
  | "CONTACTED"
  | "QUALIFIED"
  | "PROPOSAL"
  | "NEGOTIATION"
  | "WON"
  | "LOST";

export type LeadSource =
  | "DIRECT"
  | "WEBSITE_FORM"
  | "CHATBOT"
  | "CAMPAIGN"
  | "REFERRAL"
  | "IMPORT"
  | "OTHER";

export type LeadStatus = "ACTIVE" | "ARCHIVED";

export type LeadActivityType =
  | "STAGE_CHANGE"
  | "NOTE"
  | "CALL"
  | "EMAIL"
  | "MEETING"
  | "FORM_SUBMISSION"
  | "ASSIGNMENT";

export interface Lead {
  id: string;
  organizationId: string;
  firstName: string;
  lastName: string | null;
  email: string | null;
  phoneNumber: string | null;
  companyName: string | null;
  source: LeadSource;
  stage: LeadStage;
  status: LeadStatus;
  assignedUserId: string | null;
  estimatedValueEtb: number | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface LeadActivity {
  id: string;
  organizationId: string;
  leadId: string;
  actorUserId: string | null;
  activityType: LeadActivityType;
  description: string;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

export interface StageMetrics {
  count: number;
  totalValue: number;
}

export interface PipelineSummary {
  stages: Record<LeadStage, StageMetrics>;
  totalLeads: number;
  totalPipelineValueEtb: number;
}

/* ----------------------------- analytics & integrations ----------------------------- */

export interface GoogleConnectionStatus {
  connected: boolean;
  websiteId: string;
  googleAccountId: string | null;
  connectedAt: string | null;
  tokenExpiry: string | null;
  scopes: string | null;
}

export interface GoogleConnectUrl {
  authUrl: string;
  state: string;
}

export interface GoogleCallbackRequest {
  websiteId: string;
  code: string;
  state: string;
}

export interface AnalyticsDailyPoint {
  date: string;
  sessions: number;
  users: number;
  pageViews: number;
  clicks: number;
  impressions: number;
}

export interface AnalyticsOverview {
  totalSessions: number;
  totalUsers: number;
  totalPageViews: number;
  avgBounceRate: number;
  totalClicks: number;
  totalImpressions: number;
  avgCtr: number;
  avgPosition: number;
  dailyMetrics: AnalyticsDailyPoint[];
  topPages: Array<{ page: string; views: number; avgTime?: number; clicks?: number; impressions?: number }>;
  topQueries: Array<{ query: string; clicks: number; impressions: number; position?: number; ctr?: number }>;
  devices: Array<{ device: string; sessions: number }>;
  channels: Array<{ channel: string; sessions: number }>;
}

/* ----------------------------- AI & Chatbot ----------------------------- */

export type RecommendationCategory =
  | "SEO"
  | "LEAD_GENERATION"
  | "CONVERSION"
  | "CONTENT"
  | "TECHNICAL"
  | "SPEED";

export type RecommendationPriority = "HIGH" | "MEDIUM" | "LOW";

export type RecommendationStatus = "OPEN" | "APPLIED" | "DISMISSED";

export interface AiRecommendation {
  id: string;
  organizationId: string;
  websiteId: string | null;
  category: RecommendationCategory;
  priority: RecommendationPriority;
  title: string;
  problem: string;
  evidence: string | null;
  expectedBenefit: string | null;
  actionRecommendation: string;
  status: RecommendationStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ChatbotConfig {
  id: string;
  organizationId: string;
  botName: string;
  greetingMessage: string;
  promptInstructions: string | null;
  primaryColor: string;
  faqItems: Array<{ question: string; answer: string; category?: string }> | null;
  isEnabled: boolean;
  updatedAt: string;
}

export interface ChatbotConfigRequest {
  botName: string;
  greetingMessage: string;
  promptInstructions?: string;
  primaryColor: string;
  faqItems?: Array<{ question: string; answer: string; category?: string }>;
  isEnabled: boolean;
}

export interface PublicChatMessageRequest {
  sessionId: string;
  message: string;
  visitorName?: string;
  visitorEmail?: string;
  visitorPhone?: string;
}

export interface PublicChatMessageResponse {
  role: string;
  content: string;
  timestamp: string;
}

// ---- Marketing Campaigns ----
export type CampaignObjective =
  | "LEAD_GENERATION"
  | "BRAND_AWARENESS"
  | "WEBSITE_TRAFFIC"
  | "SALES"
  | "EVENT_PROMOTION";

export type CampaignStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "COMPLETED";

export type CampaignChannel =
  | "GOOGLE_ADS"
  | "META_ADS"
  | "TELEGRAM"
  | "TIKTOK"
  | "EMAIL"
  | "DIRECT"
  | "SMS";

export interface Campaign {
  id: string;
  organizationId: string;
  name: string;
  description?: string;
  objective: CampaignObjective;
  status: CampaignStatus;
  channel: CampaignChannel;
  budgetEtb: number;
  spentEtb: number;
  startDate?: string;
  endDate?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  utmTerm?: string;
  utmContent?: string;
  attributedLeadsCount: number;
  pipelineValueEtb: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCampaignRequest {
  name: string;
  description?: string;
  objective: CampaignObjective;
  status?: CampaignStatus;
  channel: CampaignChannel;
  budgetEtb?: number;
  spentEtb?: number;
  startDate?: string;
  endDate?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  utmTerm?: string;
  utmContent?: string;
}

export interface UpdateCampaignRequest {
  name?: string;
  description?: string;
  objective?: CampaignObjective;
  status?: CampaignStatus;
  channel?: CampaignChannel;
  budgetEtb?: number;
  spentEtb?: number;
  startDate?: string;
  endDate?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  utmTerm?: string;
  utmContent?: string;
}

export interface CampaignPerformance {
  campaignId: string;
  campaignName: string;
  budgetEtb: number;
  spentEtb: number;
  totalAttributedLeads: number;
  wonLeads: number;
  wonPipelineValueEtb: number;
  totalPipelineValueEtb: number;
  costPerLeadEtb: number;
  returnOnAdSpendRatio: number;
}

// ---- Agency Workspace ----
export type AgencyRelationshipStatus = "ACTIVE" | "PENDING" | "REVOKED";
export type AgencyAccessLevel = "FULL" | "READ_ONLY" | "MARKETING_ONLY";

export interface AgencyClientSummary {
  relationshipId: string;
  clientOrganizationId: string;
  clientName: string;
  clientSlug: string;
  clientIndustry?: string;
  status: AgencyRelationshipStatus;
  accessLevel: AgencyAccessLevel;
  websiteCount: number;
  latestSeoScore?: number;
  totalLeads: number;
  totalPipelineValueEtb: number;
  activeCampaignsCount: number;
  linkedAt: string;
}

export interface AgencyOverview {
  totalClients: number;
  activeClients: number;
  totalManagedWebsites: number;
  totalAggregatedLeads: number;
  totalAggregatedPipelineEtb: number;
  averageClientSeoScore: number;
  clients: AgencyClientSummary[];
}

export interface LinkClientRequest {
  clientOrganizationSlug: string;
  accessLevel?: AgencyAccessLevel;
  notes?: string;
}

export interface UpdateClientRelationshipRequest {
  status?: AgencyRelationshipStatus;
  accessLevel?: AgencyAccessLevel;
  notes?: string;
}

// ---- Cross-Module Reports ----
export interface ComprehensiveReport {
  organizationId: string;
  organizationName: string;
  generatedAt: string;
  seo: {
    latestScore?: number;
    crawledPagesCount: number;
    criticalIssuesCount: number;
    warningIssuesCount: number;
    auditStatus: string;
  };
  traffic: {
    totalSessions: number;
    totalPageviews: number;
    bounceRatePct: number;
    totalSearchImpressions: number;
    totalSearchClicks: number;
    averageCtrPct: number;
  };
  crm: {
    totalLeads: number;
    activeLeads: number;
    wonLeads: number;
    lostLeads: number;
    conversionRatePct: number;
    totalPipelineValueEtb: number;
    wonRevenueEtb: number;
  };
  marketing: {
    totalCampaigns: number;
    activeCampaigns: number;
    totalBudgetEtb: number;
    totalSpentEtb: number;
    attributedLeadsCount: number;
    returnOnAdSpend: number;
  };
}