"use client";

import { useState } from "react";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorCallout,
  LinkButton,
  PageLoader,
} from "@/components/ui";
import { errorMessage } from "@/lib/api/client";
import { useMembers, useRemoveMember, useUpdateMember } from "@/lib/api/hooks";
import { ROLE_LABELS, can } from "@/lib/api/permissions";
import { useAuth } from "@/lib/auth/auth-context";
import type { Member, MembershipRole, MembershipStatus } from "@/lib/api/types";

const ROLE_OPTIONS: MembershipRole[] = [
  "BUSINESS_OWNER",
  "BUSINESS_ADMIN",
  "MARKETING_MANAGER",
  "SALES_MANAGER",
  "SALES_AGENT",
  "ANALYST",
];

const dateFormatter = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "Africa/Addis_Ababa",
});

export default function MembersPage() {
  const { currentOrg, user, reloadMe } = useAuth();
  const orgId = currentOrg?.organizationId ?? null;
  const [page, setPage] = useState(0);
  const members = useMembers(orgId, page);
  const updateMember = useUpdateMember(orgId ?? "-");
  const removeMember = useRemoveMember(orgId ?? "-");
  const [actionError, setActionError] = useState<string | null>(null);

  const mayManage = currentOrg ? can(currentOrg.role, "MEMBER_MANAGE") : false;

  if (!currentOrg) return <PageLoader label="Select a workspace…" />;
  if (members.isPending) return <PageLoader label="Loading members…" />;

  const data = members.data;
  const rows = data?.content ?? [];

  async function changeRole(member: Member, role: MembershipRole) {
    setActionError(null);
    try {
      await updateMember.mutateAsync({ memberId: member.id, role });
      // Role changes can affect our own access (e.g. demoted by another owner).
      await reloadMe();
    } catch (error) {
      setActionError(errorMessage(error));
    }
  }

  async function changeStatus(member: Member, status: MembershipStatus) {
    setActionError(null);
    try {
      await updateMember.mutateAsync({ memberId: member.id, status });
      await reloadMe();
    } catch (error) {
      setActionError(errorMessage(error));
    }
  }

  async function remove(member: Member) {
    setActionError(null);
    try {
      await removeMember.mutateAsync(member.id);
      await reloadMe();
    } catch (error) {
      setActionError(errorMessage(error));
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Members</h1>
          <p className="mt-1 text-sm text-slate-500">
            Everyone with access to this workspace. Roles decide what each
            person can do — the last owner always stays protected.
          </p>
        </div>
        <LinkButton href="/workspace/invitations">Invite teammate</LinkButton>
      </div>

      {actionError && <ErrorCallout message={actionError} />}

      {rows.length === 0 ? (
        <EmptyState
          title="No members yet"
          description="Invite the first teammate to this workspace."
        />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-slate-600">Member</th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">Role</th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">Status</th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">Joined</th>
                {mayManage && (
                  <th className="px-4 py-3 text-right font-medium text-slate-600">Actions</th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {rows.map((member) => {
                const isSelf = member.userId === user?.id;
                const isOwner = member.role === "BUSINESS_OWNER";
                return (
                  <tr key={member.id}>
                    <td className="px-4 py-3">
                      <p className="font-medium text-slate-900">
                        {member.firstName} {member.lastName}
                        {isSelf && (
                          <span className="ml-2 text-xs font-normal text-slate-400">(you)</span>
                        )}
                      </p>
                      <p className="text-xs text-slate-500">{member.email}</p>
                    </td>
                    <td className="px-4 py-3">
                      {mayManage && !isSelf && !isOwner ? (
                        <select
                          value={member.role}
                          onChange={(e) => void changeRole(member, e.target.value as MembershipRole)}
                          disabled={updateMember.isPending}
                          aria-label={`Role for ${member.firstName}`}
                          className="rounded-lg border-0 bg-white px-2 py-1.5 text-sm text-slate-900 ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-teal-600"
                        >
                          {ROLE_OPTIONS.map((role) => (
                            <option key={role} value={role}>
                              {ROLE_LABELS[role]}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span className="text-slate-700">{ROLE_LABELS[member.role]}</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <Badge status={member.status} />
                    </td>
                    <td className="px-4 py-3 text-slate-500">
                      {dateFormatter.format(new Date(member.joinedAt))}
                    </td>
                    {mayManage && (
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex items-center gap-2">
                          {member.status === "ACTIVE" ? (
                            <Button
                              variant="ghost"
                              disabled={isSelf || isOwner || removeMember.isPending}
                              title={
                                isSelf
                                  ? "You cannot suspend yourself."
                                  : isOwner
                                    ? "Suspend an admin instead — owners cannot be suspended."
                                    : undefined
                              }
                              onClick={() => void changeStatus(member, "SUSPENDED")}
                            >
                              Suspend
                            </Button>
                          ) : (
                            <Button
                              variant="ghost"
                              disabled={removeMember.isPending}
                              onClick={() => void changeStatus(member, "ACTIVE")}
                            >
                              Reactivate
                            </Button>
                          )}
                          <Button
                            variant="danger"
                            disabled={isSelf || isOwner || removeMember.isPending}
                            title={
                              isSelf
                                ? "You cannot remove yourself here."
                                : isOwner
                                  ? "The last owner cannot be removed."
                                  : undefined
                            }
                            onClick={() => void remove(member)}
                          >
                            Remove
                          </Button>
                        </div>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-500">
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button variant="secondary" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
