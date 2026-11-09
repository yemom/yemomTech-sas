"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorCallout,
  PageLoader,
} from "@/components/ui";
import { errorMessage } from "@/lib/api/client";
import { useSeoAudits, useTriggerSeoAudit } from "@/lib/api/hooks";
import { can } from "@/lib/api/permissions";
import { useAuth } from "@/lib/auth/auth-context";

export default function WebsiteAuditsPage() {
  const params = useParams<{ websiteId: string }>();
  const websiteId = params.websiteId;
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? null;
  const [page, setPage] = useState(0);

  const audits = useSeoAudits(orgId, websiteId, page);
  const trigger = useTriggerSeoAudit(orgId ?? "", websiteId);
  const [actionError, setActionError] = useState<string | null>(null);

  const mayManage = currentOrg ? can(currentOrg.role, "SEO_MANAGE") : false;

  if (!currentOrg) return <PageLoader label="Select a workspace…" />;
  if (audits.isPending) return <PageLoader label="Loading SEO audits…" />;

  const data = audits.data;
  const content = data?.content ?? [];
  const hasActiveAudit = content.some(
    (a) => a.status === "PENDING" || a.status === "RUNNING",
  );

  async function handleStartAudit() {
    setActionError(null);
    try {
      await trigger.mutateAsync();
    } catch (err) {
      setActionError(errorMessage(err));
    }
  }

  function getScoreBadgeStatus(score: number | null): string {
    if (score == null) return "INACTIVE";
    if (score >= 80) return "SUCCESS";
    if (score >= 50) return "WARNING";
    return "FAILED";
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
            <Link href="/workspace/websites" className="hover:underline">
              Websites
            </Link>
            <span>/</span>
            <span className="text-slate-900 font-medium">SEO Audits</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900">SEO Audit History</h1>
          <p className="mt-1 text-sm text-slate-500">
            Automated crawler analyzing technical SEO, on-page tags, content quality, and performance signals.
          </p>
        </div>
        {mayManage && (
          <Button
            onClick={handleStartAudit}
            disabled={trigger.isPending || hasActiveAudit}
          >
            {trigger.isPending
              ? "Starting Audit…"
              : hasActiveAudit
                ? "Audit In Progress…"
                : "Run New Audit"}
          </Button>
        )}
      </div>

      {actionError && (
        <ErrorCallout message={actionError} />
      )}

      {hasActiveAudit && (
        <Card className="p-4 bg-amber-50 border-amber-200">
          <div className="flex items-center gap-3">
            <span className="animate-spin text-amber-600 text-lg">⏳</span>
            <div>
              <p className="text-sm font-semibold text-amber-900">
                SEO Audit in progress
              </p>
              <p className="text-xs text-amber-700">
                The crawler is currently fetching pages, analyzing structure, and generating issues. This page will update automatically.
              </p>
            </div>
          </div>
        </Card>
      )}

      {content.length === 0 ? (
        <EmptyState
          title="No audits conducted yet"
          description={
            mayManage
              ? "Run your first website audit to uncover broken links, missing meta descriptions, heading gaps, and performance bottlenecks."
              : "No audits have been executed for this website."
          }
          action={
            mayManage ? (
              <Button onClick={handleStartAudit} disabled={trigger.isPending}>
                Run Initial Audit
              </Button>
            ) : undefined
          }
        />
      ) : (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Audit Date</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Overall Score</th>
                <th className="px-4 py-3">Pages</th>
                <th className="px-4 py-3">Issues</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {content.map((audit) => (
                <tr key={audit.id} className="hover:bg-slate-50/80">
                  <td className="px-4 py-3 font-medium text-slate-900">
                    {new Intl.DateTimeFormat("en-GB", {
                      dateStyle: "medium",
                      timeStyle: "short",
                      timeZone: "Africa/Addis_Ababa",
                    }).format(new Date(audit.createdAt))}
                  </td>
                  <td className="px-4 py-3">
                    <Badge status={audit.status} />
                  </td>
                  <td className="px-4 py-3">
                    {audit.score != null ? (
                      <div className="flex items-center gap-2">
                        <Badge status={getScoreBadgeStatus(audit.score)}>
                          {audit.score} / 100
                        </Badge>
                      </div>
                    ) : (
                      <span className="text-slate-400">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {audit.pagesCrawled} {audit.pagesCrawled === 1 ? "page" : "pages"}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1.5 text-xs">
                      {audit.criticalIssuesCount > 0 && (
                        <span className="inline-flex items-center px-1.5 py-0.5 rounded bg-red-100 text-red-800 font-medium">
                          {audit.criticalIssuesCount} crit
                        </span>
                      )}
                      {audit.warningIssuesCount > 0 && (
                        <span className="inline-flex items-center px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 font-medium">
                          {audit.warningIssuesCount} warn
                        </span>
                      )}
                      {audit.infoIssuesCount > 0 && (
                        <span className="inline-flex items-center px-1.5 py-0.5 rounded bg-blue-100 text-blue-800 font-medium">
                          {audit.infoIssuesCount} info
                        </span>
                      )}
                      {audit.criticalIssuesCount === 0 &&
                        audit.warningIssuesCount === 0 &&
                        audit.infoIssuesCount === 0 && (
                          <span className="text-slate-400">None</span>
                        )}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Link
                      href={`/workspace/websites/${websiteId}/audits/${audit.id}`}
                    >
                      <Button variant="ghost">
                        View Report
                      </Button>
                    </Link>
                  </td>
                </tr>
              ))}
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
