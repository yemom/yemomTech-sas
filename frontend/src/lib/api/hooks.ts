"use client";

/**
 * TanStack Query hooks — the only place components learn HTTP details.
 * Keys are hierarchical so invalidation is coarse but predictable:
 * invalidating ["organizations"] refreshes every org-scoped child query.
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
} from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  AgencyClientSummary,
  AgencyOverview,
  AiRecommendation,
  AnalyticsOverview,
  AuditLog,
  BillingHistory,
  Campaign,
  CampaignPerformance,
  ChatbotConfig,
  ChatbotConfigRequest,
  ComprehensiveReport,
  CreateCampaignRequest,
  Dashboard,
  GoogleAccessibleResources,
  GoogleConnectUrl,
  GoogleConnectionStatus,
  GooglePropertySelectionRequest,
  InitializePaymentRequest,
  InitializePaymentResponse,
  Invitation,
  InvitationAcceptance,
  Lead,
  LeadActivity,
  LeadStage,
  LinkClientRequest,
  Member,
  MembershipRole,
  MembershipStatus,
  MessageResponse,
  Organization,
  OrganizationSubscription,
  Page,
  PaymentTransaction,
  PipelineSummary,
  PublicChatMessageResponse,
  RecommendationStatus,
  SeoAudit,
  SeoCrawledPage,
  SeoIssue,
  SubscriptionPlan,
  UpdateCampaignRequest,
  UpdateClientRelationshipRequest,
  VerifyPaymentResponse,
  Website,
} from "@/lib/api/types";

export const queryKeys = {
  organizations: ["organizations"] as const,
  dashboard: (orgId: string) => ["organizations", orgId, "dashboard"] as const,
  organization: (orgId: string) => ["organizations", orgId] as const,
  websites: (orgId: string, page: number) =>
    ["organizations", orgId, "websites", page] as const,
  members: (orgId: string, page: number) =>
    ["organizations", orgId, "members", page] as const,
  invitations: (orgId: string, page: number) =>
    ["organizations", orgId, "invitations", page] as const,
  auditLogs: (orgId: string, page: number) =>
    ["organizations", orgId, "auditLogs", page] as const,
  seoAudits: (orgId: string, websiteId: string, page: number) =>
    ["organizations", orgId, "websites", websiteId, "audits", page] as const,
  seoAudit: (orgId: string, websiteId: string, auditId: string) =>
    ["organizations", orgId, "websites", websiteId, "audits", auditId] as const,
  seoAuditIssues: (
    orgId: string,
    websiteId: string,
    auditId: string,
    page: number,
    severity?: string,
    category?: string,
  ) =>
    [
      "organizations",
      orgId,
      "websites",
      websiteId,
      "audits",
      auditId,
      "issues",
      page,
      severity,
      category,
    ] as const,
  seoAuditPages: (orgId: string, websiteId: string, auditId: string, page: number) =>
    ["organizations", orgId, "websites", websiteId, "audits", auditId, "pages", page] as const,
  leads: (orgId: string, stage?: string, search?: string, page?: number) =>
    ["organizations", orgId, "leads", stage, search, page] as const,
  lead: (orgId: string, leadId: string) =>
    ["organizations", orgId, "leads", leadId] as const,
  leadActivities: (orgId: string, leadId: string, page: number) =>
    ["organizations", orgId, "leads", leadId, "activities", page] as const,
  pipelineSummary: (orgId: string) =>
    ["organizations", orgId, "leads", "pipeline-summary"] as const,
  googleStatus: (orgId: string, websiteId: string) =>
    ["organizations", orgId, "integrations", "google", websiteId] as const,
  analyticsOverview: (
    orgId: string,
    websiteId?: string,
    startDate?: string,
    endDate?: string,
  ) =>
    [
      "organizations",
      orgId,
      "analytics",
      "overview",
      websiteId,
      startDate,
      endDate,
    ] as const,
  aiRecommendations: (orgId: string, status?: string, page?: number) =>
    ["organizations", orgId, "ai", "recommendations", status, page] as const,
  chatbotConfig: (orgId: string) =>
    ["organizations", orgId, "chatbot", "config"] as const,
};

/* ----------------------------- organizations ----------------------------- */

export function useMyOrganizations() {
  return useQuery({
    queryKey: queryKeys.organizations,
    queryFn: () => api.get<Organization[]>("/organizations"),
  });
}

export function useDashboard(orgId: string | null) {
  return useQuery({
    queryKey: queryKeys.dashboard(orgId ?? "-"),
    queryFn: () => api.get<Dashboard>(`/organizations/${orgId}/dashboard`),
    enabled: Boolean(orgId),
  });
}

export function useCreateOrganization() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; industry?: string; websiteUrl?: string }) =>
      api.post<Organization>("/organizations", body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: queryKeys.organizations });
      void client.invalidateQueries({ queryKey: ["auth"] });
    },
  });
}

export function useUpdateOrganization(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: Partial<{ name: string; industry: string; description: string; websiteUrl: string }>) =>
      api.patch<Organization>(`/organizations/${orgId}`, body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: queryKeys.organizations });
    },
  });
}

export function useRestoreOrganization(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<Organization>(`/organizations/${orgId}/restore`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: queryKeys.organizations });
    },
  });
}

/* -------------------------------- websites -------------------------------- */

export function useWebsites(orgId: string | null, page: number, size = 20) {
  return useQuery({
    queryKey: queryKeys.websites(orgId ?? "-", page),
    queryFn: () =>
      api.get<Page<Website>>(
        `/organizations/${orgId}/websites?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId),
  });
}

export function useCreateWebsite(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; url: string }) =>
      api.post<Website>(`/organizations/${orgId}/websites`, body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["organizations", orgId, "websites"] });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard(orgId) });
    },
  });
}

export function useUpdateWebsite(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ websiteId, body }: { websiteId: string; body: { name?: string; url?: string } }) =>
      api.patch<Website>(`/organizations/${orgId}/websites/${websiteId}`, body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["organizations", orgId, "websites"] });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard(orgId) });
    },
  });
}

export function useDeactivateWebsite(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (websiteId: string) =>
      api.delete<Website>(`/organizations/${orgId}/websites/${websiteId}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["organizations", orgId, "websites"] });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard(orgId) });
    },
  });
}

/* -------------------------------- members -------------------------------- */

export function useMembers(orgId: string | null, page: number, size = 20) {
  return useQuery({
    queryKey: queryKeys.members(orgId ?? "-", page),
    queryFn: () =>
      api.get<Page<Member>>(
        `/organizations/${orgId}/members?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId),
  });
}

export function useUpdateMember(orgId: string): UseMutationResult<
  Member,
  unknown,
  { memberId: string; role?: MembershipRole; status?: MembershipStatus }
> {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ memberId, ...body }: { memberId: string; role?: MembershipRole; status?: MembershipStatus }) =>
      api.patch<Member>(`/organizations/${orgId}/members/${memberId}`, body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["organizations", orgId, "members"] });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard(orgId) });
    },
  });
}

export function useRemoveMember(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) =>
      api.delete<void>(`/organizations/${orgId}/members/${memberId}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["organizations", orgId, "members"] });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard(orgId) });
    },
  });
}

/* ------------------------------ invitations ------------------------------ */

export function useInvitations(orgId: string | null, page: number, size = 20) {
  return useQuery({
    queryKey: queryKeys.invitations(orgId ?? "-", page),
    queryFn: () =>
      api.get<Page<Invitation>>(
        `/organizations/${orgId}/invitations?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId),
  });
}

export function useCreateInvitation(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { email: string; role: MembershipRole }) =>
      api.post<Invitation>(`/organizations/${orgId}/invitations`, body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["organizations", orgId, "invitations"] });
    },
  });
}

export function useRevokeInvitation(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (invitationId: string) =>
      api.post<Invitation>(
        `/organizations/${orgId}/invitations/${invitationId}/revoke`,
      ),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["organizations", orgId, "invitations"] });
    },
  });
}

export function useAcceptInvitation(onJoined?: () => void) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (token: string) =>
      api.post<InvitationAcceptance>("/invitations/accept", { token }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: queryKeys.organizations });
      onJoined?.();
    },
  });
}

/* ------------------------------- audit logs ------------------------------- */

export function useAuditLogs(orgId: string | null, page: number, size = 20) {
  return useQuery({
    queryKey: queryKeys.auditLogs(orgId ?? "-", page),
    queryFn: () =>
      api.get<Page<AuditLog>>(
        `/organizations/${orgId}/audit-logs?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId),
  });
}

/* --------------------------- auth & recovery --------------------------- */

export function useForgotPassword() {
  return useMutation({
    mutationFn: (body: { email: string }) =>
      api.public<MessageResponse>("/auth/forgot-password", body),
  });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: (body: { token: string; newPassword: string }) =>
      api.public<MessageResponse>("/auth/reset-password", body),
  });
}

export function useVerifyEmail() {
  return useMutation({
    mutationFn: (body: { token: string }) =>
      api.public<MessageResponse>("/auth/verify-email", body),
  });
}

export function useResendVerification() {
  return useMutation({
    mutationFn: () => api.post<MessageResponse>("/auth/resend-verification"),
  });
}

export function useRevokeOtherSessions() {
  return useMutation({
    mutationFn: () => api.post<MessageResponse>("/auth/sessions/revoke-others"),
  });
}

export function useRevokeAllSessions() {
  return useMutation({
    mutationFn: () => api.post<MessageResponse>("/auth/sessions/revoke-all"),
  });
}


/* --------------------------------- seo --------------------------------- */

export function useTriggerSeoAudit(orgId: string, websiteId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api.post<SeoAudit>(
        `/organizations/${orgId}/websites/${websiteId}/audits`,
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "websites", websiteId, "audits"],
      });
    },
  });
}

export function useSeoAudits(
  orgId: string | null,
  websiteId: string | null,
  page = 0,
  size = 10,
) {
  return useQuery({
    queryKey: queryKeys.seoAudits(orgId ?? "-", websiteId ?? "-", page),
    queryFn: () =>
      api.get<Page<SeoAudit>>(
        `/organizations/${orgId}/websites/${websiteId}/audits?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId && websiteId),
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data) return false;
      const hasActive = data.content.some(
        (a) => a.status === "PENDING" || a.status === "RUNNING",
      );
      return hasActive ? 3000 : false;
    },
  });
}

export function useSeoAudit(
  orgId: string | null,
  websiteId: string | null,
  auditId: string | null,
) {
  return useQuery({
    queryKey: queryKeys.seoAudit(
      orgId ?? "-",
      websiteId ?? "-",
      auditId ?? "-",
    ),
    queryFn: () =>
      api.get<SeoAudit>(
        `/organizations/${orgId}/websites/${websiteId}/audits/${auditId}`,
      ),
    enabled: Boolean(orgId && websiteId && auditId),
    refetchInterval: (query) => {
      const audit = query.state.data;
      if (!audit) return false;
      return audit.status === "PENDING" || audit.status === "RUNNING"
        ? 3000
        : false;
    },
  });
}

export function useSeoAuditIssues(
  orgId: string | null,
  websiteId: string | null,
  auditId: string | null,
  severity?: string,
  category?: string,
  page = 0,
  size = 50,
) {
  let url = `/organizations/${orgId}/websites/${websiteId}/audits/${auditId}/issues?page=${page}&size=${size}`;
  if (severity) url += `&severity=${encodeURIComponent(severity)}`;
  if (category) url += `&category=${encodeURIComponent(category)}`;

  return useQuery({
    queryKey: queryKeys.seoAuditIssues(
      orgId ?? "-",
      websiteId ?? "-",
      auditId ?? "-",
      page,
      severity,
      category,
    ),
    queryFn: () => api.get<Page<SeoIssue>>(url),
    enabled: Boolean(orgId && websiteId && auditId),
  });
}

export function useSeoAuditPages(
  orgId: string | null,
  websiteId: string | null,
  auditId: string | null,
  page = 0,
  size = 50,
) {
  return useQuery({
    queryKey: queryKeys.seoAuditPages(
      orgId ?? "-",
      websiteId ?? "-",
      auditId ?? "-",
      page,
    ),
    queryFn: () =>
      api.get<Page<SeoCrawledPage>>(
        `/organizations/${orgId}/websites/${websiteId}/audits/${auditId}/pages?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId && websiteId && auditId),
  });
}


/* ---------------------------------- crm ---------------------------------- */

export function useLeads(
  orgId: string | null,
  stage?: string,
  search?: string,
  page = 0,
  size = 20,
) {
  let url = `/organizations/${orgId}/leads?page=${page}&size=${size}`;
  if (stage) url += `&stage=${encodeURIComponent(stage)}`;
  if (search) url += `&search=${encodeURIComponent(search)}`;

  return useQuery({
    queryKey: queryKeys.leads(orgId ?? "-", stage, search, page),
    queryFn: () => api.get<Page<Lead>>(url),
    enabled: Boolean(orgId),
  });
}

export function useLead(orgId: string | null, leadId: string | null) {
  return useQuery({
    queryKey: queryKeys.lead(orgId ?? "-", leadId ?? "-"),
    queryFn: () => api.get<Lead>(`/organizations/${orgId}/leads/${leadId}`),
    enabled: Boolean(orgId && leadId),
  });
}

export function usePipelineSummary(orgId: string | null) {
  return useQuery({
    queryKey: queryKeys.pipelineSummary(orgId ?? "-"),
    queryFn: () =>
      api.get<PipelineSummary>(`/organizations/${orgId}/leads/pipeline-summary`),
    enabled: Boolean(orgId),
  });
}

export function useCreateLead(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: Partial<Lead>) =>
      api.post<Lead>(`/organizations/${orgId}/leads`, body),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "leads"],
      });
    },
  });
}

export function useUpdateLead(orgId: string, leadId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: Partial<Lead>) =>
      api.patch<Lead>(`/organizations/${orgId}/leads/${leadId}`, body),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "leads"],
      });
    },
  });
}

export function useTransitionLeadStage(orgId: string, leadId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { stage: LeadStage; notes?: string }) =>
      api.patch<Lead>(`/organizations/${orgId}/leads/${leadId}/stage`, body),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "leads"],
      });
    },
  });
}

export function useArchiveLead(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (leadId: string) =>
      api.delete<void>(`/organizations/${orgId}/leads/${leadId}`),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "leads"],
      });
    },
  });
}

export function useLeadActivities(
  orgId: string | null,
  leadId: string | null,
  page = 0,
  size = 20,
) {
  return useQuery({
    queryKey: queryKeys.leadActivities(orgId ?? "-", leadId ?? "-", page),
    queryFn: () =>
      api.get<Page<LeadActivity>>(
        `/organizations/${orgId}/leads/${leadId}/activities?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId && leadId),
  });
}

export function useAddLeadActivity(orgId: string, leadId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      activityType: string;
      description: string;
      metadata?: Record<string, unknown>;
    }) =>
      api.post<LeadActivity>(
        `/organizations/${orgId}/leads/${leadId}/activities`,
        body,
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "leads", leadId, "activities"],
      });
    },
  });
}

export function useCapturePublicLead(orgSlug: string) {
  return useMutation({
    mutationFn: (body: {
      firstName: string;
      lastName?: string;
      email?: string;
      phoneNumber?: string;
      companyName?: string;
      notes?: string;
      honeypot?: string;
    }) => api.public<MessageResponse>(`/public/leads/${orgSlug}`, body),
  });
}

/* ----------------------------- Google Integrations & Analytics ----------------------------- */

export function useGoogleConnectionStatus(orgId: string, websiteId?: string) {
  return useQuery({
    queryKey: queryKeys.googleStatus(orgId, websiteId ?? ""),
    queryFn: () =>
      api.get<GoogleConnectionStatus>(
        `/organizations/${orgId}/integrations/google/status?websiteId=${websiteId}`,
      ),
    enabled: Boolean(orgId && websiteId),
  });
}

export function useGoogleConnectUrl(orgId: string, websiteId?: string) {
  return useMutation({
    mutationFn: () =>
      api.get<GoogleConnectUrl>(
        `/organizations/${orgId}/integrations/google/connect?websiteId=${websiteId}`,
      ),
  });
}

export function useGoogleCallback(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { websiteId: string; code: string; state: string }) =>
      api.post<GoogleConnectionStatus>(
        `/organizations/${orgId}/integrations/google/callback`,
        body,
      ),
    onSuccess: (_, vars) => {
      void client.invalidateQueries({
        queryKey: queryKeys.googleStatus(orgId, vars.websiteId),
      });
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "analytics"],
      });
    },
  });
}

export function useDisconnectGoogle(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (websiteId: string) =>
      api.delete<void>(
        `/organizations/${orgId}/integrations/google?websiteId=${websiteId}`,
      ),
    onSuccess: (_, websiteId) => {
      void client.invalidateQueries({
        queryKey: queryKeys.googleStatus(orgId, websiteId),
      });
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "analytics"],
      });
    },
  });
}

export function useGoogleAccessibleProperties(orgId: string, websiteId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'integrations', 'google', websiteId, 'properties'],
    queryFn: () =>
      api.get<GoogleAccessibleResources>(
        `/organizations/${orgId}/integrations/google/properties?websiteId=${websiteId}`,
      ),
    enabled: Boolean(orgId && websiteId),
  });
}

export function useSelectGoogleProperties(orgId: string, websiteId?: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: GooglePropertySelectionRequest) =>
      api.post<GoogleConnectionStatus>(
        `/organizations/${orgId}/integrations/google/properties?websiteId=${websiteId}`,
        body,
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: queryKeys.googleStatus(orgId, websiteId ?? ''),
      });
      void client.invalidateQueries({
        queryKey: ['organizations', orgId, 'integrations', 'google', websiteId, 'properties'],
      });
      void client.invalidateQueries({
        queryKey: ['organizations', orgId, 'analytics'],
      });
    },
  });
}

export function useSyncGoogleAnalytics(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (websiteId: string) =>
      api.post<void>(
        `/organizations/${orgId}/integrations/google/sync?websiteId=${websiteId}`,
        {},
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "analytics"],
      });
    },
  });
}

export function useAnalyticsOverview(
  orgId: string,
  websiteId?: string,
  startDate?: string,
  endDate?: string,
) {
  const params = new URLSearchParams();
  if (websiteId) params.append("websiteId", websiteId);
  if (startDate) params.append("startDate", startDate);
  if (endDate) params.append("endDate", endDate);
  const qs = params.toString() ? `?${params.toString()}` : "";

  return useQuery({
    queryKey: queryKeys.analyticsOverview(orgId, websiteId, startDate, endDate),
    queryFn: () =>
      api.get<AnalyticsOverview>(
        `/organizations/${orgId}/analytics/overview${qs}`,
      ),
    enabled: Boolean(orgId),
  });
}

/* ----------------------------- AI Recommendations & Chatbot ----------------------------- */

export function useAiRecommendations(
  orgId: string,
  status?: RecommendationStatus,
  page = 0,
) {
  const qs = status ? `?status=${status}&page=${page}` : `?page=${page}`;
  return useQuery({
    queryKey: queryKeys.aiRecommendations(orgId, status, page),
    queryFn: () =>
      api.get<Page<AiRecommendation>>(
        `/organizations/${orgId}/ai/recommendations${qs}`,
      ),
    enabled: Boolean(orgId),
  });
}

export function useGenerateAiRecommendations(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api.post<AiRecommendation[]>(
        `/organizations/${orgId}/ai/recommendations/generate`,
        {},
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "ai", "recommendations"],
      });
    },
  });
}

export function useUpdateAiRecommendationStatus(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({
      recommendationId,
      status,
    }: {
      recommendationId: string;
      status: RecommendationStatus;
    }) =>
      api.patch<AiRecommendation>(
        `/organizations/${orgId}/ai/recommendations/${recommendationId}/status`,
        { status },
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "ai", "recommendations"],
      });
    },
  });
}

export function useChatbotConfig(orgId: string) {
  return useQuery({
    queryKey: queryKeys.chatbotConfig(orgId),
    queryFn: () =>
      api.get<ChatbotConfig>(`/organizations/${orgId}/chatbot/config`),
    enabled: Boolean(orgId),
  });
}

export function useUpdateChatbotConfig(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: ChatbotConfigRequest) =>
      api.put<ChatbotConfig>(`/organizations/${orgId}/chatbot/config`, body),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: queryKeys.chatbotConfig(orgId),
      });
    },
  });
}

export function usePublicChat(orgSlug: string) {
  return useMutation({
    mutationFn: (body: {
      sessionId: string;
      message: string;
      visitorName?: string;
      visitorEmail?: string;
      visitorPhone?: string;
    }) => api.public<PublicChatMessageResponse>(`/public/chat/${orgSlug}`, body),
  });
}

// ============================================================================
// Marketing Campaigns Hooks
// ============================================================================

export function useCampaigns(orgId: string, status?: string, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);

  return useQuery({
    queryKey: ["organizations", orgId, "campaigns", page, status],
    queryFn: () =>
      api.get<Page<Campaign>>(`/organizations/${orgId}/campaigns?${params}`),
    enabled: Boolean(orgId),
  });
}

export function useCampaign(orgId: string, campaignId: string) {
  return useQuery({
    queryKey: ["organizations", orgId, "campaigns", campaignId],
    queryFn: () =>
      api.get<Campaign>(`/organizations/${orgId}/campaigns/${campaignId}`),
    enabled: Boolean(orgId && campaignId),
  });
}

export function useCreateCampaign(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateCampaignRequest) =>
      api.post<Campaign>(`/organizations/${orgId}/campaigns`, body),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "campaigns"],
      });
    },
  });
}

export function useUpdateCampaign(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({
      campaignId,
      body,
    }: {
      campaignId: string;
      body: UpdateCampaignRequest;
    }) =>
      api.put<Campaign>(
        `/organizations/${orgId}/campaigns/${campaignId}`,
        body,
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "campaigns"],
      });
    },
  });
}

export function useDeleteCampaign(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (campaignId: string) =>
      api.delete<void>(`/organizations/${orgId}/campaigns/${campaignId}`),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "campaigns"],
      });
    },
  });
}

export function useCampaignPerformance(orgId: string, campaignId: string) {
  return useQuery({
    queryKey: ["organizations", orgId, "campaigns", campaignId, "performance"],
    queryFn: () =>
      api.get<CampaignPerformance>(`/organizations/${orgId}/campaigns/${campaignId}/performance`),
    enabled: Boolean(orgId && campaignId),
  });
}

// ============================================================================
// Agency Workspace Hooks
// ============================================================================

export function useAgencyClients(orgId: string) {
  return useQuery({
    queryKey: ["organizations", orgId, "agency", "clients"],
    queryFn: () =>
      api.get<AgencyClientSummary[]>(`/organizations/${orgId}/agency/clients`),
    enabled: Boolean(orgId),
  });
}

export function useAgencyOverview(orgId: string) {
  return useQuery({
    queryKey: ["organizations", orgId, "agency", "overview"],
    queryFn: () =>
      api.get<AgencyOverview>(`/organizations/${orgId}/agency/overview`),
    enabled: Boolean(orgId),
  });
}

export function useLinkAgencyClient(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: LinkClientRequest) =>
      api.post<AgencyClientSummary>(`/organizations/${orgId}/agency/clients`, body),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "agency"],
      });
    },
  });
}

export function useUpdateAgencyClient(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({
      relationshipId,
      body,
    }: {
      relationshipId: string;
      body: UpdateClientRelationshipRequest;
    }) =>
      api.put<AgencyClientSummary>(`/organizations/${orgId}/agency/clients/${relationshipId}`, body),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "agency"],
      });
    },
  });
}

export function useRevokeAgencyClient(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (relationshipId: string) =>
      api.delete<void>(`/organizations/${orgId}/agency/clients/${relationshipId}`),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "agency"],
      });
    },
  });
}

// ============================================================================
// Cross-Module Reports Hooks
// ============================================================================

export function useComprehensiveReport(orgId: string) {
  return useQuery({
    queryKey: ["organizations", orgId, "reports", "comprehensive"],
    queryFn: () =>
      api.get<ComprehensiveReport>(`/organizations/${orgId}/reports/comprehensive`),
    enabled: Boolean(orgId),
  });
}

/* -------------------------------- billing hooks -------------------------------- */

export function usePlans() {
  return useQuery({
    queryKey: ["billing", "plans"],
    queryFn: () => api.get<SubscriptionPlan[]>("/billing/plans"),
  });
}

export function useSubscription(orgId: string) {
  return useQuery({
    queryKey: ["organizations", orgId, "billing", "subscription"],
    queryFn: () =>
      api.get<OrganizationSubscription>(
        `/organizations/${orgId}/billing/subscription`,
      ),
    enabled: Boolean(orgId),
  });
}

export function useInitializePayment(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: InitializePaymentRequest) =>
      api.post<InitializePaymentResponse>(
        `/organizations/${orgId}/billing/checkout`,
        body,
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "billing"],
      });
    },
  });
}

export function useVerifyPayment(orgId: string, txRef: string) {
  return useQuery({
    queryKey: ["organizations", orgId, "billing", "verify", txRef],
    queryFn: () =>
      api.get<VerifyPaymentResponse>(
        `/organizations/${orgId}/billing/verify/${txRef}`,
      ),
    enabled: Boolean(orgId && txRef),
  });
}

export function useCancelSubscription(orgId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api.post<OrganizationSubscription>(
        `/organizations/${orgId}/billing/cancel`,
        {},
      ),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["organizations", orgId, "billing"],
      });
    },
  });
}

export function usePaymentTransactions(orgId: string, page = 0, size = 20) {
  return useQuery({
    queryKey: ["organizations", orgId, "billing", "transactions", page],
    queryFn: () =>
      api.get<Page<PaymentTransaction>>(
        `/organizations/${orgId}/billing/transactions?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId),
  });
}

export function useBillingHistory(orgId: string, page = 0, size = 20) {
  return useQuery({
    queryKey: ["organizations", orgId, "billing", "history", page],
    queryFn: () =>
      api.get<Page<BillingHistory>>(
        `/organizations/${orgId}/billing/history?page=${page}&size=${size}`,
      ),
    enabled: Boolean(orgId),
  });
}
