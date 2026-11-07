"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import {
  Badge,
  Button,
  Card,
  ErrorCallout,
  Field,
  Input,
  PageLoader,
  Textarea,
} from "@/components/ui";
import { ApiError, errorMessage } from "@/lib/api/client";
import { useUpdateOrganization } from "@/lib/api/hooks";
import { can } from "@/lib/api/permissions";
import { organizationSchema, type OrganizationInput } from "@/lib/api/schemas";
import { useAuth } from "@/lib/auth/auth-context";
import { api } from "@/lib/api/client";

export default function SettingsPage() {
  const { currentOrg, reloadMe } = useAuth();
  const orgId = currentOrg?.organizationId ?? null;
  const update = useUpdateOrganization(orgId ?? "-");
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [archiveError, setArchiveError] = useState<string | null>(null);

  const mayUpdate = currentOrg ? can(currentOrg.role, "ORG_UPDATE") : false;
  const mayArchive = currentOrg ? can(currentOrg.role, "ORG_DELETE") : false;

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<OrganizationInput>({ resolver: zodResolver(organizationSchema) });

  // Load the current profile once the org is known.
  useEffect(() => {
    if (!orgId) return;
    let cancelled = false;
    void (async () => {
      try {
        const org = await api.get<{
          name: string;
          industry: string | null;
          description: string | null;
          websiteUrl: string | null;
        }>(`/organizations/${orgId}`);
        if (!cancelled) {
          reset({
            name: org.name,
            industry: org.industry ?? "",
            description: org.description ?? "",
            websiteUrl: org.websiteUrl ?? "",
          });
        }
      } catch {
        // Form stays empty; submit will surface the error if the load failed.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [orgId, reset]);

  if (!currentOrg) return <PageLoader label="Select a workspace…" />;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    setSaved(false);
    try {
      await update.mutateAsync({
        name: values.name.trim(),
        industry: values.industry?.trim() ?? "",
        description: values.description?.trim() ?? "",
        websiteUrl: values.websiteUrl?.trim() ?? "",
      });
      await reloadMe();
      setSaved(true);
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as keyof OrganizationInput, { message });
        }
        return;
      }
      if (error instanceof ApiError && error.status === 404) {
        setFormError("This workspace no longer exists.");
        return;
      }
      setFormError(errorMessage(error));
    }
  });

  async function archive() {
    setArchiveError(null);
    try {
      await api.delete<void>(`/organizations/${orgId}`);
      await reloadMe();
    } catch (error) {
      setArchiveError(errorMessage(error));
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Settings</h1>
        <p className="mt-1 text-sm text-slate-500">
          Workspace profile and lifecycle. Defaults: Ethiopia · ETB ·
          Africa/Addis_Ababa.
        </p>
      </div>

      <Card className="max-w-2xl">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-slate-900">
            Workspace profile
          </h2>
          <Badge status={currentOrg.status} />
        </div>

        {!mayUpdate ? (
          <p className="mt-4 text-sm text-slate-500">
            Only owners and admins can edit the workspace profile. You can view
            the current values below.
          </p>
        ) : (
          <form onSubmit={onSubmit} noValidate className="mt-4 space-y-4">
            {formError && <ErrorCallout message={formError} />}
            {saved && (
              <div
                className="rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-800 ring-1 ring-inset ring-emerald-200"
                role="status"
              >
                Workspace profile saved.
              </div>
            )}

            <Field
              label="Organization name"
              error={errors.name?.message}
              htmlFor="org-name"
              required
            >
              <Input
                id="org-name"
                disabled={update.isPending}
                invalid={Boolean(errors.name)}
                {...register("name")}
              />
            </Field>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                label="Industry"
                error={errors.industry?.message}
                htmlFor="org-industry"
              >
                <Input
                  id="org-industry"
                  placeholder="e.g. Retail"
                  disabled={update.isPending}
                  invalid={Boolean(errors.industry)}
                  {...register("industry")}
                />
              </Field>
              <Field
                label="Company website"
                error={errors.websiteUrl?.message}
                htmlFor="org-website"
              >
                <Input
                  id="org-website"
                  type="url"
                  placeholder="https://example.et"
                  disabled={update.isPending}
                  invalid={Boolean(errors.websiteUrl)}
                  {...register("websiteUrl")}
                />
              </Field>
            </div>

            <Field
              label="Description"
              error={errors.description?.message}
              htmlFor="org-description"
            >
              <Textarea
                id="org-description"
                placeholder="What does your business do?"
                disabled={update.isPending}
                invalid={Boolean(errors.description)}
                {...register("description")}
              />
            </Field>

            <p className="text-xs text-slate-400">
              Slug: {currentOrg.slug} · Country: ET · Currency: ETB · Timezone:{" "}
              Africa/Addis_Ababa (fixed at creation)
            </p>

            <Button
              type="submit"
              loading={isSubmitting || update.isPending}
              disabled={!isDirty}
            >
              Save changes
            </Button>
          </form>
        )}
      </Card>

      {mayArchive && (
        <Card className="max-w-2xl">
          <h2 className="text-base font-semibold text-slate-900">
            Archive workspace
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Archiving hides the workspace from every member and blocks all
            changes. The data is retained — this is not deletion. Only owners
            can archive.
          </p>
          {archiveError && (
            <div className="mt-4">
              <ErrorCallout message={archiveError} />
            </div>
          )}
          <div className="mt-4 flex items-center gap-3">
            {!archiving ? (
              <Button variant="danger" onClick={() => setArchiving(true)}>
                Archive workspace…
              </Button>
            ) : (
              <>
                <Button variant="danger" onClick={() => void archive()}>
                  Yes, archive “{currentOrg.name}”
                </Button>
                <Button variant="ghost" onClick={() => setArchiving(false)}>
                  Cancel
                </Button>
              </>
            )}
          </div>
        </Card>
      )}
    </div>
  );
}
