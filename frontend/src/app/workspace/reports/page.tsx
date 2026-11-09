"use client";

import { useAuth } from "@/lib/auth/auth-context";
import { useComprehensiveReport } from "@/lib/api/hooks";
import { Button, EmptyState, ErrorCallout } from "@/components/ui";

function formatEtb(value?: number): string {
  if (value === undefined || value === null) return "ETB 0.00";
  return `ETB ${Number(value).toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export default function ReportsPage() {
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? "";

  const { data: report, isLoading, error, refetch } = useComprehensiveReport(orgId);

  const handleDownloadCsv = () => {
    const apiBase =
      process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
    window.open(`${apiBase}/organizations/${orgId}/reports/export/csv`, "_blank");
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Cross-Module Performance Reports
          </h1>
          <p className="text-sm text-slate-500">
            Exportable growth metrics combining SEO audits, website traffic, CRM pipelines, and campaigns.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => void refetch()}>
            ↻ Refresh
          </Button>
          <Button onClick={handleDownloadCsv}>↓ Download CSV Report</Button>
        </div>
      </div>

      {error && <ErrorCallout message={(error as Error).message} />}

      {isLoading ? (
        <div className="py-16 text-center text-sm text-slate-500">
          Compiling business performance data...
        </div>
      ) : !report ? (
        <EmptyState
          title="No report data available"
          description="Reports require at least one verified website, audit snapshot, or CRM lead."
        />
      ) : (
        <div className="space-y-6">
          {/* Organization & Generation Meta */}
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4 text-xs text-slate-500 shadow-sm">
            <div>
              <span className="font-semibold text-slate-800">Report Entity: </span>
              {report.organizationName}
            </div>
            <div>
              <span className="font-semibold text-slate-800">Generated: </span>
              {new Date(report.generatedAt).toLocaleString()}
            </div>
          </div>

          {/* Module 1: SEO Performance */}
          <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">1. Search Engine Optimization (SEO)</h2>
            <p className="text-xs text-slate-500">Technical health and crawling audit indicators</p>

            <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div className="rounded-lg bg-slate-50 p-4 text-center">
                <p className="text-xs text-slate-500">Health Score</p>
                <p className="mt-1 text-2xl font-black text-indigo-600">
                  {report.seo.latestScore !== null && report.seo.latestScore !== undefined
                    ? `${report.seo.latestScore}/100`
                    : "N/A"}
                </p>
              </div>
              <div className="rounded-lg bg-slate-50 p-4 text-center">
                <p className="text-xs text-slate-500">Pages Crawled</p>
                <p className="mt-1 text-2xl font-bold text-slate-900">
                  {report.seo.crawledPagesCount}
                </p>
              </div>
              <div className="rounded-lg bg-rose-50 p-4 text-center">
                <p className="text-xs text-rose-600">Critical Issues</p>
                <p className="mt-1 text-2xl font-bold text-rose-700">
                  {report.seo.criticalIssuesCount}
                </p>
              </div>
              <div className="rounded-lg bg-amber-50 p-4 text-center">
                <p className="text-xs text-amber-600">Warning Issues</p>
                <p className="mt-1 text-2xl font-bold text-amber-700">
                  {report.seo.warningIssuesCount}
                </p>
              </div>
            </div>
          </div>

          {/* Module 2: Traffic & Search Console */}
          <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">2. Website Traffic & Search Engine Visibility</h2>
            <p className="text-xs text-slate-500">30-day verified Google Analytics & Search Console metrics</p>

            <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
              <div className="rounded-lg bg-slate-50 p-3 text-center">
                <p className="text-[11px] text-slate-500">Sessions</p>
                <p className="mt-1 text-lg font-bold text-slate-900">{report.traffic.totalSessions}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3 text-center">
                <p className="text-[11px] text-slate-500">Pageviews</p>
                <p className="mt-1 text-lg font-bold text-slate-900">{report.traffic.totalPageviews}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3 text-center">
                <p className="text-[11px] text-slate-500">Bounce Rate</p>
                <p className="mt-1 text-lg font-bold text-slate-900">{report.traffic.bounceRatePct}%</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3 text-center">
                <p className="text-[11px] text-slate-500">Search Impressions</p>
                <p className="mt-1 text-lg font-bold text-indigo-600">
                  {report.traffic.totalSearchImpressions}
                </p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3 text-center">
                <p className="text-[11px] text-slate-500">Organic Clicks</p>
                <p className="mt-1 text-lg font-bold text-emerald-600">
                  {report.traffic.totalSearchClicks}
                </p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3 text-center">
                <p className="text-[11px] text-slate-500">Search CTR</p>
                <p className="mt-1 text-lg font-bold text-slate-900">{report.traffic.averageCtrPct}%</p>
              </div>
            </div>
          </div>

          {/* Module 3: CRM Pipeline & Revenue */}
          <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">3. CRM & Sales Conversion Pipeline</h2>
            <p className="text-xs text-slate-500">Lead generation velocity and revenue in Ethiopian Birr</p>

            <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div className="rounded-lg bg-slate-50 p-4">
                <p className="text-xs text-slate-500">Total Leads</p>
                <p className="mt-1 text-2xl font-bold text-slate-900">{report.crm.totalLeads}</p>
                <p className="mt-1 text-xs text-slate-400">{report.crm.activeLeads} active pipeline</p>
              </div>
              <div className="rounded-lg bg-emerald-50 p-4">
                <p className="text-xs text-emerald-700">Won Deals</p>
                <p className="mt-1 text-2xl font-bold text-emerald-800">{report.crm.wonLeads}</p>
                <p className="mt-1 text-xs text-emerald-600">
                  {report.crm.conversionRatePct}% win rate
                </p>
              </div>
              <div className="rounded-lg bg-slate-50 p-4">
                <p className="text-xs text-slate-500">Total Pipeline Value</p>
                <p className="mt-1 text-xl font-bold text-slate-900">
                  {formatEtb(report.crm.totalPipelineValueEtb)}
                </p>
              </div>
              <div className="rounded-lg bg-emerald-50 p-4">
                <p className="text-xs text-emerald-700">Won Revenue</p>
                <p className="mt-1 text-xl font-bold text-emerald-800">
                  {formatEtb(report.crm.wonRevenueEtb)}
                </p>
              </div>
            </div>
          </div>

          {/* Module 4: Marketing Campaigns & ROAS */}
          <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">4. Marketing Performance & ROAS</h2>
            <p className="text-xs text-slate-500">Campaign spend attribution and return on investment</p>

            <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div className="rounded-lg bg-slate-50 p-4">
                <p className="text-xs text-slate-500">Active Campaigns</p>
                <p className="mt-1 text-2xl font-bold text-slate-900">
                  {report.marketing.activeCampaigns}
                </p>
                <p className="mt-1 text-xs text-slate-400">{report.marketing.totalCampaigns} total</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-4">
                <p className="text-xs text-slate-500">Total Spend</p>
                <p className="mt-1 text-xl font-bold text-slate-900">
                  {formatEtb(report.marketing.totalSpentEtb)}
                </p>
                <p className="mt-1 text-xs text-slate-400">
                  Budget: {formatEtb(report.marketing.totalBudgetEtb)}
                </p>
              </div>
              <div className="rounded-lg bg-slate-50 p-4">
                <p className="text-xs text-slate-500">Attributed Leads</p>
                <p className="mt-1 text-2xl font-bold text-emerald-600">
                  {report.marketing.attributedLeadsCount}
                </p>
              </div>
              <div className="rounded-lg bg-indigo-50 p-4">
                <p className="text-xs text-indigo-700">Overall ROAS</p>
                <p className="mt-1 text-2xl font-bold text-indigo-800">
                  {Number(report.marketing.returnOnAdSpend).toFixed(2)}x
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
