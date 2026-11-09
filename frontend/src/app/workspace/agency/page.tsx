"use client";

import { useState } from "react";
import { useAuth } from "@/lib/auth/auth-context";
import {
  useAgencyOverview,
  useLinkAgencyClient,
  useUpdateAgencyClient,
  useRevokeAgencyClient,
} from "@/lib/api/hooks";
import type { AgencyAccessLevel, AgencyClientSummary } from "@/lib/api/types";
import { Badge, Button, EmptyState, ErrorCallout, Input } from "@/components/ui";

function formatEtb(value?: number): string {
  if (value === undefined || value === null) return "ETB 0.00";
  return `ETB ${Number(value).toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export default function AgencyPage() {
  const { currentOrg, selectOrg, memberships } = useAuth();
  const orgId = currentOrg?.organizationId ?? "";

  const [showLinkModal, setShowLinkModal] = useState(false);
  const [clientSlug, setClientSlug] = useState("");
  const [accessLevel, setAccessLevel] = useState<AgencyAccessLevel>("FULL");
  const [notes, setNotes] = useState("");
  const [formError, setFormError] = useState("");

  const { data: overview, isLoading, error } = useAgencyOverview(orgId);
  const linkMutation = useLinkAgencyClient(orgId);
  const updateMutation = useUpdateAgencyClient(orgId);
  const revokeMutation = useRevokeAgencyClient(orgId);

  const handleLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError("");
    if (!clientSlug.trim()) {
      setFormError("Client organization slug is required");
      return;
    }

    try {
      await linkMutation.mutateAsync({
        clientOrganizationSlug: clientSlug.trim().toLowerCase(),
        accessLevel,
        notes: notes.trim() || undefined,
      });
      setShowLinkModal(false);
      setClientSlug("");
      setNotes("");
    } catch (err: unknown) {
      setFormError((err as Error).message ?? "Failed to link client organization");
    }
  };

  const handleRevoke = async (relationshipId: string) => {
    if (confirm("Are you sure you want to revoke agency access to this client?")) {
      await revokeMutation.mutateAsync(relationshipId);
    }
  };

  const handleSwitchToClient = (clientOrgId: string) => {
    const hasMembership = memberships.some((m) => m.organizationId === clientOrgId);
    if (hasMembership) {
      selectOrg(clientOrgId);
    } else {
      alert("You need an active team membership in this client organization to switch directly.");
    }
  };

  const clients = overview?.clients ?? [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Agency Client Workspace
          </h1>
          <p className="text-sm text-slate-500">
            Manage multiple client organizations, unified performance reporting, and multi-tenant access.
          </p>
        </div>
        <Button onClick={() => setShowLinkModal(true)}>+ Link Client Organization</Button>
      </div>

      {/* KPI Overview Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Managed Clients
          </p>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-semibold text-slate-900">
              {overview?.activeClients ?? 0}
            </span>
            <span className="text-xs text-slate-500">
              of {overview?.totalClients ?? 0} total
            </span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Client Websites
          </p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {overview?.totalManagedWebsites ?? 0}
          </p>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Aggregated Leads
          </p>
          <p className="mt-2 text-2xl font-semibold text-emerald-600">
            {overview?.totalAggregatedLeads ?? 0}
          </p>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Avg Client SEO Score
          </p>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-semibold text-indigo-600">
              {overview?.averageClientSeoScore ? `${overview.averageClientSeoScore}/100` : "N/A"}
            </span>
          </div>
        </div>
      </div>

      {/* Error Callout */}
      {error && <ErrorCallout message={(error as Error).message} />}

      {/* Clients Table */}
      {isLoading ? (
        <div className="py-12 text-center text-sm text-slate-500">
          Loading client organizations...
        </div>
      ) : clients.length === 0 ? (
        <EmptyState
          title="No client organizations linked"
          description="Link client organizations to your agency account to monitor SEO audits, traffic, and CRM pipelines in one unified portal."
          action={<Button onClick={() => setShowLinkModal(true)}>Link Client</Button>}
        />
      ) : (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
            <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-6 py-3">Client Organization</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3">Access Level</th>
                <th className="px-6 py-3">Websites</th>
                <th className="px-6 py-3">SEO Health</th>
                <th className="px-6 py-3">Leads & Pipeline</th>
                <th className="px-6 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {clients.map((c: AgencyClientSummary) => (
                <tr key={c.relationshipId} className="hover:bg-slate-50/50 transition">
                  <td className="px-6 py-4">
                    <div>
                      <p className="font-semibold text-slate-900">{c.clientName}</p>
                      <p className="text-xs font-mono text-slate-400">{c.clientSlug}</p>
                      {c.clientIndustry && (
                        <span className="mt-1 inline-block text-[10px] text-slate-500">
                          {c.clientIndustry}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <Badge status={c.status} />
                  </td>
                  <td className="px-6 py-4">
                    <Badge status={c.accessLevel} />
                  </td>
                  <td className="px-6 py-4 font-medium text-slate-700">
                    {c.websiteCount}
                  </td>
                  <td className="px-6 py-4">
                    {c.latestSeoScore !== undefined && c.latestSeoScore !== null ? (
                      <span
                        className={`inline-flex rounded-full px-2 py-0.5 text-xs font-bold ${
                          c.latestSeoScore >= 80
                            ? "bg-emerald-50 text-emerald-700"
                            : c.latestSeoScore >= 50
                            ? "bg-amber-50 text-amber-700"
                            : "bg-rose-50 text-rose-700"
                        }`}
                      >
                        {c.latestSeoScore}/100
                      </span>
                    ) : (
                      <span className="text-xs text-slate-400">No audits</span>
                    )}
                  </td>
                  <td className="px-6 py-4">
                    <p className="font-semibold text-slate-900">{c.totalLeads} leads</p>
                    <p className="text-xs text-slate-500">{formatEtb(c.totalPipelineValueEtb)}</p>
                  </td>
                  <td className="px-6 py-4 text-right space-x-2">
                    <button
                      onClick={() => handleSwitchToClient(c.clientOrganizationId)}
                      className="rounded-lg bg-slate-900 px-2.5 py-1 text-xs font-semibold text-white hover:bg-slate-800 transition"
                    >
                      Open Workspace
                    </button>
                    {c.status === "ACTIVE" && (
                      <button
                        onClick={() => handleRevoke(c.relationshipId)}
                        className="text-xs text-rose-600 hover:text-rose-800"
                      >
                        Revoke
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Link Client Modal */}
      {showLinkModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
            <h2 className="text-lg font-bold text-slate-900">Link Client Organization</h2>
            <p className="mt-1 text-xs text-slate-500">
              Enter the unique URL slug of the client organization you wish to manage.
            </p>

            {formError && <div className="mt-3"><ErrorCallout message={formError} /></div>}

            <form onSubmit={handleLink} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-700">
                  Client Organization Slug *
                </label>
                <Input
                  value={clientSlug}
                  onChange={(e) => setClientSlug(e.target.value)}
                  placeholder="e.g. ethio-real-estate"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-700">Access Level</label>
                <select
                  value={accessLevel}
                  onChange={(e) => setAccessLevel(e.target.value as AgencyAccessLevel)}
                  className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
                >
                  <option value="FULL">FULL (Websites, SEO, CRM & Analytics)</option>
                  <option value="MARKETING_ONLY">MARKETING_ONLY (SEO & Campaigns)</option>
                  <option value="READ_ONLY">READ_ONLY (Analytics & Reports)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-700">Internal Agency Notes</label>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="e.g. Contract signed Q4 2026 for digital marketing management"
                  rows={2}
                  className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
                />
              </div>

              <div className="mt-6 flex justify-end gap-2">
                <Button
                  variant="secondary"
                  type="button"
                  onClick={() => setShowLinkModal(false)}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={linkMutation.isPending}>
                  {linkMutation.isPending ? "Linking..." : "Link Client"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
