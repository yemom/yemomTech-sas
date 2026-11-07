"use client";

import Link from "next/link";
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorCallout,
  Field,
  Input,
  PageLoader,
} from "@/components/ui";
import { ApiError, errorMessage } from "@/lib/api/client";
import {
  useCreateWebsite,
  useDeactivateWebsite,
  useUpdateWebsite,
  useWebsites,
} from "@/lib/api/hooks";
import { can } from "@/lib/api/permissions";
import { websiteSchema, type WebsiteInput } from "@/lib/api/schemas";
import { useAuth } from "@/lib/auth/auth-context";
import type { Website } from "@/lib/api/types";

export default function WebsitesPage() {
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? null;
  const [page, setPage] = useState(0);
  const websites = useWebsites(orgId, page);
  const [editing, setEditing] = useState<Website | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const mayManage = currentOrg ? can(currentOrg.role, "WEBSITE_MANAGE") : false;

  if (!currentOrg) return <PageLoader label="Select a workspace…" />;
  if (websites.isPending) return <PageLoader label="Loading websites…" />;

  const data = websites.data;
  const content = data?.content ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Websites</h1>
          <p className="mt-1 text-sm text-slate-500">
            Domains registered to this workspace. Registration stores the URL —
            we never fetch it.
          </p>
        </div>
        {mayManage &&
          (showCreate ? (
            <Button variant="secondary" onClick={() => setShowCreate(false)}>
              Close
            </Button>
          ) : (
            <Button onClick={() => setShowCreate(true)}>Register website</Button>
          ))}
      </div>

      {showCreate && mayManage && (
        <WebsiteForm orgId={currentOrg.organizationId} onDone={() => setShowCreate(false)} />
      )}
      {editing && mayManage && (
        <WebsiteForm
          orgId={currentOrg.organizationId}
          website={editing}
          onDone={() => setEditing(null)}
        />
      )}

      {content.length === 0 ? (
        <EmptyState
          title="No websites yet"
          description={
            mayManage
              ? "Register the first domain your business runs on — name and URL are all we need."
              : "No websites have been registered in this workspace yet."
          }
          action={
            mayManage && !showCreate ? (
              <Button onClick={() => setShowCreate(true)}>Register website</Button>
            ) : undefined
          }
        />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-slate-600">Name</th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">Domain</th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">Status</th>
                <th className="px-4 py-3 text-left font-medium text-slate-600">Registered</th>
                {mayManage && <th className="px-4 py-3 text-right font-medium text-slate-600">Actions</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {content.map((site) => (
                <tr key={site.id}>
                  <td className="px-4 py-3 font-medium text-slate-900">{site.name}</td>
                  <td className="px-4 py-3">
                    <a
                      href={site.url}
                      target="_blank"
                      rel="noreferrer noopener"
                      className="text-teal-700 hover:underline"
                    >
                      {site.domain}
                    </a>
                  </td>
                  <td className="px-4 py-3">
                    <Badge status={site.status} />
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {new Intl.DateTimeFormat("en-GB", {
                      day: "numeric",
                      month: "short",
                      year: "numeric",
                      timeZone: "Africa/Addis_Ababa",
                    }).format(new Date(site.createdAt))}
                  </td>
                  {mayManage && (
                    <td className="px-4 py-3 text-right">
                      <div className="inline-flex gap-1">
                        <Link href={`/workspace/websites/${site.id}/audits`}>
                          <Button variant="ghost">Audits</Button>
                        </Link>
                        <Button variant="ghost" onClick={() => setEditing(site)}>
                          Edit
                        </Button>
                        {site.status === "ACTIVE" && (
                          <DeactivateButton orgId={currentOrg.organizationId} website={site} />
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-500">
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button variant="secondary" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

/* ------------------------------ create / edit ------------------------------ */

function WebsiteForm({
  orgId,
  website,
  onDone,
}: {
  orgId: string;
  website?: Website;
  onDone: () => void;
}) {
  const create = useCreateWebsite(orgId);
  const update = useUpdateWebsite(orgId);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<WebsiteInput>({
    resolver: zodResolver(websiteSchema),
    defaultValues: website ? { name: website.name, url: website.url } : undefined,
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      if (website) {
        await update.mutateAsync({
          websiteId: website.id,
          body: { name: values.name.trim(), url: values.url.trim() },
        });
      } else {
        await create.mutateAsync({ name: values.name.trim(), url: values.url.trim() });
      }
      onDone();
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as keyof WebsiteInput, { message });
        }
        return;
      }
      if (error instanceof ApiError && error.status === 409) {
        setFormError(
          "This domain is already registered in your workspace — check the websites list.",
        );
        return;
      }
      setFormError(errorMessage(error));
    }
  });

  return (
    <Card>
      <h2 className="text-base font-semibold text-slate-900">
        {website ? "Edit website" : "Register a website"}
      </h2>
      <form onSubmit={onSubmit} noValidate className="mt-4 max-w-xl space-y-4">
        {formError && <ErrorCallout message={formError} />}
        <Field label="Name" error={errors.name?.message} htmlFor="site-name" required>
          <Input
            id="site-name"
            placeholder="e.g. Company site"
            invalid={Boolean(errors.name)}
            {...register("name")}
          />
        </Field>
        <Field
          label="URL"
          error={errors.url?.message}
          htmlFor="site-url"
          hint="http:// or https://, public domains only (localhost and IP addresses are rejected)."
          required
        >
          <Input
            id="site-url"
            type="url"
            placeholder="https://www.example.et"
            invalid={Boolean(errors.url)}
            {...register("url")}
          />
        </Field>
        <div className="flex gap-3">
          <Button type="submit" loading={isSubmitting || create.isPending || update.isPending}>
            {website ? "Save changes" : "Register website"}
          </Button>
          <Button type="button" variant="secondary" onClick={onDone}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  );
}

/* -------------------------------- deactivate -------------------------------- */

function DeactivateButton({ orgId, website }: { orgId: string; website: Website }) {
  const deactivate = useDeactivateWebsite(orgId);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!confirming) {
    return (
      <Button variant="danger" onClick={() => setConfirming(true)}>
        Deactivate
      </Button>
    );
  }

  return (
    <span className="inline-flex items-center gap-2">
      {error ? (
        <span className="text-xs text-rose-600">{error}</span>
      ) : (
        <span className="text-xs text-slate-500">Deactivate “{website.name}”?</span>
      )}
      <Button
        variant="danger"
        loading={deactivate.isPending}
        onClick={async () => {
          try {
            await deactivate.mutateAsync(website.id);
            setConfirming(false);
          } catch (caught) {
            setError(errorMessage(caught));
          }
        }}
      >
        Yes, deactivate
      </Button>
      <Button variant="ghost" onClick={() => { setConfirming(false); setError(null); }}>
        Cancel
      </Button>
    </span>
  );
}
