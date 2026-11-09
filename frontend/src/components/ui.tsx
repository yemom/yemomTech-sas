/**
 * Design-system primitives shared by every screen: one Button, one Field,
 * one Card, one Badge — so spacing, focus rings and error styling stay
 * consistent without a component library.
 */
import Link from "next/link";
import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from "react";

/* -------------------------------- button -------------------------------- */

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary:
    "bg-teal-700 text-white hover:bg-teal-800 focus-visible:ring-teal-600 disabled:bg-teal-300",
  secondary:
    "bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50 focus-visible:ring-slate-400 disabled:opacity-50",
  danger:
    "bg-white text-rose-700 ring-1 ring-rose-200 hover:bg-rose-50 focus-visible:ring-rose-400 disabled:opacity-50",
  ghost:
    "text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus-visible:ring-slate-400 disabled:opacity-50",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "primary", loading = false, disabled, children, className = "", ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed ${BUTTON_STYLES[variant]} ${className}`}
      {...rest}
    >
      {loading && <Spinner className="size-4" />}
      {children}
    </button>
  );
});

export function LinkButton({
  href,
  children,
  variant = "primary",
  className = "",
}: {
  href: string;
  children: ReactNode;
  variant?: ButtonVariant;
  className?: string;
}) {
  return (
    <Link
      href={href}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ${BUTTON_STYLES[variant]} ${className}`}
    >
      {children}
    </Link>
  );
}

/* -------------------------------- spinner -------------------------------- */

export function Spinner({ className = "size-5" }: { className?: string }) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z" />
    </svg>
  );
}

export function PageLoader({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-slate-500" role="status">
      <Spinner />
      <span className="text-sm">{label}</span>
    </div>
  );
}

/* --------------------------------- field --------------------------------- */

interface FieldProps {
  label: string;
  error?: string | undefined;
  hint?: ReactNode;
  required?: boolean;
  children: ReactNode;
  htmlFor?: string;
}

export function Field({ label, error, hint, required, children, htmlFor }: FieldProps) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="text-rose-600"> *</span>}
      </label>
      {children}
      {error ? (
        <p className="mt-1.5 text-sm text-rose-600" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="mt-1.5 text-sm text-slate-500">{hint}</p>
      ) : null}
    </div>
  );
}

const INPUT_STYLES =
  "block w-full rounded-lg border-0 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-teal-600 aria-invalid:ring-rose-400 aria-invalid:focus:ring-rose-500";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(
  function Input({ invalid, className = "", ...rest }, ref) {
    return (
      <input
        ref={ref}
        aria-invalid={invalid || undefined}
        className={`${INPUT_STYLES} ${className}`}
        {...rest}
      />
    );
  },
);

export function Select({
  invalid,
  className = "",
  children,
  ...rest
}: React.SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return (
    <select
      aria-invalid={invalid || undefined}
      className={`${INPUT_STYLES} appearance-none ${className}`}
      {...rest}
    >
      {children}
    </select>
  );
}

export function Textarea({
  invalid,
  className = "",
  ...rest
}: React.TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return (
    <textarea
      aria-invalid={invalid || undefined}
      className={`${INPUT_STYLES} min-h-24 ${className}`}
      {...rest}
    />
  );
}

/* --------------------------------- card --------------------------------- */

export function Card({
  children,
  className = "",
  onClick,
}: {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
}) {
  return (
    <div
      onClick={onClick}
      className={`rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 ${className}`}
    >
      {children}
    </div>
  );
}

/* --------------------------------- badge --------------------------------- */

const BADGE_STYLES: Record<string, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  PENDING: "bg-amber-50 text-amber-700 ring-amber-200",
  INACTIVE: "bg-slate-100 text-slate-600 ring-slate-200",
  SUSPENDED: "bg-rose-50 text-rose-700 ring-rose-200",
  REMOVED: "bg-slate-100 text-slate-500 ring-slate-200",
  ACCEPTED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  REVOKED: "bg-slate-100 text-slate-500 ring-slate-200",
  EXPIRED: "bg-rose-50 text-rose-600 ring-rose-200",
  ARCHIVED: "bg-slate-100 text-slate-600 ring-slate-200",
  COMPLETED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  RUNNING: "bg-amber-50 text-amber-700 ring-amber-200",
  FAILED: "bg-rose-50 text-rose-700 ring-rose-200",
  CRITICAL: "bg-rose-50 text-rose-700 ring-rose-200",
  WARNING: "bg-amber-50 text-amber-700 ring-amber-200",
  INFO: "bg-blue-50 text-blue-700 ring-blue-200",
  SUCCESS: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  NEW: "bg-blue-50 text-blue-700 ring-blue-200",
  CONTACTED: "bg-indigo-50 text-indigo-700 ring-indigo-200",
  QUALIFIED: "bg-purple-50 text-purple-700 ring-purple-200",
  PROPOSAL: "bg-amber-50 text-amber-700 ring-amber-200",
  NEGOTIATION: "bg-cyan-50 text-cyan-700 ring-cyan-200",
  WON: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  LOST: "bg-rose-50 text-rose-700 ring-rose-200",
  HIGH: "bg-rose-50 text-rose-700 ring-rose-200",
  MEDIUM: "bg-amber-50 text-amber-700 ring-amber-200",
  LOW: "bg-blue-50 text-blue-700 ring-blue-200",
  OPEN: "bg-teal-50 text-teal-700 ring-teal-200",
  APPLIED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  DISMISSED: "bg-slate-100 text-slate-500 ring-slate-200",
  DRAFT: "bg-slate-100 text-slate-600 ring-slate-200",
  PAUSED: "bg-amber-50 text-amber-700 ring-amber-200",
  FULL: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  READ_ONLY: "bg-blue-50 text-blue-700 ring-blue-200",
  MARKETING_ONLY: "bg-purple-50 text-purple-700 ring-purple-200",
};

export function Badge({ status, children }: { status: string; children?: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${BADGE_STYLES[status] ?? BADGE_STYLES.INACTIVE}`}
    >
      {children ?? status}
    </span>
  );
}

/* ------------------------------ empty state ------------------------------ */

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 px-6 py-12 text-center">
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{description}</p>
      {action && <div className="mt-4 flex justify-center">{action}</div>}
    </div>
  );
}

/* ----------------------------- callout (errors) ----------------------------- */

export function ErrorCallout({ message, traceId }: { message: string; traceId?: string | undefined }) {
  if (!message) return null;
  return (
    <div className="rounded-lg bg-rose-50 px-4 py-3 text-sm text-rose-800 ring-1 ring-inset ring-rose-200" role="alert">
      <p>{message}</p>
      {traceId && <p className="mt-1 text-xs text-rose-500">Reference: {traceId}</p>}
    </div>
  );
}
