"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { PageLoader } from "@/components/ui";
import { RequireAuth } from "@/components/require-auth";
import { WorkspaceShell } from "@/components/workspace-shell";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Workspace frame. Two gates:
 * 1. RequireAuth — session must exist (refresh bootstrap → /auth/me).
 * 2. No membership → /onboarding (a workspace must exist before any
 *    workspace page can load data; otherwise queries stay disabled forever).
 */
export default function WorkspaceLayout({ children }: { children: ReactNode }) {
  const { status, memberships } = useAuth();
  const router = useRouter();
  const hasOrg = memberships.length > 0;

  useEffect(() => {
    if (status === "authenticated" && !hasOrg) {
      router.replace("/onboarding");
    }
  }, [status, hasOrg, router]);

  return (
    <RequireAuth>
      {status === "authenticated" && !hasOrg ? (
        <PageLoader label="Setting up your workspace…" />
      ) : (
        <WorkspaceShell>{children}</WorkspaceShell>
      )}
    </RequireAuth>
  );
}
