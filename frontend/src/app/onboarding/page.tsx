"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Button, ErrorCallout, Field, Input, Textarea } from "@/components/ui";
import { ApiError, errorMessage } from "@/lib/api/client";
import { useCreateOrganization } from "@/lib/api/hooks";
import { organizationSchema, type OrganizationInput } from "@/lib/api/schemas";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Onboarding: create the first workspace. Reachable any time from the org
 * switcher ("+ New workspace"), so it also serves multi-workspace users.
 */
export default function OnboardingPage() {
  const { status, memberships, reloadMe, selectOrg } = useAuth();
  const router = useRouter();
  const createOrg = useCreateOrganization();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<OrganizationInput>({ resolver: zodResolver(organizationSchema) });

  // Anonymous users are sent to sign-in; members with a workspace can leave
  // this page freely (it doubles as "create another workspace").
  useEffect(() => {
    if (status === "anonymous") router.replace("/login?next=/onboarding");
  }, [status, router]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const org = await createOrg.mutateAsync({
        name: values.name.trim(),
        ...(values.industry?.trim() ? { industry: values.industry.trim() } : {}),
        ...(values.websiteUrl?.trim() ? { websiteUrl: values.websiteUrl.trim() } : {}),
      });
      // Refresh memberships so the switcher shows the new workspace…
      await reloadMe();
      // …then land on its dashboard (backend response gives us the id directly).
      selectOrg(org.id);
      router.push("/workspace");
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as keyof OrganizationInput, { message });
        }
        return;
      }
      if (error instanceof ApiError && error.status === 409) {
        setFormError("You already have a workspace with this name.");
        return;
      }
      setFormError(errorMessage(error));
    }
  });

  if (status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-slate-500">
        Loading…
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-12">
      <div className="w-full max-w-lg">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-slate-900">Create your workspace</h1>
          <p className="mt-1 text-sm text-slate-500">
            A workspace holds your websites, team members and settings. You can
            create more later.
          </p>
        </div>

        <form
          onSubmit={onSubmit}
          noValidate
          className="space-y-4 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200"
        >
          {formError && <ErrorCallout message={formError} />}

          <Field label="Organization name" error={errors.name?.message} htmlFor="name" required>
            <Input
              id="name"
              placeholder="e.g. Habesha Foods PLC"
              invalid={Boolean(errors.name)}
              {...register("name")}
            />
          </Field>

          <Field label="Industry (optional)" error={errors.industry?.message} htmlFor="industry">
            <Input
              id="industry"
              placeholder="e.g. Food & Beverage"
              invalid={Boolean(errors.industry)}
              {...register("industry")}
            />
          </Field>

          <Field
            label="Company website (optional)"
            error={errors.websiteUrl?.message}
            htmlFor="websiteUrl"
            hint="Your marketing site — separate from registering sites later."
          >
            <Input
              id="websiteUrl"
              type="url"
              placeholder="https://example.et"
              invalid={Boolean(errors.websiteUrl)}
              {...register("websiteUrl")}
            />
          </Field>

          <Field label="Description (optional)" error={errors.description?.message} htmlFor="description">
            <Textarea
              id="description"
              placeholder="What does your business do?"
              invalid={Boolean(errors.description)}
              {...register("description")}
            />
          </Field>

          <div className="flex items-center justify-between gap-3 pt-2">
            {memberships.length > 0 ? (
              <Button type="button" variant="ghost" onClick={() => router.push("/workspace")}>
                Back to dashboard
              </Button>
            ) : (
              <span className="text-xs text-slate-400">Country: Ethiopia · Currency: ETB</span>
            )}
            <Button type="submit" loading={isSubmitting || createOrg.isPending}>
              Create workspace
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
