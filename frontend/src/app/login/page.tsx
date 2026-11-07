"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Button, ErrorCallout, Field, Input, PageLoader } from "@/components/ui";
import { ApiError, errorMessage } from "@/lib/api/client";
import { loginSchema, type LoginInput } from "@/lib/api/schemas";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * useSearchParams requires a Suspense boundary when the route is statically
 * prerendered, so the real form lives in an inner component.
 */
export default function LoginPage() {
  return (
    <Suspense fallback={<PageLoader label="Loading…" />}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const { status, login } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = searchParams.get("next");
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({ resolver: zodResolver(loginSchema) });

  // Already signed in? Continue to the requested page (or the workspace).
  useEffect(() => {
    if (status === "authenticated") {
      router.replace(safeNext(next) ?? "/workspace");
    }
  }, [status, next, router]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await login(values.email, values.password, next ?? undefined);
    } catch (error) {
      // 401 is deliberately generic on the backend — mirror that here.
      if (error instanceof ApiError && error.status === 401) {
        setFormError("Incorrect email or password.");
        return;
      }
      if (error instanceof ApiError && error.status === 429) {
        setFormError(error.message);
        return;
      }
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as keyof LoginInput, { message });
        }
        return;
      }
      setFormError(errorMessage(error));
    }
  });

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <Link href="/" className="text-lg font-bold tracking-tight text-teal-800">
            Yemom<span className="text-slate-900">Tech</span>
          </Link>
          <h1 className="mt-4 text-2xl font-bold text-slate-900">Welcome back</h1>
          <p className="mt-1 text-sm text-slate-500">Sign in to your workspace.</p>
        </div>

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

          <Field label="Password" error={errors.password?.message} htmlFor="password" required>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              invalid={Boolean(errors.password)}
              {...register("password")}
            />
          </Field>

          <Button type="submit" loading={isSubmitting} className="w-full">
            {isSubmitting ? "Signing in…" : "Sign in"}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          New here?{" "}
          <Link href="/register" className="font-medium text-teal-700 hover:text-teal-800">
            Create an account
          </Link>
        </p>
      </div>
    </div>
  );
}

/** Only same-app absolute paths (open-redirect guard); null when unsafe. */
function safeNext(value: string | null): string | null {
  if (!value || !value.startsWith("/") || value.startsWith("//")) return null;
  return value;
}
