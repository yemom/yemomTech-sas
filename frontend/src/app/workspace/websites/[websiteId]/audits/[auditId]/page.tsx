"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  PageLoader,
} from "@/components/ui";
import { useSeoAudit, useSeoAuditIssues, useSeoAuditPages } from "@/lib/api/hooks";
import { useAuth } from "@/lib/auth/auth-context";
import type { SeoIssueSeverity, SeoIssueCategory } from "@/lib/api/types";

export default function AuditDetailPage() {
  const params = useParams<{ websiteId: string; auditId: string }>();
  const { websiteId, auditId } = params;
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? null;

  const [activeTab, setActiveTab] = useState<"issues" | "pages">("issues");
  const [severityFilter, setSeverityFilter] = useState<string>("");
  const [categoryFilter, setCategoryFilter] = useState<string>("");
  const [issuesPage, setIssuesPage] = useState(0);
  const [crawledPagesPage, setCrawledPagesPage] = useState(0);

  const auditQuery = useSeoAudit(orgId, websiteId, auditId);
  const issuesQuery = useSeoAuditIssues(
    orgId,
    websiteId,
    auditId,
    severityFilter || undefined,
    categoryFilter || undefined,
    issuesPage,
    50,
  );
  const pagesQuery = useSeoAuditPages(orgId, websiteId, auditId, crawledPagesPage, 50);

  if (!currentOrg) return <PageLoader label="Select a workspace…" />;
  if (auditQuery.isPending) return <PageLoader label="Loading audit details…" />;

  const audit = auditQuery.data;
  if (!audit) {
    return (
      <EmptyState
        title="Audit not found"
        description="The requested audit report could not be found for this website."
        action={
          <Link href={`/workspace/websites/${websiteId}/audits`}>
            <Button>Back to Audits</Button>
          </Link>
        }
      />
    );
  }

  function getScoreColor(score: number | null): string {
    if (score == null) return "text-slate-400";
    if (score >= 80) return "text-emerald-600";
    if (score >= 50) return "text-amber-500";
    return "text-rose-600";
  }

  function getSeverityBadge(severity: string) {
    switch (severity) {
      case "CRITICAL":
        return <Badge status="CRITICAL">Critical</Badge>;
      case "WARNING":
        return <Badge status="WARNING">Warning</Badge>;
      default:
        return <Badge status="INFO">Info</Badge>;
    }
  }

  const issuesData = issuesQuery.data;
  const issuesList = issuesData?.content ?? [];

  const pagesData = pagesQuery.data;
  const pagesList = pagesData?.content ?? [];

  return (
    <div className="space-y-6">
      {/* Header breadcrumb */}
      <div>
        <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
          <Link href="/workspace/websites" className="hover:underline">
            Websites
          </Link>
          <span>/</span>
          <Link href={`/workspace/websites/${websiteId}/audits`} className="hover:underline">
            SEO Audits
          </Link>
          <span>/</span>
          <span className="text-slate-900 font-medium">Report #{auditId.slice(0, 8)}</span>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h1 className="text-2xl font-bold text-slate-900">Audit Executive Summary</h1>
          <Badge status={audit.status} />
        </div>
        <p className="mt-1 text-sm text-slate-500">
          Generated on{" "}
          {new Intl.DateTimeFormat("en-GB", {
            dateStyle: "full",
            timeStyle: "short",
            timeZone: "Africa/Addis_Ababa",
          }).format(new Date(audit.createdAt))}
        </p>
      </div>

      {/* Score Hero */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
        {/* Overall Score */}
        <Card className="p-6 md:col-span-2 flex flex-col justify-between bg-gradient-to-br from-slate-900 to-slate-800 text-white shadow-md">
          <div>
            <span className="text-xs font-semibold tracking-wider uppercase text-slate-400">
              Overall Health Score
            </span>
            <div className="mt-3 flex items-baseline gap-2">
              <span className={`text-5xl font-black ${getScoreColor(audit.score)}`}>
                {audit.score != null ? audit.score : "—"}
              </span>
              <span className="text-xl text-slate-400">/ 100</span>
            </div>
            <p className="mt-2 text-xs text-slate-300">
              {audit.score != null && audit.score >= 80
                ? "Excellent technical and on-page optimization. Your website adheres to top SEO standards."
                : audit.score != null && audit.score >= 50
                  ? "Moderate performance with actionable opportunities to improve search indexing and traffic."
                  : "Critical SEO barriers detected that impede search crawler indexing and customer experience."}
            </p>
          </div>

          <div className="mt-6 pt-4 border-t border-slate-700 grid grid-cols-3 gap-2 text-center text-xs">
            <div>
              <p className="text-slate-400">Pages Analyzed</p>
              <p className="text-base font-bold text-white mt-0.5">{audit.pagesCrawled}</p>
            </div>
            <div>
              <p className="text-slate-400">Critical Issues</p>
              <p className="text-base font-bold text-rose-400 mt-0.5">{audit.criticalIssuesCount}</p>
            </div>
            <div>
              <p className="text-slate-400">Warnings</p>
              <p className="text-base font-bold text-amber-400 mt-0.5">{audit.warningIssuesCount}</p>
            </div>
          </div>
        </Card>

        {/* Category Breakdown */}
        <div className="md:col-span-3 grid grid-cols-2 gap-3">
          <Card className="p-4 flex flex-col justify-between">
            <div>
              <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Technical SEO
              </span>
              <div className="mt-2 flex items-baseline justify-between">
                <span className={`text-2xl font-bold ${getScoreColor(audit.technicalScore)}`}>
                  {audit.technicalScore ?? "—"}%
                </span>
                <span className="text-xs text-slate-400">Weight 30%</span>
              </div>
            </div>
            <div className="mt-3 w-full bg-slate-100 rounded-full h-2">
              <div
                className="bg-indigo-600 h-2 rounded-full transition-all"
                style={{ width: `${audit.technicalScore ?? 0}%` }}
              />
            </div>
          </Card>

          <Card className="p-4 flex flex-col justify-between">
            <div>
              <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                On-Page SEO
              </span>
              <div className="mt-2 flex items-baseline justify-between">
                <span className={`text-2xl font-bold ${getScoreColor(audit.onPageScore)}`}>
                  {audit.onPageScore ?? "—"}%
                </span>
                <span className="text-xs text-slate-400">Weight 30%</span>
              </div>
            </div>
            <div className="mt-3 w-full bg-slate-100 rounded-full h-2">
              <div
                className="bg-emerald-600 h-2 rounded-full transition-all"
                style={{ width: `${audit.onPageScore ?? 0}%` }}
              />
            </div>
          </Card>

          <Card className="p-4 flex flex-col justify-between">
            <div>
              <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Content Quality
              </span>
              <div className="mt-2 flex items-baseline justify-between">
                <span className={`text-2xl font-bold ${getScoreColor(audit.contentScore)}`}>
                  {audit.contentScore ?? "—"}%
                </span>
                <span className="text-xs text-slate-400">Weight 20%</span>
              </div>
            </div>
            <div className="mt-3 w-full bg-slate-100 rounded-full h-2">
              <div
                className="bg-sky-600 h-2 rounded-full transition-all"
                style={{ width: `${audit.contentScore ?? 0}%` }}
              />
            </div>
          </Card>

          <Card className="p-4 flex flex-col justify-between">
            <div>
              <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Accessibility
              </span>
              <div className="mt-2 flex items-baseline justify-between">
                <span className={`text-2xl font-bold ${getScoreColor(audit.accessibilityScore)}`}>
                  {audit.accessibilityScore ?? "—"}%
                </span>
                <span className="text-xs text-slate-400">Weight 20%</span>
              </div>
            </div>
            <div className="mt-3 w-full bg-slate-100 rounded-full h-2">
              <div
                className="bg-purple-600 h-2 rounded-full transition-all"
                style={{ width: `${audit.accessibilityScore ?? 0}%` }}
              />
            </div>
          </Card>
        </div>
      </div>

      {/* Tabs navigation */}
      <div className="flex border-b border-slate-200 gap-6">
        <button
          onClick={() => setActiveTab("issues")}
          className={`pb-3 text-sm font-semibold transition-colors border-b-2 ${
            activeTab === "issues"
              ? "border-slate-900 text-slate-900"
              : "border-transparent text-slate-500 hover:text-slate-800"
          }`}
        >
          Detected Issues ({audit.criticalIssuesCount + audit.warningIssuesCount + audit.infoIssuesCount})
        </button>
        <button
          onClick={() => setActiveTab("pages")}
          className={`pb-3 text-sm font-semibold transition-colors border-b-2 ${
            activeTab === "pages"
              ? "border-slate-900 text-slate-900"
              : "border-transparent text-slate-500 hover:text-slate-800"
          }`}
        >
          Crawled Pages ({audit.pagesCrawled})
        </button>
      </div>

      {/* Issues View */}
      {activeTab === "issues" && (
        <div className="space-y-4">
          {/* Filters */}
          <div className="flex flex-wrap items-center gap-3">
            <select
              value={severityFilter}
              onChange={(e) => {
                setSeverityFilter(e.target.value);
                setIssuesPage(0);
              }}
              className="text-xs font-medium bg-white border border-slate-300 rounded-md px-3 py-1.5 text-slate-700 shadow-sm"
            >
              <option value="">All Severities</option>
              <option value="CRITICAL">Critical Only</option>
              <option value="WARNING">Warnings Only</option>
              <option value="INFO">Info Only</option>
            </select>

            <select
              value={categoryFilter}
              onChange={(e) => {
                setCategoryFilter(e.target.value);
                setIssuesPage(0);
              }}
              className="text-xs font-medium bg-white border border-slate-300 rounded-md px-3 py-1.5 text-slate-700 shadow-sm"
            >
              <option value="">All Categories</option>
              <option value="TECHNICAL">Technical SEO</option>
              <option value="ON_PAGE">On-Page SEO</option>
              <option value="CONTENT">Content Quality</option>
              <option value="ACCESSIBILITY">Accessibility</option>
              <option value="PERFORMANCE">Performance</option>
            </select>
          </div>

          {issuesList.length === 0 ? (
            <EmptyState
              title="No issues found"
              description="No SEO issues match the currently selected criteria."
            />
          ) : (
            <div className="space-y-3">
              {issuesList.map((issue) => (
                <Card key={issue.id} className="p-4 space-y-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        {getSeverityBadge(issue.severity)}
                        <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                          {issue.category}
                        </span>
                      </div>
                      <h3 className="text-base font-semibold text-slate-900">{issue.title}</h3>
                    </div>
                  </div>

                  <p className="text-sm text-slate-600">{issue.description}</p>

                  {issue.affectedUrl && (
                    <div className="text-xs font-mono bg-slate-50 border border-slate-200 rounded px-2.5 py-1 text-slate-700 truncate">
                      Affected: {issue.affectedUrl}
                    </div>
                  )}

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs bg-slate-50 p-3 rounded-md border border-slate-100">
                    <div>
                      <p className="font-semibold text-slate-700 mb-0.5">Why this matters:</p>
                      <p className="text-slate-600">{issue.explanation || "Impairs crawlability or user experience."}</p>
                    </div>
                    <div>
                      <p className="font-semibold text-slate-700 mb-0.5">How to resolve:</p>
                      <p className="text-emerald-700 font-medium">
                        {issue.recommendation || "Follow SEO best practice guidelines."}
                      </p>
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Pages View */}
      {activeTab === "pages" && (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">URL</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">H1 Tags</th>
                <th className="px-4 py-3">Words</th>
                <th className="px-4 py-3">Images (Missing Alt)</th>
                <th className="px-4 py-3">Response Time</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {pagesList.map((page) => (
                <tr key={page.id} className="hover:bg-slate-50/80">
                  <td className="px-4 py-3 max-w-xs truncate">
                    <p className="font-medium text-slate-900 truncate" title={page.title ?? ""}>
                      {page.title || "Untitled"}
                    </p>
                    <p className="text-xs font-mono text-slate-500 truncate" title={page.url}>
                      {page.url}
                    </p>
                  </td>
                  <td className="px-4 py-3">
                    <Badge status={page.statusCode === 200 ? "SUCCESS" : "FAILED"}>
                      HTTP {page.statusCode ?? 0}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-slate-700">{page.h1Count}</td>
                  <td className="px-4 py-3 text-slate-700">{page.wordCount}</td>
                  <td className="px-4 py-3 text-slate-700">
                    {page.imagesCount} ({page.missingAltImagesCount} missing)
                  </td>
                  <td className="px-4 py-3 text-slate-700">
                    {page.loadTimeMs != null ? `${page.loadTimeMs}ms` : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}
