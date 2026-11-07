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
  Dashboard,
  Invitation,
  InvitationAcceptance,
  Member,
  MembershipRole,
  MembershipStatus,
  Organization,
  Page,
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

/** Soft deactivation (DELETE keeps the row, status -> INACTIVE). */
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
      api.get<Page<Member>>(`/organizations/${orgId}/members?page=${page}&size=${size}`),
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
