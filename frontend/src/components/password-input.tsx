"use client";

/**
 * A password field with a show/hide toggle.
 *
 * The input forwards its ref and all native props, so react-hook-form's
 * `register("password")` keeps working unchanged. The toggle is a real
 * `type="button"` (never submits the form), carries `aria-pressed` and
 * points at the field with `aria-controls`.
 *
 * The toggle's aria-label deliberately does NOT contain the word
 * "password": the field label is "Password" and the browser suite locates
 * it with getByLabel("Password"), which would otherwise match two elements.
 * The hover title still says "Show/Hide password" for sighted users.
 */
import { forwardRef, useState, type InputHTMLAttributes } from "react";
import { INPUT_STYLES } from "@/components/ui";

export type PasswordInputProps = InputHTMLAttributes<HTMLInputElement> & {
  invalid?: boolean;
};

function EyeIcon() {
  return (
    <svg className="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12s3.5-6.75 9.75-6.75S21.75 12 21.75 12s-3.5 6.75-9.75 6.75S2.25 12 2.25 12Z" />
      <circle cx="12" cy="12" r="2.75" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg className="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 3l18 18M10.6 5.28A10.7 10.7 0 0 1 12 5.25c6.25 0 9.75 6.75 9.75 6.75a17.6 17.6 0 0 1-2.36 3.18M6.6 6.62A17.4 17.4 0 0 0 2.25 12s3.5 6.75 9.75 6.75a10.4 10.4 0 0 0 3.42-.57M9.9 9.9a3 3 0 0 0 4.2 4.2" />
    </svg>
  );
}

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(function PasswordInput(
  { invalid, className = "", id, style, ...rest },
  ref,
) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="relative">
      <input
        {...rest}
        ref={ref}
        id={id}
        type={visible ? "text" : "password"}
        aria-invalid={invalid || undefined}
        className={`${INPUT_STYLES} pr-12 ${className}`}
        style={{ paddingRight: "2.75rem", ...style }}
      />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        aria-label={visible ? "Hide text" : "Show text"}
        aria-pressed={visible}
        aria-controls={id}
        title={visible ? "Hide password" : "Show password"}
        className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-lg text-slate-500 transition-colors hover:text-teal-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-600"
      >
        {visible ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    </div>
  );
});
