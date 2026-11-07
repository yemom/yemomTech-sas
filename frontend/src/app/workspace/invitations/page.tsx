"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorCallout,
  Field,
  Input,
  PageLoader,
  Select,
} from "@/components/ui";
import { ApiError, errorMessage } from "@/lib/api/client";
import {
  useAcceptInvitation,
  useCreateInvitation,
  useInvitations,
  useRevokeInvitation,
} from "@/lib/api/hooks";
import { INVITABLE_ROLES, ROLE_LABELS, can } from "@/lib/api/permissions";
import {
  acceptInviteSchema,
  inviteSchema,
  type AcceptInviteInput,
  type InviteInput,
} from "@/lib/api/schemas";
import { useAuth } from "@/lib/auth/auth-context";

const dateFormatter = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "Africa/Addis_Ababa",
});

export default function InvitationsPage() {
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? null;
  const [page, setPage] = useState(0);
  const invitations = useInvitations(orgId, page);
  const revoke = useRevokeInvitation(orgId ?? "-");
  const [actionError, setActionError] = useState<string | null>(null);

  const mayManage = currentOrg ? can(currentOrg.role, "INVITE_MANAGE") : false;

  if (!currentOrg) return <PageLoader label="Select a workspace…" />;
  if (invitations.isPending) return <PageLoader label="Loading invitations…" />;

  const data = invitations.data;
  const rows = data?.content ?? [];

  async function revokeInvite(invitationId: string) {
    setActionError(null);
    try {
      await revoke.mutateAsync(invitationId);
    } catch (error) {
      setActionError(errorMessage(error));
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Invitations</h1>
        <p className="mt-1 text-sm text-slate-500">
          Invite teammates by email. Invitations expire after 7 days and are
          bound to the exact address they were sent to.
        </p>
      </div>

      {actionError && <ErrorCallout message={actionError} />}

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Accept a pending invitation (the page the emailed link points to). */}
        <Card>
          <h2 className="text-base font-semibold text-slate-900">
            Accept an invitation
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            Paste the token from your invitation email to join another
            workspace.
          </p>
          <AcceptInviteForm />
        </Card>

        {mayManage && (
          <div className="lg:col-span-2">
            <InviteForm orgId={currentOrg.organizationId} />
          </div>
        )}
      </div>

      <Card className="overflow-x-auto p-0">
        {rows.length === 0 ? (
          <div className="p-6">
            <EmptyState
              title="No invitations yet"
              description={
                mayManage
                  ? "Invite your first teammate — they will receive an email with an acceptance link."
                  : "No invitations have been created in this workspace."
              }
            />
          </div>
        ) : (
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-slate-600">
                  Email
                </th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">
                  Role
                </th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">
                  State
                </th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">
                  Expires
                </th>
                {mayManage && (
                  <th className="px-4 py-3 text-right font-medium text-slate-600">
                    Actions
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {rows.map((invitation) => (
                <tr key={invitation.id}>
                  <td className="px-4 py-3 font-medium text-slate-900">
                    {invitation.email}
                  </td>
                  <td className="px-4 py-3 text-slate-700">
                    {ROLE_LABELS[invitation.role]}
                  </td>
                  <td className="px-4 py-3">
                    <Badge
                      status={
                        invitation.state === "PENDING"
                          ? "PENDING"
                          : invitation.state
                      }
                    >
                      {invitation.state}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {invitation.state === "PENDING" && invitation.expiresAt
                      ? dateFormatter.format(new Date(invitation.expiresAt))
                      : "—"}
                  </td>
                  {mayManage && (
                    <td className="px-4 py-3 text-right">
                      {invitation.state === "PENDING" ? (
                        <RevokeButton
                          loading={revoke.isPending}
                          onRevoke={() => void revokeInvite(invitation.id)}
                        />
                      ) : (
                        <span className="text-xs text-slate-400">—</span>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-500">
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              disabled={data.first}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <Button
              variant="secondary"
              disabled={data.last}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

/* -------------------------------- create -------------------------------- */

function InviteForm({ orgId }: { orgId: string }) {
  const create = useCreateInvitation(orgId);
  const [formError, setFormError] = useState<string | null>(null);
  const [created, setCreated] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InviteInput>({ resolver: zodResolver(inviteSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    setCreated(null);
    try {
      const invitation = await create.mutateAsync({
        email: values.email.trim(),
        role: values.role,
      });
      reset();
      setCreated(invitation.email);
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as keyof InviteInput, { message });
        }
        return;
      }
      if (error instanceof ApiError && error.status === 409) {
        setFormError(
          "That person is already a member, or an invitation is already pending.",
        );
        return;
      }
      setFormError(errorMessage(error));
    }
  });

  return (
    <Card>
      <h2 className="text-base font-semibold text-slate-900">
        Invite a teammate
      </h2>
      <form onSubmit={onSubmit} noValidate className="mt-4 space-y-4">
        {formError && <ErrorCallout message={formError} />}
        {created && (
          <div
            className="rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-800 ring-1 ring-inset ring-emerald-200"
            role="status"
          >
            Invitation created for {created}. They have 7 days to accept.
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label="Email"
            error={errors.email?.message}
            htmlFor="invite-email"
            required
          >
            <Input
              id="invite-email"
              type="email"
              placeholder="teammate@business.et"
              invalid={Boolean(errors.email)}
              {...register("email")}
            />
          </Field>
          <Field
            label="Role"
            error={errors.role?.message}
            htmlFor="invite-role"
            required
          >
            <Select id="invite-role" invalid={Boolean(errors.role)} {...register("role")}>
              <option value="">Choose a role…</option>
              {INVITABLE_ROLES.map((role) => (
                <option key={role} value={role}>
                  {ROLE_LABELS[role]}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <div className="flex items-center gap-3">
          <Button
            type="submit"
            loading={isSubmitting || create.isPending}
          >
            Send invitation
          </Button>
          <span className="text-xs text-slate-400">
            Owner role cannot be granted by invitation.
          </span>
        </div>
      </form>
    </Card>
  );
}

/* -------------------------------- accept -------------------------------- */

function AcceptInviteForm() {
  const { reloadMe, selectOrg } = useAuth();
  const accept = useAcceptInvitation();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AcceptInviteInput>({ resolver: zodResolver(acceptInviteSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const joined = await accept.mutateAsync(values.token.trim());
      // Reload memberships so the new workspace appears, then select it.
      await reloadMe();
      selectOrg(joined.organizationId);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFormError(
          "This invitation is not valid — it may have expired, been revoked, or already used.",
        );
        return;
      }
      if (error instanceof ApiError && error.status === 403) {
        // Signed in as someone else (invitations are email-bound).
        setFormError(
          "This invitation was sent to a different email address. Sign in with that account first.",
        );
        return;
      }
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as keyof AcceptInviteInput, { message });
        }
        return;
      }
      setFormError(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="mt-4 space-y-4">
      {formError && <ErrorCallout message={formError} />}
      <Field
        label="Invitation token"
        error={errors.token?.message}
        htmlFor="invite-token"
        hint="Found in your invitation email link."
        required
      >
        <Input
          id="invite-token"
          placeholder="Paste the token"
          invalid={Boolean(errors.token)}
          {...register("token")}
        />
      </Field>
      <Button
        type="submit"
        loading={isSubmitting || accept.isPending}
        className="w-full"
      >
        Join workspace
      </Button>
    </form>
  );
}

/* -------------------------------- revoke -------------------------------- */

function RevokeButton({
  loading,
  onRevoke,
}: {
  loading: boolean;
  onRevoke: () => void;
}) {
  const [confirming, setConfirming] = useState(false);

  if (!confirming) {
    return (
      <Button variant="danger" onClick={() => setConfirming(true)}>
        Revoke
      </Button>
    );
  }
  return (
    <span className="inline-flex items-center gap-2">
      <Button variant="danger" loading={loading} onClick={onRevoke}>
        Yes, revoke
      </Button>
      <Button variant="ghost" onClick={() => setConfirming(false)}>
        Cancel
      </Button>
    </span>
  );
}
