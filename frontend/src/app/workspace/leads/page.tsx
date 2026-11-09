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
} from "@/components/ui";
import { errorMessage } from "@/lib/api/client";
import {
  useAddLeadActivity,
  useArchiveLead,
  useCreateLead,
  useLeadActivities,
  useLeads,
  usePipelineSummary,
  useTransitionLeadStage,
} from "@/lib/api/hooks";
import { can } from "@/lib/api/permissions";
import {
  leadSchema,
  type LeadInput,
} from "@/lib/api/schemas";
import { useAuth } from "@/lib/auth/auth-context";
import type { Lead, LeadStage } from "@/lib/api/types";

const STAGES: { key: LeadStage; label: string }[] = [
  { key: "NEW", label: "New Inquiry" },
  { key: "CONTACTED", label: "Contacted" },
  { key: "QUALIFIED", label: "Qualified" },
  { key: "PROPOSAL", label: "Proposal Sent" },
  { key: "NEGOTIATION", label: "Negotiating" },
  { key: "WON", label: "Won / Converted" },
  { key: "LOST", label: "Lost" },
];

export default function LeadsPage() {
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? null;

  const [viewMode, setViewMode] = useState<"board" | "table">("board");
  const [search, setSearch] = useState("");
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEmbedModal, setShowEmbedModal] = useState(false);

  const leadsQuery = useLeads(orgId, undefined, search || undefined, 0, 100);
  const summaryQuery = usePipelineSummary(orgId);

  const mayManage = currentOrg ? can(currentOrg.role, "LEAD_MANAGE") : false;

  if (!currentOrg) return <PageLoader label="Select a workspace…" />;
  if (leadsQuery.isPending && !leadsQuery.data) return <PageLoader label="Loading leads & pipeline…" />;

  const leads = leadsQuery.data?.content ?? [];
  const summary = summaryQuery.data;

  // Group leads by stage for the Kanban view
  const leadsByStage: Record<LeadStage, Lead[]> = {
    NEW: [],
    CONTACTED: [],
    QUALIFIED: [],
    PROPOSAL: [],
    NEGOTIATION: [],
    WON: [],
    LOST: [],
  };

  leads.forEach((l) => {
    if (leadsByStage[l.stage]) {
      leadsByStage[l.stage].push(l);
    }
  });

  function formatEtb(amount: number | null | undefined): string {
    if (amount == null) return "ETB 0";
    return new Intl.NumberFormat("en-ET", {
      style: "currency",
      currency: "ETB",
      maximumFractionDigits: 0,
    }).format(amount);
  }

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Leads & Sales Pipeline</h1>
          <p className="mt-1 text-sm text-slate-500">
            Organize customer inquiries, track deal values in ETB, and move prospects through your conversion funnel.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={() => setShowEmbedModal(true)}>
            Web Form Code
          </Button>
          {mayManage && (
            <Button onClick={() => setShowCreateModal(true)}>+ Add New Lead</Button>
          )}
        </div>
      </div>

      {/* Metrics Bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="p-4 bg-gradient-to-br from-slate-900 to-slate-800 text-white shadow-sm">
          <p className="text-xs uppercase tracking-wider text-slate-400 font-medium">Total Pipeline Value</p>
          <p className="text-2xl font-black mt-1 text-emerald-400">
            {formatEtb(summary?.totalPipelineValueEtb ?? 0)}
          </p>
          <p className="text-xs text-slate-400 mt-1">Across all open and closed stages</p>
        </Card>

        <Card className="p-4">
          <p className="text-xs uppercase tracking-wider text-slate-500 font-medium">Total Leads Tracked</p>
          <p className="text-2xl font-bold mt-1 text-slate-900">
            {summary?.totalLeads ?? leads.length}
          </p>
          <p className="text-xs text-slate-500 mt-1">Active prospective clients</p>
        </Card>

        <Card className="p-4">
          <p className="text-xs uppercase tracking-wider text-slate-500 font-medium">Won Deals (Converted)</p>
          <p className="text-2xl font-bold mt-1 text-emerald-600">
            {summary?.stages?.WON?.count ?? 0}
          </p>
          <p className="text-xs text-slate-500 mt-1">
            Value: {formatEtb(summary?.stages?.WON?.totalValue ?? 0)}
          </p>
        </Card>
      </div>

      {/* Search and View Controls */}
      <div className="flex flex-wrap items-center justify-between gap-4 bg-white p-3 rounded-xl border border-slate-200">
        <div className="w-full md:w-80">
          <input
            type="search"
            placeholder="Search leads by name, email, phone, company…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-teal-600"
          />
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-500 font-medium">View:</span>
          <div className="inline-flex rounded-lg border border-slate-200 p-0.5 bg-slate-50">
            <button
              onClick={() => setViewMode("board")}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                viewMode === "board"
                  ? "bg-white text-slate-900 shadow-sm"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              Kanban Board
            </button>
            <button
              onClick={() => setViewMode("table")}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                viewMode === "table"
                  ? "bg-white text-slate-900 shadow-sm"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              Table List
            </button>
          </div>
        </div>
      </div>

      {/* Kanban Board View */}
      {viewMode === "board" && (
        <div className="flex gap-4 overflow-x-auto pb-4 items-start">
          {STAGES.map((col) => {
            const colLeads = leadsByStage[col.key] || [];
            const colMetrics = summary?.stages?.[col.key];

            return (
              <div
                key={col.key}
                className="w-72 shrink-0 bg-slate-50/90 rounded-xl p-3 border border-slate-200 space-y-3"
              >
                {/* Column Header */}
                <div className="flex items-center justify-between pb-1 border-b border-slate-200">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-slate-800">{col.label}</span>
                    <span className="text-xs px-1.5 py-0.2 rounded-full bg-slate-200 text-slate-700 font-semibold">
                      {colLeads.length}
                    </span>
                  </div>
                  <span className="text-[10px] font-medium text-slate-500">
                    {formatEtb(colMetrics?.totalValue ?? 0)}
                  </span>
                </div>

                {/* Column Cards */}
                <div className="space-y-2.5 min-h-[120px]">
                  {colLeads.length === 0 ? (
                    <div className="text-center py-6 text-xs text-slate-400 italic">
                      No leads
                    </div>
                  ) : (
                    colLeads.map((lead) => (
                      <Card
                        key={lead.id}
                        onClick={() => setSelectedLead(lead)}
                        className="p-3 cursor-pointer hover:shadow-md transition-shadow space-y-2 border-slate-200"
                      >
                        <div className="flex items-start justify-between gap-1">
                          <p className="text-xs font-bold text-slate-900">
                            {lead.firstName} {lead.lastName || ""}
                          </p>
                          <Badge status={lead.stage} />
                        </div>

                        {lead.companyName && (
                          <p className="text-[11px] text-slate-600 truncate font-medium">
                            {lead.companyName}
                          </p>
                        )}

                        <div className="flex items-center justify-between pt-1 border-t border-slate-100 text-[11px]">
                          <span className="font-semibold text-emerald-700">
                            {formatEtb(lead.estimatedValueEtb)}
                          </span>
                          <span className="text-slate-400 text-[10px]">
                            {new Intl.DateTimeFormat("en-GB", {
                              day: "numeric",
                              month: "short",
                            }).format(new Date(lead.createdAt))}
                          </span>
                        </div>
                      </Card>
                    ))
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Table List View */}
      {viewMode === "table" && (
        <Card className="overflow-hidden">
          {leads.length === 0 ? (
            <EmptyState
              title="No leads found"
              description="No leads match your current search query."
            />
          ) : (
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-3">Lead Name</th>
                  <th className="px-4 py-3">Company</th>
                  <th className="px-4 py-3">Contact</th>
                  <th className="px-4 py-3">Stage</th>
                  <th className="px-4 py-3">Value (ETB)</th>
                  <th className="px-4 py-3">Source</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {leads.map((lead) => (
                  <tr
                    key={lead.id}
                    onClick={() => setSelectedLead(lead)}
                    className="hover:bg-slate-50/80 cursor-pointer"
                  >
                    <td className="px-4 py-3 font-medium text-slate-900">
                      {lead.firstName} {lead.lastName || ""}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{lead.companyName || "—"}</td>
                    <td className="px-4 py-3 text-xs text-slate-600 space-y-0.5">
                      {lead.email && <p>{lead.email}</p>}
                      {lead.phoneNumber && <p className="font-mono">{lead.phoneNumber}</p>}
                    </td>
                    <td className="px-4 py-3">
                      <Badge status={lead.stage} />
                    </td>
                    <td className="px-4 py-3 font-medium text-slate-900">
                      {formatEtb(lead.estimatedValueEtb)}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500">{lead.source}</td>
                    <td className="px-4 py-3 text-right">
                      <Button variant="ghost">View</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      )}

      {/* Lead Detail Drawer / Modal */}
      {selectedLead && (
        <LeadDetailModal
          orgId={currentOrg.organizationId}
          lead={selectedLead}
          mayManage={mayManage}
          onClose={() => setSelectedLead(null)}
          onUpdated={(updated) => setSelectedLead(updated)}
        />
      )}

      {/* Create Lead Modal */}
      {showCreateModal && (
        <CreateLeadModal
          orgId={currentOrg.organizationId}
          onClose={() => setShowCreateModal(false)}
        />
      )}

      {/* Public Embed Form Generator Modal */}
      {showEmbedModal && (
        <EmbedFormModal
          orgSlug={currentOrg.slug}
          onClose={() => setShowEmbedModal(false)}
        />
      )}
    </div>
  );
}

/* ------------------------------ modals & subcomponents ------------------------------ */

function LeadDetailModal({
  orgId,
  lead,
  mayManage,
  onClose,
  onUpdated,
}: {
  orgId: string;
  lead: Lead;
  mayManage: boolean;
  onClose: () => void;
  onUpdated: (updated: Lead) => void;
}) {
  const transition = useTransitionLeadStage(orgId, lead.id);
  const archive = useArchiveLead(orgId);
  const activitiesQuery = useLeadActivities(orgId, lead.id);
  const addActivity = useAddLeadActivity(orgId, lead.id);

  const [activeTab, setActiveTab] = useState<"info" | "timeline">("info");
  const [stageNotes, setStageNotes] = useState("");
  const [newActivityType, setNewActivityType] = useState("NOTE");
  const [newActivityDesc, setNewActivityDesc] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  async function handleStageChange(newStage: LeadStage) {
    setActionError(null);
    try {
      const res = await transition.mutateAsync({
        stage: newStage,
        notes: stageNotes || undefined,
      });
      onUpdated(res);
      setStageNotes("");
    } catch (err) {
      setActionError(errorMessage(err));
    }
  }

  async function handleAddActivity(e: React.FormEvent) {
    e.preventDefault();
    if (!newActivityDesc.trim()) return;
    setActionError(null);
    try {
      await addActivity.mutateAsync({
        activityType: newActivityType,
        description: newActivityDesc.trim(),
      });
      setNewActivityDesc("");
    } catch (err) {
      setActionError(errorMessage(err));
    }
  }

  async function handleArchive() {
    if (!confirm("Are you sure you want to archive this lead?")) return;
    try {
      await archive.mutateAsync(lead.id);
      onClose();
    } catch (err) {
      setActionError(errorMessage(err));
    }
  }

  const activities = activitiesQuery.data?.content ?? [];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-2xl bg-white rounded-xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="flex items-center justify-between p-4 border-b border-slate-200 bg-slate-50">
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              {lead.firstName} {lead.lastName || ""}
            </h2>
            <p className="text-xs text-slate-500">
              {lead.companyName || "Individual Prospect"} • Created on{" "}
              {new Intl.DateTimeFormat("en-GB", {
                dateStyle: "medium",
                timeStyle: "short",
                timeZone: "Africa/Addis_Ababa",
              }).format(new Date(lead.createdAt))}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-xl font-bold p-1"
          >
            ✕
          </button>
        </div>

        {actionError && (
          <div className="p-4 border-b border-rose-100">
            <ErrorCallout message={actionError} />
          </div>
        )}

        {/* Stage Progression Bar */}
        <div className="p-4 bg-slate-50 border-b border-slate-200">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold text-slate-700">Pipeline Stage:</span>
            <Badge status={lead.stage} />
          </div>
          {mayManage && (
            <div className="flex flex-wrap gap-1.5">
              {STAGES.map((s) => (
                <button
                  key={s.key}
                  disabled={transition.isPending}
                  onClick={() => handleStageChange(s.key)}
                  className={`text-xs px-2.5 py-1 rounded-md font-medium transition-all ${
                    lead.stage === s.key
                      ? "bg-slate-900 text-white shadow-sm"
                      : "bg-white text-slate-600 border border-slate-200 hover:bg-slate-100"
                  }`}
                >
                  {s.label}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Tab Selector */}
        <div className="flex border-b border-slate-200 px-4 gap-4">
          <button
            onClick={() => setActiveTab("info")}
            className={`py-2.5 text-xs font-semibold border-b-2 transition-colors ${
              activeTab === "info"
                ? "border-teal-700 text-teal-800"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            Lead Information
          </button>
          <button
            onClick={() => setActiveTab("timeline")}
            className={`py-2.5 text-xs font-semibold border-b-2 transition-colors ${
              activeTab === "timeline"
                ? "border-teal-700 text-teal-800"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            Activity Timeline ({activities.length})
          </button>
        </div>

        {/* Content area */}
        <div className="p-4 overflow-y-auto flex-1 space-y-4">
          {activeTab === "info" && (
            <div className="space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-3 bg-slate-50 p-3 rounded-lg border border-slate-200">
                <div>
                  <p className="text-slate-400 font-medium">Email</p>
                  <p className="text-slate-800 font-semibold mt-0.5">{lead.email || "—"}</p>
                </div>
                <div>
                  <p className="text-slate-400 font-medium">Phone</p>
                  <p className="text-slate-800 font-semibold font-mono mt-0.5">{lead.phoneNumber || "—"}</p>
                </div>
                <div>
                  <p className="text-slate-400 font-medium">Estimated Deal Value</p>
                  <p className="text-emerald-700 font-bold mt-0.5">
                    {lead.estimatedValueEtb ? `ETB ${lead.estimatedValueEtb}` : "ETB 0"}
                  </p>
                </div>
                <div>
                  <p className="text-slate-400 font-medium">Acquisition Source</p>
                  <p className="text-slate-800 font-semibold mt-0.5">{lead.source}</p>
                </div>
              </div>

              {lead.notes && (
                <div>
                  <p className="font-semibold text-slate-700 mb-1">Notes & Description</p>
                  <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 text-slate-700 whitespace-pre-wrap">
                    {lead.notes}
                  </div>
                </div>
              )}

              {mayManage && (
                <div className="pt-4 border-t border-slate-100 flex justify-end">
                  <Button variant="danger" onClick={handleArchive}>
                    Archive Lead
                  </Button>
                </div>
              )}
            </div>
          )}

          {activeTab === "timeline" && (
            <div className="space-y-4">
              {/* Add Note Form */}
              {mayManage && (
                <form onSubmit={handleAddActivity} className="space-y-2 bg-slate-50 p-3 rounded-lg border border-slate-200">
                  <div className="flex items-center gap-2">
                    <select
                      value={newActivityType}
                      onChange={(e) => setNewActivityType(e.target.value)}
                      className="text-xs bg-white border border-slate-300 rounded px-2 py-1 font-medium"
                    >
                      <option value="NOTE">Note</option>
                      <option value="CALL">Phone Call</option>
                      <option value="EMAIL">Email Sent</option>
                      <option value="MEETING">Meeting</option>
                    </select>
                    <span className="text-xs text-slate-500">Record a customer touchpoint</span>
                  </div>
                  <input
                    type="text"
                    placeholder="Enter activity summary or call notes…"
                    value={newActivityDesc}
                    onChange={(e) => setNewActivityDesc(e.target.value)}
                    className="w-full text-xs rounded border border-slate-300 p-2 focus:outline-none focus:ring-1 focus:ring-teal-600"
                  />
                  <div className="flex justify-end">
                    <Button type="submit" disabled={addActivity.isPending}>
                      Log Touchpoint
                    </Button>
                  </div>
                </form>
              )}

              {/* Activity Timeline List */}
              <div className="space-y-3">
                {activities.length === 0 ? (
                  <p className="text-xs text-slate-400 text-center py-4">
                    No activity recorded yet for this lead.
                  </p>
                ) : (
                  activities.map((act) => (
                    <div key={act.id} className="text-xs border-l-2 border-teal-600 pl-3 py-1 space-y-0.5">
                      <div className="flex items-center justify-between text-slate-500">
                        <span className="font-semibold text-slate-700">{act.activityType}</span>
                        <span>
                          {new Intl.DateTimeFormat("en-GB", {
                            dateStyle: "short",
                            timeStyle: "short",
                            timeZone: "Africa/Addis_Ababa",
                          }).format(new Date(act.createdAt))}
                        </span>
                      </div>
                      <p className="text-slate-800">{act.description}</p>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function CreateLeadModal({
  orgId,
  onClose,
}: {
  orgId: string;
  onClose: () => void;
}) {
  const create = useCreateLead(orgId);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LeadInput>({
    resolver: zodResolver(leadSchema),
    defaultValues: {
      source: "DIRECT",
      stage: "NEW",
    },
  });

  async function onSubmit(values: LeadInput) {
    setFormError(null);
    try {
      const payload: Partial<Lead> = {
        firstName: values.firstName,
        lastName: values.lastName || null,
        email: values.email || null,
        phoneNumber: values.phoneNumber || null,
        companyName: values.companyName || null,
        source: values.source,
        stage: values.stage,
        notes: values.notes || null,
        estimatedValueEtb: values.estimatedValueEtb
          ? parseFloat(values.estimatedValueEtb)
          : null,
      };
      await create.mutateAsync(payload);
      onClose();
    } catch (err) {
      setFormError(errorMessage(err));
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-lg bg-white rounded-xl shadow-2xl p-6 space-y-4">
        <div className="flex items-center justify-between border-b pb-3">
          <h2 className="text-lg font-bold text-slate-900">Add New Lead</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 font-bold">
            ✕
          </button>
        </div>

        {formError && <ErrorCallout message={formError} />}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <Field label="First name" error={errors.firstName?.message} required>
              <Input {...register("firstName")} placeholder="e.g. Abebe" />
            </Field>
            <Field label="Last name" error={errors.lastName?.message}>
              <Input {...register("lastName")} placeholder="e.g. Bikila" />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Email" error={errors.email?.message}>
              <Input {...register("email")} type="email" placeholder="client@example.et" />
            </Field>
            <Field label="Phone number" error={errors.phoneNumber?.message}>
              <Input {...register("phoneNumber")} placeholder="+251911..." />
            </Field>
          </div>

          <Field label="Company name" error={errors.companyName?.message}>
            <Input {...register("companyName")} placeholder="e.g. Great Ethiopian Run Ltd." />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Initial stage">
              <select
                {...register("stage")}
                className="w-full text-xs border rounded-lg p-2.5 bg-white border-slate-300"
              >
                {STAGES.map((s) => (
                  <option key={s.key} value={s.key}>
                    {s.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Estimated value (ETB)" error={errors.estimatedValueEtb?.message}>
              <Input
                {...register("estimatedValueEtb")}
                type="number"
                step="any"
                placeholder="e.g. 50000"
              />
            </Field>
          </div>

          <Field label="Notes / Discovery details">
            <textarea
              {...register("notes")}
              rows={3}
              placeholder="Enter context, project scope, or client requirements…"
              className="w-full text-xs rounded-lg border border-slate-300 p-2.5 focus:outline-none focus:ring-2 focus:ring-teal-600"
            />
          </Field>

          <div className="flex justify-end gap-2 pt-2 border-t">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? "Creating…" : "Save Lead"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

function EmbedFormModal({
  orgSlug,
  onClose,
}: {
  orgSlug: string;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);

  const snippet = `<!-- BizGrow Automated Lead Capture Widget -->
<form action="http://localhost:8080/api/v1/public/leads/${orgSlug}" method="POST" style="max-width: 400px; font-family: sans-serif; display: flex; flex-direction: column; gap: 10px;">
  <!-- Anti-bot honeypot field: real visitors do not see or fill this -->
  <input type="text" name="honeypot" style="display:none !important;" tabindex="-1" autocomplete="off" />
  
  <label style="font-size: 13px; font-weight: 600;">First Name *</label>
  <input type="text" name="firstName" required style="padding: 8px; border: 1px solid #ccc; border-radius: 6px;" />

  <label style="font-size: 13px; font-weight: 600;">Last Name</label>
  <input type="text" name="lastName" style="padding: 8px; border: 1px solid #ccc; border-radius: 6px;" />

  <label style="font-size: 13px; font-weight: 600;">Email</label>
  <input type="email" name="email" style="padding: 8px; border: 1px solid #ccc; border-radius: 6px;" />

  <label style="font-size: 13px; font-weight: 600;">Phone Number</label>
  <input type="tel" name="phoneNumber" placeholder="+251..." style="padding: 8px; border: 1px solid #ccc; border-radius: 6px;" />

  <label style="font-size: 13px; font-weight: 600;">Message / Project Scope</label>
  <textarea name="notes" rows="3" style="padding: 8px; border: 1px solid #ccc; border-radius: 6px;"></textarea>

  <button type="submit" style="background: #0d9488; color: white; border: none; padding: 10px; border-radius: 6px; font-weight: 600; cursor: pointer;">Send Inquiry</button>
</form>`;

  function handleCopy() {
    navigator.clipboard.writeText(snippet);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-xl bg-white rounded-xl shadow-2xl p-6 space-y-4">
        <div className="flex items-center justify-between border-b pb-3">
          <h2 className="text-lg font-bold text-slate-900">Embed Lead Capture Form</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 font-bold">
            ✕
          </button>
        </div>

        <p className="text-xs text-slate-600">
          Copy this secure HTML form code and paste it directly onto your website (WordPress, Wix, Webflow, or custom HTML). Inquiries are automatically routed into your BizGrow CRM pipeline with honeypot spam protection.
        </p>

        <div className="relative">
          <pre className="text-[11px] font-mono bg-slate-900 text-slate-100 p-3 rounded-lg overflow-x-auto max-h-60 border border-slate-700">
            {snippet}
          </pre>
        </div>

        <div className="flex items-center justify-between pt-2 border-t">
          <span className="text-xs text-emerald-700 font-medium">
            {copied ? "✓ Copied to clipboard!" : ""}
          </span>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={onClose}>
              Close
            </Button>
            <Button onClick={handleCopy}>
              {copied ? "Copied" : "Copy HTML Code"}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
