"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";
import { Button, ErrorCallout, Field, Input, PageLoader } from "@/components/ui";
import { errorMessage } from "@/lib/api/client";
import { useResetPassword } from "@/lib/api/hooks";
import { resetPasswordSchema, type ResetPasswordInput } from "@/lib/api/schemas";

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<PageLoader label="Loading…" />}>
      <ResetPasswordForm />
    </Suspense>
  );
}

function ResetPasswordForm() {
  const searchParams = useSearchParams();
  const tokenFromUrl = searchParams.get("token") ?? "";
  const [success, setSuccess] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const resetMutation = useResetPassword();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordInput>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { token: tokenFromUrl, newPassword: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await resetMutation.mutateAsync({ token: values.token, newPassword: values.newPassword });
      setSuccess(true);
    } catch (err) {
      setFormError(errorMessage(err));
    }
  });

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <Link href="/" className="text-lg font-bold tracking-tight text-teal-800">
            Biz<span className="text-slate-900">Grow</span>
          </Link>
          <h1 className="mt-4 text-2xl font-bold text-slate-900">Set new password</h1>
          <p className="mt-1 text-sm text-slate-500">
            Choose a strong password with at least 8 characters.
          </p>
        </div>

        {success ? (
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-teal-100 text-teal-700">
              <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className="mt-4 text-lg font-semibold text-slate-900">Password updated</h2>
            <p className="mt-2 text-sm text-slate-600">
              Your password has been reset and all previous sessions have been signed out.
            </p>
            <div className="mt-6">
              <Link href="/login" className="inline-block text-sm font-medium text-teal-700 hover:text-teal-800">
                Sign in with new password
              </Link>
            </div>
          </div>
        ) : (
          <form
            onSubmit={onSubmit}
            noValidate
            className="space-y-4 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200"
          >
            {formError && <ErrorCallout message={formError} />}

            <Field label="Reset Token" error={errors.token?.message} htmlFor="token" required>
              <Input
                id="token"
                type="text"
                placeholder="Paste token if not in URL"
                invalid={Boolean(errors.token)}
                {...register("token")}
              />
            </Field>

            <Field label="New Password" error={errors.newPassword?.message} htmlFor="newPassword" required>
              <Input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                invalid={Boolean(errors.newPassword)}
                {...register("newPassword")}
              />
            </Field>

            <Button type="submit" loading={isSubmitting} className="w-full">
              {isSubmitting ? "Updating password…" : "Reset password"}
            </Button>
          </form>
        )}
      </div>
    </div>
  );
}
