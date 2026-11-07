/**
 * The single HTTP entry point for the whole app.
 *
 * Security model (mirrors the backend contract):
 * - The access token lives in a module-level variable — never localStorage,
 *   sessionStorage, or a cookie — so an XSS payload cannot persist it after a
 *   reload (the session is re-established through the HttpOnly refresh cookie).
 * - The refresh/logout endpoints are cookie-authenticated and require the
 *   double-submit CSRF header. The CSRF value is readable by JS by design, so
 *   it is mirrored into localStorage (the cookie itself is path-scoped to
 *   /api/v1/auth and therefore invisible to document.cookie on app pages).
 * - A 401 triggers ONE refresh (single-flight per tab, serialized across tabs
 *   via the Web Locks API so two tabs cannot rotate the same cookie twice),
 *   then the original request is retried once.
 */
import type { AuthSession, ErrorBody } from "./types";

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082/api/v1";

const CSRF_STORAGE_KEY = "bizgrow_csrf";

/* ------------------------------ errors ------------------------------ */

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string> | undefined;
  readonly traceId: string | undefined;

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors?: Record<string, string>,
    traceId?: string,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.traceId = traceId;
  }
}

/** Human-readable message for any thrown value. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return "Something went wrong. Please try again.";
}

/* --------------------------- session storage --------------------------- */

let accessToken: string | null = null;
let csrfToken: string | null = null;
let hydrated = false;

function hydrate(): void {
  if (hydrated || typeof window === "undefined") return;
  hydrated = true;
  try {
    csrfToken = window.localStorage.getItem(CSRF_STORAGE_KEY) ?? readCookie("bizgrow_csrf");
  } catch {
    /* storage disabled (private mode): fall through, login will set it anyway */
  }
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const entry = document.cookie
    .split("; ")
    .find((part) => part.startsWith(`${name}=`));
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null;
}

export function getAccessToken(): string | null {
  hydrate();
  return accessToken;
}

export function getCsrfToken(): string | null {
  hydrate();
  return csrfToken ?? readCookie("bizgrow_csrf");
}

/** Adopt a session returned by register/login/refresh. */
export function setSession(session: AuthSession): void {
  accessToken = session.accessToken;
  csrfToken = session.csrfToken;
  try {
    window.localStorage.setItem(CSRF_STORAGE_KEY, session.csrfToken);
  } catch {
    /* non-fatal: in-memory copy still works until reload */
  }
}

export function clearSession(): void {
  accessToken = null;
  csrfToken = null;
  try {
    window.localStorage.removeItem(CSRF_STORAGE_KEY);
  } catch {
    /* ignore */
  }
}

/* ------------------------- session end events ------------------------- */

type SessionEvent = "ended";
const listeners: Record<SessionEvent, Set<() => void>> = { ended: new Set() };

/** Subscribe to "the server rejected our session" (used by AuthProvider). */
export function onSessionEvent(event: SessionEvent, cb: () => void): () => void {
  listeners[event].add(cb);
  return () => listeners[event].delete(cb);
}

function emit(event: SessionEvent): void {
  for (const cb of listeners[event]) cb();
}

/* ------------------------------ refresh ------------------------------ */

let refreshInFlight: Promise<AuthSession | null> | null = null;

async function withCrossTabLock<T>(fn: () => Promise<T>): Promise<T> {
  // Web Locks serializes refresh across tabs of this origin; without it two
  // tabs can present the same refresh cookie simultaneously and the reuse
  // detector revokes the whole family (both logged out).
  if (typeof navigator !== "undefined" && navigator.locks?.request) {
    return navigator.locks.request("bizgrow.session-refresh", fn) as Promise<T>;
  }
  return fn();
}

async function doRefresh(): Promise<AuthSession | null> {
  const csrf = getCsrfToken();
  if (!csrf) {
    // No CSRF value anywhere → no recoverable session (first visit or logout).
    clearSession();
    emit("ended");
    return null;
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      credentials: "include",
      headers: { "X-CSRF-TOKEN": csrf, Accept: "application/json" },
    });
  } catch {
    return null; // network failure: keep state, caller retries later
  }
  if (!response.ok) {
    // 401 (expired/revoked/reuse) or 403 (CSRF mismatch) → session is gone.
    clearSession();
    emit("ended");
    return null;
  }
  const session = (await response.json()) as AuthSession;
  setSession(session);
  return session;
}

/**
 * Rotate the refresh cookie and obtain a new access token.
 * Single-flight per tab; cross-tab serialized via Web Locks.
 */
export function refreshSession(): Promise<AuthSession | null> {
  if (!refreshInFlight) {
    refreshInFlight = withCrossTabLock(doRefresh).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/* ------------------------------ requests ------------------------------ */

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  /** JSON body; omitted when undefined. */
  body?: unknown;
  /** Attach the bearer token and refresh on 401 (default true). */
  auth?: boolean;
}

async function toApiError(response: Response): Promise<ApiError> {
  const text = await response.text().catch(() => "");
  let body: ErrorBody | null = null;
  try {
    body = text ? (JSON.parse(text) as ErrorBody) : null;
  } catch {
    body = null;
  }
  return new ApiError(
    response.status,
    body?.error ?? "ERROR",
    body?.message ?? defaultMessageFor(response.status),
    body?.fieldErrors,
    body?.traceId,
  );
}

function defaultMessageFor(status: number): string {
  if (status === 401) return "Your session has ended. Please sign in again.";
  if (status === 403) return "You do not have permission to do that.";
  if (status === 404) return "Not found.";
  if (status === 409) return "That conflicts with existing data.";
  if (status === 429) return "Too many requests. Please slow down.";
  return "Request failed. Please try again.";
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true } = options;

  const send = (): Promise<Response> => {
    const headers = new Headers({ Accept: "application/json" });
    if (body !== undefined) headers.set("Content-Type", "application/json");
    const token = getAccessToken();
    if (auth && token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${API_BASE}${path}`, {
      method,
      headers,
      credentials: "include",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  };

  let response = await send();

  if (response.status === 401 && auth) {
    const session = await refreshSession();
    if (session) {
      response = await send(); // one retry with the rotated token
    } else {
      emit("ended");
    }
  }

  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: "PATCH", body }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  /** Credential endpoints: no bearer, no 401-retry (a 401 here is a real error). */
  public: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "POST", body, auth: false }),
};

/** Best-effort server logout; local state is always cleared. */
export async function logoutRequest(): Promise<void> {
  const csrf = getCsrfToken();
  try {
    await fetch(`${API_BASE}/auth/logout`, {
      method: "POST",
      credentials: "include",
      headers: csrf ? { "X-CSRF-TOKEN": csrf } : {},
    });
  } catch {
    /* offline logout is fine — cookie expiry will end the session */
  } finally {
    clearSession();
  }
}
