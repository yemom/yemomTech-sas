"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button, ErrorCallout, Field, Input } from "@/components/ui";
import { ApiError, errorMessage } from "@/lib/api/client";
import { PASSWORD_RULES, registerSchema, type RegisterInput } from "@/lib/api/schemas";
import { useAuth } from "@/lib/auth/auth-context";

export default function RegisterPage() {
  const { register: registerUser } = useAuth();
  const [formError, setFormError] = useState<string | null>(null);
  const [passwordValue, setPasswordValue] = useState("");

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterInput>({ resolver: zodResolver(registerSchema) });

  // NOTE: no auto-redirect here — register() itself navigates to /onboarding
  // on success. An authenticated user landing on this page is redirected by
  // the login page's effect or can use the sign-in link below.

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await registerUser({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        email: values.email.trim(),
        password: values.password,
        // The backend pattern rejects ""; omit the field when empty.
        ...(values.phoneNumber?.trim() ? { phoneNumber: values.phoneNumber.trim() } : {}),
      });
      // AuthProvider redirects to /onboarding on success.
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as keyof RegisterInput, { message });
        }
        return;
      }
      if (error instanceof ApiError && error.status === 409) {
        setFormError("An account with this email already exists. Try signing in.");
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
          <h1 className="mt-4 text-2xl font-bold text-slate-900">Create your account</h1>
          <p className="mt-1 text-sm text-slate-500">
            Then create a workspace for your business.
          </p>
        </div>

        <form
          onSubmit={onSubmit}
          noValidate
          className="space-y-4 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200"
        >
          {formError && <ErrorCallout message={formError} />}

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="First name" error={errors.firstName?.message} htmlFor="firstName" required>
              <Input
                id="firstName"
                autoComplete="given-name"
                invalid={Boolean(errors.firstName)}
                {...register("firstName")}
              />
            </Field>
            <Field label="Last name" error={errors.lastName?.message} htmlFor="lastName" required>
              <Input
                id="lastName"
                autoComplete="family-name"
                invalid={Boolean(errors.lastName)}
                {...register("lastName")}
              />
            </Field>
          </div>

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

          <Field label="Phone (optional)" error={errors.phoneNumber?.message} htmlFor="phoneNumber">
            <Input
              id="phoneNumber"
              type="tel"
              autoComplete="tel"
              placeholder="+251911234567"
              invalid={Boolean(errors.phoneNumber)}
              {...register("phoneNumber")}
            />
          </Field>

          <Field
            label="Password"
            error={errors.password?.message}
            htmlFor="password"
            hint="8-128 characters with upper, lower, digit and symbol."
            required
          >
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              invalid={Boolean(errors.password)}
              {...register("password", {
                onChange: (e) => setPasswordValue(e.target.value),
              })}
            />
          </Field>

          {/* Live rule checklist mirrors the backend PasswordValidator. */}
          <ul className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
            {PASSWORD_RULES.filter((rule) => rule.label !== "at most 128 characters").map(
              (rule) => {
                const ok = passwordValue.length > 0 && rule.test(passwordValue);
                return (
                  <li
                    key={rule.label}
                    className={ok ? "text-emerald-600" : "text-slate-400"}
                  >
                    {ok ? "✓" : "○"} {rule.label}
                  </li>
                );
              },
            )}
          </ul>

          <Button type="submit" loading={isSubmitting} className="w-full">
            {isSubmitting ? "Creating account…" : "Create account"}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-teal-700 hover:text-teal-800">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
