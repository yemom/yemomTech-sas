"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Button, ErrorCallout, PageLoader } from "@/components/ui";
import { errorMessage } from "@/lib/api/client";
import { useVerifyEmail } from "@/lib/api/hooks";

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<PageLoader label="Verifying…" />}>
      <VerifyEmailContent />
    </Suspense>
  );
}

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<"idle" | "verifying" | "success" | "error">(token ? "verifying" : "idle");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const verifyMutation = useVerifyEmail();

  useEffect(() => {
    if (!token) return;
    verifyMutation
      .mutateAsync({ token })
      .then(() => setStatus("success"))
      .catch((err) => {
        setStatus("error");
        setErrorMsg(errorMessage(err));
      });
  }, [token]);

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-12">
      <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 text-center">
        <Link href="/" className="inline-block text-lg font-bold tracking-tight text-teal-800 mb-6">
          Yemom<span className="text-slate-900">BizGrow</span>
        </Link>

        {status === "verifying" && (
          <div className="py-6">
            <PageLoader label="Verifying your email address…" />
          </div>
        )}

        {status === "success" && (
          <div>
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-teal-100 text-teal-700">
              <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h1 className="mt-4 text-xl font-bold text-slate-900">Email Verified!</h1>
            <p className="mt-2 text-sm text-slate-600">
              Your email address has been confirmed. You now have full access to all verified features.
            </p>
            <div className="mt-6">
              <Link href="/workspace">
                <Button className="w-full">Go to Workspace</Button>
              </Link>
            </div>
          </div>
        )}

        {status === "error" && (
          <div>
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-700">
              <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h1 className="mt-4 text-xl font-bold text-slate-900">Verification Failed</h1>
            <p className="mt-2 text-sm text-slate-600">
              {errorMsg ?? "The verification link is invalid or has expired."}
            </p>
            <div className="mt-6 flex flex-col gap-2">
              <Link href="/workspace">
                <Button variant="secondary" className="w-full">Return to Workspace</Button>
              </Link>
            </div>
          </div>
        )}

        {status === "idle" && (
          <div>
            <h1 className="text-xl font-bold text-slate-900">Email Verification</h1>
            <p className="mt-2 text-sm text-slate-600">
              No verification token was provided in the link. Please check your verification email and click the link.
            </p>
            <div className="mt-6">
              <Link href="/workspace">
                <Button className="w-full">Go to Workspace</Button>
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
