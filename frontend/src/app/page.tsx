import Link from "next/link";
import { LinkButton } from "@/components/ui";

const FEATURES = [
  {
    title: "One workspace per business",
    body: "Create your organization, invite your team, and keep every member's access scoped to exactly the workspace they belong to.",
  },
  {
    title: "Website registry",
    body: "Register the domains your business runs on, with validation that keeps bad URLs out and duplicate registrations impossible.",
  },
  {
    title: "Roles that match your team",
    body: "Owner, admin, marketing, sales and analyst roles — permissions follow the role, and the last owner can never be removed.",
  },
  {
    title: "Built for Ethiopia",
    body: "ETB currency, Addis Ababa timezone and local defaults from day one — no configuration needed to get started.",
  },
  {
    title: "Secure by default",
    body: "Short-lived access tokens, rotating refresh sessions, and audit trails on every change made inside your workspace.",
  },
  {
    title: "Honest numbers",
    body: "Dashboards show only real data recorded in your workspace. No vanity metrics, no fabricated insights.",
  },
];

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-white">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
        <span className="text-lg font-bold tracking-tight text-teal-800">
          Yemom<span className="text-slate-900">Tech</span>
        </span>
        <nav className="flex items-center gap-3">
          <Link
            href="/login"
            className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
          >
            Sign in
          </Link>
          <LinkButton href="/register">Get started</LinkButton>
        </nav>
      </header>

      <main>
        <section className="mx-auto max-w-4xl px-6 py-20 text-center">
          <p className="mb-4 inline-flex items-center gap-2 rounded-full bg-teal-50 px-3 py-1 text-xs font-medium text-teal-700 ring-1 ring-inset ring-teal-200">
            Built in Addis Ababa · Powered by real data
          </p>
          <h1 className="text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl">
            Run your business website and team from one secure workspace.
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-lg text-slate-600">
            YemomTech (YemomBizGrow) gives Ethiopian businesses a shared workspace for
            websites, team members and the work that grows the business — with
            roles, audit trails and honest reporting built in.
          </p>
          <div className="mt-8 flex items-center justify-center gap-4">
            <LinkButton href="/register" className="px-6 py-3 text-base">
              Create your workspace
            </LinkButton>
            <LinkButton href="/login" variant="secondary" className="px-6 py-3 text-base">
              Sign in
            </LinkButton>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 pb-24">
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((feature) => (
              <div
                key={feature.title}
                className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200"
              >
                <h3 className="text-base font-semibold text-slate-900">{feature.title}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">{feature.body}</p>
              </div>
            ))}
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-200">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-2 px-6 py-6 text-sm text-slate-500 sm:flex-row">
          <span>YemomTech (YemomBizGrow)</span>
          <span>Addis Ababa, Ethiopia · Africa/Addis_Ababa</span>
        </div>
      </footer>
    </div>
  );
}
