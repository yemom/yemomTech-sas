"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { PageLoader } from "@/components/ui";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Client-side gate for authenticated areas.
 * - loading → spinner (session is being re-established via refresh cookie)
 * - anonymous → /login with a `next` param so sign-in returns to the page
 * - authenticated → render children
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [status, pathname, router]);

  if (status !== "authenticated") {
    return <PageLoader label="Checking your session…" />;
  }
  return <>{children}</>;
}
