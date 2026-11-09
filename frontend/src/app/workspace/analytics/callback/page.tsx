"use client";

import { useEffect, useState, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button, Card, ErrorCallout, Spinner } from "@/components/ui";
import { useGoogleCallback } from "@/lib/api/hooks";
import { useAuth } from "@/lib/auth/auth-context";

export default function GoogleCallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? "";

  const [error, setError] = useState<string | null>(null);
  const attemptedRef = useRef(false);

  const callbackMutation = useGoogleCallback(orgId);

  useEffect(() => {
    if (attemptedRef.current) return;

    const code = searchParams.get("code");
    const state = searchParams.get("state");
    const oauthError = searchParams.get("error");

    if (oauthError) {
      setError(`Google authorization was denied or failed: ${oauthError}`);
      return;
    }

    if (!code || !state) {
      setError("Missing authorization code or state parameter from Google.");
      return;
    }

    const websiteId =
      typeof window !== "undefined"
        ? sessionStorage.getItem("google_oauth_website_id")
        : null;

    if (!websiteId) {
      setError("Could not associate callback with a website. Please try connecting again from the Analytics page.");
      return;
    }

    if (!orgId) {
      // Organization context not ready yet
      return;
    }

    attemptedRef.current = true;

    callbackMutation
      .mutateAsync({ websiteId, code, state })
      .then(() => {
        if (typeof window !== "undefined") {
          sessionStorage.removeItem("google_oauth_website_id");
          sessionStorage.removeItem("google_oauth_org_id");
        }
        router.push("/workspace/analytics");
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : "Failed to exchange Google authorization code.";
        setError(message);
      });
  }, [searchParams, orgId, callbackMutation, router]);

  return (
    <div className="flex min-h-[60vh] items-center justify-center p-4">
      <Card className="w-full max-w-md text-center space-y-4 py-8">
        {error ? (
          <>
            <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-red-100 text-red-600 font-bold text-xl">
              ✕
            </div>
            <h2 className="text-lg font-bold text-slate-900">Connection Failed</h2>
            <ErrorCallout message={error} />
            <div className="pt-2">
              <Button variant="primary" onClick={() => router.push("/workspace/analytics")}>
                Return to Analytics
              </Button>
            </div>
          </>
        ) : (
          <>
            <Spinner className="mx-auto size-8 text-teal-600" />
            <h2 className="text-lg font-semibold text-slate-900">
              Connecting Google Analytics & Search Console
            </h2>
            <p className="text-sm text-slate-500">
              Verifying credentials and exchanging OAuth authorization code...
            </p>
          </>
        )}
      </Card>
    </div>
  );
}
