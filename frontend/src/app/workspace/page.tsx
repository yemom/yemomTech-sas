"use client";

import Link from "next/link";
import { useMemo } from "react";
import { Badge, Card, EmptyState, LinkButton, PageLoader } from "@/components/ui";
import { useDashboard } from "@/lib/api/hooks";
import { useAuth } from "@/lib/auth/auth-context";

const dateFormatter = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "Africa/Addis_Ababa",
});

export default function DashboardPage() {
  const { currentOrg, user } = useAuth();
  const dashboard = useDashboard(currentOrg?.organizationId ?? null);

  const org = dashboard.data?.organization;
  const recent = dashboard.data?.recentWebsites ?? [];
  const progress = dashboard.data?.onboarding;

  const checklist = useMemo(() => {
    if (!progress) return [];
    return [
      {
        done: true,
        label: "Workspace created",
        detail: "You have a place to organize your sites and team.",
      },
      {
        done: progress.websiteRegistered,
        label: "Register your first website",
        detail: "Track the domains your business runs on.",
        href: "/workspace/websites",
      },
      {
        done: progress.hasAdditionalMember,
        label: "Invite a teammate",
        detail: "Work is better with your team in the same workspace.",
        href: "/workspace/members",
      },
    ];
  }, [progress]);

  if (dashboard.isPending) return <PageLoader label="Loading workspace…" />;

  if (dashboard.isError) {
    return (
      <EmptyState
        title="Workspace unavailable"
        description="We could not load this workspace. It may have been archived — pick another from the switcher above."
        action={<LinkButton href="/workspace" variant="secondary">Retry</LinkButton>}
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{org?.name}</h1>
          <p className="mt-1 text-sm text-slate-500">
            Welcome back{user ? `, ${user.firstName}` : ""} · {org?.timezone} ·{" "}
            {org?.currencyCode}
          </p>
        </div>
        <div className="flex gap-2">
          <LinkButton href="/workspace/websites" variant="secondary">
            Register website
          </LinkButton>
          <LinkButton href="/workspace/members">Invite teammate</LinkButton>
        </div>
      </div>

      {/* Real counts only — no invented metrics. */}
      <div className="grid gap-4 sm:grid-cols-3">
        <Card className="p-5">
          <p className="text-sm text-slate-500">Active members</p>
          <p className="mt-1 text-3xl font-bold text-slate-900">{org?.memberCount ?? 0}</p>
        </Card>
        <Card className="p-5">
          <p className="text-sm text-slate-500">Active websites</p>
          <p className="mt-1 text-3xl font-bold text-slate-900">{org?.websiteCount ?? 0}</p>
        </Card>
        <Card className="p-5">
          <p className="text-sm text-slate-500">Your role</p>
          <p className="mt-1 text-3xl font-bold text-slate-900">
            <Badge status="ACTIVE">{currentOrg?.role.replace("BUSINESS_", "")}</Badge>
          </p>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Getting started checklist driven by real persisted data. */}
        <Card>
          <h2 className="text-base font-semibold text-slate-900">Getting started</h2>
          <ul className="mt-4 space-y-3">
            {checklist.map((item) => (
              <li key={item.label} className="flex items-start gap-3">
                <span
                  aria-hidden
                  className={`mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full text-xs ${
                    item.done ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-400"
                  }`}
                >
                  {item.done ? "✓" : "○"}
                </span>
                <span className="min-w-0">
                  <span
                    className={`block text-sm font-medium ${
                      item.done ? "text-slate-400 line-through" : "text-slate-900"
                    }`}
                  >
                    {item.label}
                  </span>
                  <span className="block text-xs text-slate-500">{item.detail}</span>
                  {!item.done && item.href && (
                    <Link
                      href={item.href}
                      className="mt-0.5 inline-block text-xs font-medium text-teal-700 hover:text-teal-800"
                    >
                      Go →
                    </Link>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-slate-900">Recent websites</h2>
            <Link href="/workspace/websites" className="text-sm font-medium text-teal-700 hover:text-teal-800">
              View all
            </Link>
          </div>
          {recent.length === 0 ? (
            <p className="mt-4 text-sm text-slate-500">
              No websites registered yet. Add the first one to get started.
            </p>
          ) : (
            <ul className="mt-4 divide-y divide-slate-100">
              {recent.map((site) => (
                <li key={site.id} className="flex items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-900">{site.name}</p>
                    <p className="truncate text-xs text-slate-500">{site.domain}</p>
                  </div>
                  <Badge status={site.status} />
                </li>
              ))}
            </ul>
          )}
          <p className="mt-4 text-xs text-slate-400">
            Created {org ? dateFormatter.format(new Date(org.createdAt)) : "—"}
          </p>
        </Card>
      </div>
    </div>
  );
}
