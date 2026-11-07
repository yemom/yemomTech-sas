"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button, ErrorCallout, Field, Input } from "@/components/ui";
import { errorMessage } from "@/lib/api/client";
import { useForgotPassword } from "@/lib/api/hooks";
import { forgotPasswordSchema, type ForgotPasswordInput } from "@/lib/api/schemas";

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const forgotMutation = useForgotPassword();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordInput>({ resolver: zodResolver(forgotPasswordSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await forgotMutation.mutateAsync({ email: values.email });
      setSubmitted(true);
    } catch (err) {
      setFormError(errorMessage(err));
    }
  });

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <Link href="/" className="text-lg font-bold tracking-tight text-teal-800">
            Yemom<span className="text-slate-900">BizGrow</span>
          </Link>
          <h1 className="mt-4 text-2xl font-bold text-slate-900">Reset your password</h1>
          <p className="mt-1 text-sm text-slate-500">
            Enter your email and we will send you instructions to reset your password.
          </p>
        </div>

        {submitted ? (
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-teal-100 text-teal-700">
              <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className="mt-4 text-lg font-semibold text-slate-900">Check your inbox</h2>
            <p className="mt-2 text-sm text-slate-600">
              If an account with that email exists, we have sent a secure link to reset your password.
            </p>
            <div className="mt-6">
              <Link href="/login" className="inline-block text-sm font-medium text-teal-700 hover:text-teal-800">
                Return to sign in
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

            <Field label="Email" error={errors.email?.message} htmlFor="email" required>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@business.et"
                invalid={Boolean(errors.email)}
                {...register("email")}
              />
            </Field>

            <Button type="submit" loading={isSubmitting} className="w-full">
              {isSubmitting ? "Sending instructions…" : "Send reset instructions"}
            </Button>
          </form>
        )}

        <p className="mt-6 text-center text-sm text-slate-500">
          Remember your password?{" "}
          <Link href="/login" className="font-medium text-teal-700 hover:text-teal-800">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
