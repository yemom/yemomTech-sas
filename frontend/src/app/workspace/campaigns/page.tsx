"use client";

import { useState } from "react";
import { useAuth } from "@/lib/auth/auth-context";
import {
  useCampaigns,
  useCreateCampaign,
  useUpdateCampaign,
  useDeleteCampaign,
  useCampaignPerformance,
} from "@/lib/api/hooks";
import type {
  Campaign,
  CampaignChannel,
  CampaignObjective,
  CampaignStatus,
} from "@/lib/api/types";
import { Badge, Button, EmptyState, ErrorCallout, Input } from "@/components/ui";

function formatEtb(value?: number): string {
  if (value === undefined || value === null) return "ETB 0.00";
  return `ETB ${Number(value).toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

const CHANNELS: CampaignChannel[] = [
  "TELEGRAM",
  "META_ADS",
  "GOOGLE_ADS",
  "TIKTOK",
  "EMAIL",
  "SMS",
  "DIRECT",
];

const OBJECTIVES: CampaignObjective[] = [
  "LEAD_GENERATION",
  "SALES",
  "WEBSITE_TRAFFIC",
  "BRAND_AWARENESS",
  "EVENT_PROMOTION",
];

export default function CampaignsPage() {
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? "";

  const [statusFilter, setStatusFilter] = useState<string>("");
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedPerformanceId, setSelectedPerformanceId] = useState<string | null>(null);

  const { data, isLoading, error } = useCampaigns(orgId, statusFilter || undefined);
  const createMutation = useCreateCampaign(orgId);
  const updateMutation = useUpdateCampaign(orgId);
  const deleteMutation = useDeleteCampaign(orgId);

  // Form state
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [objective, setObjective] = useState<CampaignObjective>("LEAD_GENERATION");
  const [channel, setChannel] = useState<CampaignChannel>("TELEGRAM");
  const [budgetEtb, setBudgetEtb] = useState("");
  const [spentEtb, setSpentEtb] = useState("");
  const [utmSource, setUtmSource] = useState("");
  const [utmMedium, setUtmMedium] = useState("");
  const [utmCampaign, setUtmCampaign] = useState("");
  const [formError, setFormError] = useState("");

  const campaigns = data?.content ?? [];

  const totalBudget = campaigns.reduce((acc, c) => acc + (Number(c.budgetEtb) || 0), 0);
  const totalSpent = campaigns.reduce((acc, c) => acc + (Number(c.spentEtb) || 0), 0);
  const totalAttributed = campaigns.reduce((acc, c) => acc + (Number(c.attributedLeadsCount) || 0), 0);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError("");
    if (!name.trim()) {
      setFormError("Campaign name is required");
      return;
    }

    try {
      await createMutation.mutateAsync({
        name: name.trim(),
        description: description.trim() || undefined,
        objective,
        status: "ACTIVE",
        channel,
        budgetEtb: budgetEtb ? parseFloat(budgetEtb) : 0,
        spentEtb: spentEtb ? parseFloat(spentEtb) : 0,
        utmSource: utmSource.trim() || undefined,
        utmMedium: utmMedium.trim() || undefined,
        utmCampaign: utmCampaign.trim() || undefined,
      });
      setShowCreateModal(false);
      setName("");
      setDescription("");
      setBudgetEtb("");
      setSpentEtb("");
      setUtmCampaign("");
    } catch (err: unknown) {
      setFormError((err as Error).message ?? "Failed to create campaign");
    }
  };

  const handleToggleStatus = async (campaign: Campaign) => {
    const nextStatus: CampaignStatus =
      campaign.status === "ACTIVE" ? "PAUSED" : "ACTIVE";
    await updateMutation.mutateAsync({
      campaignId: campaign.id,
      body: { name: campaign.name, status: nextStatus },
    });
  };

  const handleDelete = async (campaignId: string) => {
    if (confirm("Are you sure you want to delete this marketing campaign?")) {
      await deleteMutation.mutateAsync(campaignId);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Marketing Campaigns
          </h1>
          <p className="text-sm text-slate-500">
            Track multi-channel digital campaigns, UTM attribution, and ROI in Ethiopian Birr.
          </p>
        </div>
        <Button onClick={() => setShowCreateModal(true)}>+ New Campaign</Button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Total Campaigns
          </p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{campaigns.length}</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Total Budget
          </p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{formatEtb(totalBudget)}</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Total Spend
          </p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{formatEtb(totalSpent)}</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Attributed Leads
          </p>
          <p className="mt-2 text-2xl font-semibold text-emerald-600">{totalAttributed}</p>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2 border-b border-slate-200 pb-2">
        {["", "ACTIVE", "PAUSED", "COMPLETED", "DRAFT"].map((st) => (
          <button
            key={st}
            onClick={() => setStatusFilter(st)}
            className={`rounded-lg px-3 py-1.5 text-xs font-medium transition ${
              statusFilter === st
                ? "bg-slate-900 text-white"
                : "text-slate-600 hover:bg-slate-100"
            }`}
          >
            {st ? st : "ALL CAMPAIGNS"}
          </button>
        ))}
      </div>

      {/* Error Callout */}
      {error && <ErrorCallout message={(error as Error).message} />}

      {/* Campaign List */}
      {isLoading ? (
        <div className="py-12 text-center text-sm text-slate-500">
          Loading campaigns...
        </div>
      ) : campaigns.length === 0 ? (
        <EmptyState
          title="No marketing campaigns yet"
          description="Create your first campaign to track Telegram, Google Ads, or Meta marketing channels with UTM lead attribution."
          action={<Button onClick={() => setShowCreateModal(true)}>Create Campaign</Button>}
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {campaigns.map((camp) => (
            <div
              key={camp.id}
              className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-5 shadow-sm hover:border-slate-300 transition"
            >
              <div>
                <div className="flex items-start justify-between gap-2">
                  <h3 className="font-semibold text-slate-900">{camp.name}</h3>
                  <Badge status={camp.status} />
                </div>
                {camp.description && (
                  <p className="mt-1 line-clamp-2 text-xs text-slate-500">
                    {camp.description}
                  </p>
                )}

                <div className="mt-3 flex flex-wrap gap-1.5">
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-700">
                    {camp.channel}
                  </span>
                  <span className="rounded bg-indigo-50 px-2 py-0.5 text-[10px] font-medium text-indigo-700">
                    {camp.objective}
                  </span>
                  {camp.utmCampaign && (
                    <span className="rounded bg-amber-50 px-2 py-0.5 text-[10px] font-mono text-amber-800">
                      utm: {camp.utmCampaign}
                    </span>
                  )}
                </div>

                <div className="mt-4 grid grid-cols-2 gap-2 border-t border-slate-100 pt-3 text-xs">
                  <div>
                    <span className="text-slate-400">Budget:</span>
                    <p className="font-medium text-slate-800">{formatEtb(camp.budgetEtb)}</p>
                  </div>
                  <div>
                    <span className="text-slate-400">Spent:</span>
                    <p className="font-medium text-slate-800">{formatEtb(camp.spentEtb)}</p>
                  </div>
                  <div>
                    <span className="text-slate-400">Attributed Leads:</span>
                    <p className="font-semibold text-emerald-600">
                      {camp.attributedLeadsCount}
                    </p>
                  </div>
                  <div>
                    <span className="text-slate-400">Pipeline Value:</span>
                    <p className="font-medium text-slate-800">
                      {formatEtb(camp.pipelineValueEtb)}
                    </p>
                  </div>
                </div>
              </div>

              <div className="mt-5 flex items-center justify-between border-t border-slate-100 pt-3">
                <button
                  onClick={() => setSelectedPerformanceId(camp.id)}
                  className="text-xs font-semibold text-indigo-600 hover:text-indigo-800"
                >
                  View Performance →
                </button>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleToggleStatus(camp)}
                    className="text-xs text-slate-600 hover:text-slate-900"
                  >
                    {camp.status === "ACTIVE" ? "Pause" : "Activate"}
                  </button>
                  <button
                    onClick={() => handleDelete(camp.id)}
                    className="text-xs text-rose-500 hover:text-rose-700"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Campaign Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl">
            <h2 className="text-lg font-bold text-slate-900">Create Marketing Campaign</h2>
            <p className="mt-1 text-xs text-slate-500">
              Set campaign channel, budget, and UTM tags to track lead conversions.
            </p>

            {formError && <div className="mt-3"><ErrorCallout message={formError} /></div>}

            <form onSubmit={handleCreate} className="mt-4 space-y-3">
              <div>
                <label className="block text-xs font-medium text-slate-700">Campaign Name *</label>
                <Input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Addis Tech Summit Promotion"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-700">Channel</label>
                  <select
                    value={channel}
                    onChange={(e) => setChannel(e.target.value as CampaignChannel)}
                    className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
                  >
                    {CHANNELS.map((ch) => (
                      <option key={ch} value={ch}>
                        {ch}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700">Objective</label>
                  <select
                    value={objective}
                    onChange={(e) => setObjective(e.target.value as CampaignObjective)}
                    className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
                  >
                    {OBJECTIVES.map((ob) => (
                      <option key={ob} value={ob}>
                        {ob}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-700">Budget (ETB)</label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    value={budgetEtb}
                    onChange={(e) => setBudgetEtb(e.target.value)}
                    placeholder="25000.00"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700">Spent (ETB)</label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    value={spentEtb}
                    onChange={(e) => setSpentEtb(e.target.value)}
                    placeholder="0.00"
                  />
                </div>
              </div>

              <div className="border-t border-slate-100 pt-3">
                <p className="text-xs font-semibold text-slate-700">UTM Tracking Tags (Optional)</p>
                <div className="mt-2 grid grid-cols-3 gap-2">
                  <Input
                    placeholder="utm_source (e.g. telegram)"
                    value={utmSource}
                    onChange={(e) => setUtmSource(e.target.value)}
                  />
                  <Input
                    placeholder="utm_medium (e.g. cpc)"
                    value={utmMedium}
                    onChange={(e) => setUtmMedium(e.target.value)}
                  />
                  <Input
                    placeholder="utm_campaign"
                    value={utmCampaign}
                    onChange={(e) => setUtmCampaign(e.target.value)}
                  />
                </div>
              </div>

              <div className="mt-5 flex justify-end gap-2 pt-2">
                <Button
                  variant="secondary"
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={createMutation.isPending}>
                  {createMutation.isPending ? "Creating..." : "Save Campaign"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Performance Modal */}
      {selectedPerformanceId && (
        <PerformanceModal
          orgId={orgId}
          campaignId={selectedPerformanceId}
          onClose={() => setSelectedPerformanceId(null)}
        />
      )}
    </div>
  );
}

function PerformanceModal({
  orgId,
  campaignId,
  onClose,
}: {
  orgId: string;
  campaignId: string;
  onClose: () => void;
}) {
  const { data: perf, isLoading } = useCampaignPerformance(orgId, campaignId);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
        <h2 className="text-lg font-bold text-slate-900">Campaign Performance</h2>
        {isLoading ? (
          <p className="mt-4 text-sm text-slate-500">Calculating metrics...</p>
        ) : perf ? (
          <div className="mt-4 space-y-4">
            <p className="text-sm font-semibold text-slate-800">{perf.campaignName}</p>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-xs text-slate-500">Attributed Leads</p>
                <p className="mt-1 text-xl font-bold text-slate-900">{perf.totalAttributedLeads}</p>
              </div>
              <div className="rounded-lg bg-emerald-50 p-3">
                <p className="text-xs text-emerald-700">Won Leads</p>
                <p className="mt-1 text-xl font-bold text-emerald-800">{perf.wonLeads}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-xs text-slate-500">Cost Per Lead (CPL)</p>
                <p className="mt-1 text-xl font-bold text-slate-900">{formatEtb(perf.costPerLeadEtb)}</p>
              </div>
              <div className="rounded-lg bg-indigo-50 p-3">
                <p className="text-xs text-indigo-700">ROAS (Return on Ad Spend)</p>
                <p className="mt-1 text-xl font-bold text-indigo-800">
                  {Number(perf.returnOnAdSpendRatio).toFixed(2)}x
                </p>
              </div>
            </div>
            <div className="border-t border-slate-100 pt-3 text-xs text-slate-500">
              <p>Won Revenue: {formatEtb(perf.wonPipelineValueEtb)}</p>
              <p>Total Pipeline: {formatEtb(perf.totalPipelineValueEtb)}</p>
            </div>
          </div>
        ) : null}

        <div className="mt-6 flex justify-end">
          <Button onClick={onClose}>Close</Button>
        </div>
      </div>
    </div>
  );
}
