"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Badge, Button } from "@/components/ui";
import { useResendVerification } from "@/lib/api/hooks";
import { ROLE_LABELS } from "@/lib/api/permissions";
import { useAuth } from "@/lib/auth/auth-context";

const NAV_ITEMS = [
  { href: "/workspace", label: "Dashboard" },
  { href: "/workspace/websites", label: "Websites" },
  { href: "/workspace/analytics", label: "Analytics" },
  { href: "/workspace/leads", label: "Leads & CRM" },
  { href: "/workspace/ai", label: "AI Insights & Bot" },
  { href: "/workspace/members", label: "Members" },
  { href: "/workspace/invitations", label: "Invitations" },
  { href: "/workspace/audit-logs", label: "Activity" },
  { href: "/workspace/settings", label: "Settings" },
];

/** Authenticated app frame: org switcher, primary nav, account menu, verification banner. */
export function WorkspaceShell({ children }: { children: ReactNode }) {
  const { user, memberships, currentOrg, selectOrg, logout } = useAuth();
  const pathname = usePathname();
  const [switcherOpen, setSwitcherOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [resendStatus, setResendStatus] = useState<string | null>(null);
  const switcherRef = useRef<HTMLDivElement>(null);
  const accountRef = useRef<HTMLDivElement>(null);
  const resendMutation = useResendVerification();

  useEffect(() => {
    function onPointerDown(event: MouseEvent) {
      if (switcherRef.current && !switcherRef.current.contains(event.target as Node)) {
        setSwitcherOpen(false);
      }
      if (accountRef.current && !accountRef.current.contains(event.target as Node)) {
        setAccountOpen(false);
      }
    }
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  const handleResendVerification = async () => {
    try {
      await resendMutation.mutateAsync();
      setResendStatus("Verification email sent! Check your inbox.");
    } catch {
      setResendStatus("Failed to dispatch verification email. Try again shortly.");
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <div className="flex items-center gap-4">
            <Link href="/workspace" className="font-bold tracking-tight text-teal-800">
              Biz<span className="text-slate-900">Grow</span>
            </Link>

            {/* Organization switcher */}
            {memberships.length > 0 && (
              <div className="relative" ref={switcherRef}>
                <button
                  type="button"
                  onClick={() => setSwitcherOpen((open) => !open)}
                  aria-expanded={switcherOpen}
                  aria-haspopup="listbox"
                  className="flex max-w-56 items-center gap-2 rounded-lg px-2.5 py-1.5 text-sm font-medium text-slate-700 ring-1 ring-inset ring-slate-200 hover:bg-slate-50"
                >
                  <span className="truncate">{currentOrg?.name ?? "Select workspace"}</span>
                  <svg className="size-4 shrink-0 text-slate-400" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M5.25 7.5 10 12.25 14.75 7.5h-9.5Z" />
                  </svg>
                </button>
                {switcherOpen && (
                  <ul
                    role="listbox"
                    className="absolute left-0 mt-2 w-64 rounded-lg bg-white p-1 shadow-lg ring-1 ring-slate-200"
                  >
                    {memberships.map((org) => (
                      <li key={org.organizationId}>
                        <button
                          type="button"
                          role="option"
                          aria-selected={org.organizationId === currentOrg?.organizationId}
                          onClick={() => {
                            setSwitcherOpen(false);
                            if (org.organizationId !== currentOrg?.organizationId) {
                              selectOrg(org.organizationId);
                            }
                          }}
                          className="flex w-full items-center justify-between gap-2 rounded-md px-3 py-2 text-left text-sm hover:bg-slate-50"
                        >
                          <span className="min-w-0">
                            <span className="block truncate font-medium text-slate-900">
                              {org.name}
                            </span>
                            <span className="block truncate text-xs text-slate-500">
                              {ROLE_LABELS[org.role]}
                            </span>
                          </span>
                          {org.organizationId === currentOrg?.organizationId && (
                            <span className="text-teal-700">✓</span>
                          )}
                        </button>
                      </li>
                    ))}
                    <li className="mt-1 border-t border-slate-100 pt-1">
                      <Link
                        href="/onboarding"
                        className="block rounded-md px-3 py-2 text-sm font-medium text-teal-700 hover:bg-teal-50"
                      >
                        + New workspace
                      </Link>
                    </li>
                  </ul>
                )}
              </div>
            )}
          </div>

          {/* Account menu */}
          <div className="relative" ref={accountRef}>
            <button
              type="button"
              onClick={() => setAccountOpen((open) => !open)}
              aria-expanded={accountOpen}
              aria-haspopup="menu"
              className="flex items-center gap-2 rounded-full px-2 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              <span className="flex size-7 items-center justify-center rounded-full bg-teal-700 text-xs font-semibold text-white">
                {user ? `${user.firstName[0] ?? ""}${user.lastName[0] ?? ""}` : "?"}
              </span>
              <span className="hidden sm:block">{user?.firstName}</span>
            </button>
            {accountOpen && (
              <div
                role="menu"
                className="absolute right-0 mt-2 w-56 rounded-lg bg-white p-1 shadow-lg ring-1 ring-slate-200"
              >
                <div className="px-3 py-2">
                  <p className="truncate text-sm font-medium text-slate-900">
                    {user?.firstName} {user?.lastName}
                  </p>
                  <p className="truncate text-xs text-slate-500">{user?.email}</p>
                </div>
                <div className="border-t border-slate-100 p-1">
                  <Button variant="ghost" className="w-full justify-start" onClick={() => void logout()}>
                    Sign out
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Primary nav */}
        <nav className="mx-auto max-w-6xl overflow-x-auto px-4 sm:px-6">
          <ul className="flex gap-1">
            {NAV_ITEMS.map((item) => {
              const active =
                item.href === "/workspace"
                  ? pathname === "/workspace"
                  : pathname.startsWith(item.href);
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={`inline-block whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium transition-colors ${
                      active
                        ? "border-teal-700 text-teal-800"
                        : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-800"
                    }`}
                  >
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      </header>

      {user && !user.emailVerified && (
        <div className="bg-blue-50 px-4 py-2.5 text-center text-sm text-blue-900 border-b border-blue-200 flex flex-wrap items-center justify-center gap-2">
          <span>Please verify your email address (<strong>{user.email}</strong>) to secure your account.</span>
          <button
            type="button"
            onClick={handleResendVerification}
            disabled={resendMutation.isPending}
            className="underline font-semibold hover:text-blue-700 cursor-pointer ml-1"
          >
            {resendMutation.isPending ? "Sending…" : "Resend verification link"}
          </button>
          {resendStatus && <span className="ml-2 font-medium text-blue-800">({resendStatus})</span>}
        </div>
      )}

      {currentOrg?.status === "SUSPENDED" && (
        <div className="bg-amber-50 px-4 py-2.5 text-center text-sm text-amber-800 ring-1 ring-inset ring-amber-200">
          <Badge status="SUSPENDED">Suspended</Badge> — this workspace is read-only
          until it is reactivated.
        </div>
      )}

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">{children}</main>
    </div>
  );
}
