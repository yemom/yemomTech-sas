"use client";

import { useState } from "react";
import { Button, Card, EmptyState, PageLoader } from "@/components/ui";
import { useAuditLogs } from "@/lib/api/hooks";
import { useAuth } from "@/lib/auth/auth-context";

export default function AuditLogsPage() {
  const { currentOrg } = useAuth();
  const [page, setPage] = useState(0);
  const orgId = currentOrg?.organizationId ?? null;

  const { data, isLoading, error } = useAuditLogs(orgId, page);

  const formatAction = (action: string) => {
    return action.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Activity History</h1>
          <p className="mt-1 text-sm text-slate-500">
            Immutable audit record of security and administrative operations in this organization.
          </p>
        </div>
      </div>

      {isLoading ? (
        <PageLoader label="Loading activity logs…" />
      ) : error ? (
        <div className="rounded-lg bg-rose-50 p-4 text-sm text-rose-800">
          Failed to load audit history.
        </div>
      ) : !data || data.content.length === 0 ? (
        <EmptyState
          title="No activity recorded yet"
          description="Actions like registering websites, inviting team members, and modifying settings will appear here."
        />
      ) : (
        <Card className="p-0 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-600">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                <tr>
                  <th scope="col" className="px-6 py-3">Action</th>
                  <th scope="col" className="px-6 py-3">Target</th>
                  <th scope="col" className="px-6 py-3">Details</th>
                  <th scope="col" className="px-6 py-3">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {data.content.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50/50">
                    <td className="whitespace-nowrap px-6 py-4 font-medium text-slate-900">
                      <div className="flex items-center gap-2">
                        <span className="inline-block size-2 rounded-full bg-teal-600" />
                        {formatAction(log.action)}
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-slate-600">
                      {log.targetType ? (
                        <span className="font-mono text-xs bg-slate-100 text-slate-700 px-2 py-0.5 rounded">
                          {log.targetType}
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="px-6 py-4 text-xs font-mono text-slate-500 max-w-xs truncate">
                      {log.metadata ? JSON.stringify(log.metadata) : "—"}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-slate-500 text-xs">
                      {new Date(log.createdAt).toLocaleString(undefined, {
                        dateStyle: "medium",
                        timeStyle: "short",
                      })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between border-t border-slate-200 px-6 py-3">
            <div className="text-xs text-slate-500">
              Page {data.page + 1} of {Math.max(1, data.totalPages)} ({data.totalElements} total entries)
            </div>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                disabled={data.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="py-1 px-3 text-xs"
              >
                Previous
              </Button>
              <Button
                variant="secondary"
                disabled={data.last}
                onClick={() => setPage((p) => p + 1)}
                className="py-1 px-3 text-xs"
              >
                Next
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
