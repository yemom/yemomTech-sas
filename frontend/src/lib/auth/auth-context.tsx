"use client";

/**
 * Session state for the whole app.
 *
 * Boot sequence: POST /auth/refresh (HttpOnly cookie + CSRF header) → if it
 * succeeds we hold an access token in memory → GET /auth/me for the profile
 * and memberships. The access token never touches persistent storage; a page
 * reload simply repeats this sequence.
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import {
  api,
  errorMessage,
  logoutRequest,
  onSessionEvent,
  refreshSession,
  setSession,
} from "@/lib/api/client";
import type { AuthSession, MeResponse, MembershipBrief, User } from "@/lib/api/types";

type AuthStatus = "loading" | "authenticated" | "anonymous";

interface RegisterPayload {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phoneNumber?: string;
}

interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  memberships: MembershipBrief[];
  /** The organization currently selected in the switcher (null if none). */
  currentOrg: MembershipBrief | null;
  selectOrg: (organizationId: string) => void;
  login: (email: string, password: string, next?: string) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  /** Re-read /auth/me after membership-changing actions (invite accept, …). */
  reloadMe: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const ORG_STORAGE_KEY = "YemomBizGrow.current_org";

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [me, setMe] = useState<MeResponse | null>(null);
  const meRef = useRef<MeResponse | null>(null);
  const [currentOrgId, setCurrentOrgId] = useState<string | null>(null);
  const loadMe = useCallback(async () => {
    const response = await api.get<MeResponse>("/auth/me");
    meRef.current = response;
    setMe(response);
    return response;
  }, []);

  /** Pick the stored org if the user still belongs to it, else the first one. */
  const chooseOrg = useCallback((memberships: MembershipBrief[], preferred: string | null) => {
    const stored = preferred ?? readStoredOrg();
    const match = memberships.find((m) => m.organizationId === stored) ?? memberships[0];
    setCurrentOrgId(match?.organizationId ?? null);
    if (match) writeStoredOrg(match.organizationId);
  }, []);

  // Bootstrap once per mount: refresh → me → authenticated | anonymous.
  // (refreshSession is single-flight, so a strict-mode double-invoke shares
  // one refresh call instead of rotating the cookie twice.)
  useEffect(() => {
    let cancelled = false;

    void (async () => {
      const session = await refreshSession();
      if (!session || cancelled) {
        if (!cancelled) setStatus("anonymous");
        return;
      }
      try {
        const response = await loadMe();
        if (cancelled) return;
        chooseOrg(response.memberships, null);
        setStatus("authenticated");
      } catch {
        if (!cancelled) setStatus("anonymous");
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [loadMe, chooseOrg]);

  // The API layer announces rejected sessions (expired/revoked/CSRF failure).
  useEffect(
    () =>
      onSessionEvent("ended", () => {
        setMe(null);
        meRef.current = null;
        setCurrentOrgId(null);
        setStatus("anonymous");
      }),
    [],
  );

  const login = useCallback(
    async (email: string, password: string, next?: string) => {
      // Register the returned session (access token in memory + CSRF value)
      // BEFORE calling /auth/me — that call needs the bearer token.
      const session = await api.public<AuthSession>("/auth/login", { email, password });
      setSession(session);
      const response = await loadMe();
      chooseOrg(response.memberships, null);
      setStatus("authenticated");
      // Honor the `next` param from RequireAuth — same-app absolute paths only
      // (never an external URL, which would be an open-redirect vector).
      router.replace(next && next.startsWith("/") && !next.startsWith("//") ? next : dashboardPath(response.memberships));
    },
    [loadMe, chooseOrg, router],
  );

  const register = useCallback(
    async (payload: RegisterPayload) => {
      const session = await api.public<AuthSession>("/auth/register", payload);
      setSession(session);
      const response = await loadMe();
      chooseOrg(response.memberships, null);
      setStatus("authenticated");
      router.push("/onboarding");
    },
    [loadMe, chooseOrg, router],
  );

  const logout = useCallback(async () => {
    await logoutRequest();
    setMe(null);
    meRef.current = null;
    setCurrentOrgId(null);
    writeStoredOrg(null);
    setStatus("anonymous");
    router.replace("/login");
  }, [router]);

  const reloadMe = useCallback(async () => {
    const response = await loadMe();
    // Keep the selected org when it still exists; otherwise fall back.
    const stillMember = response.memberships.some(
      (m) => m.organizationId === currentOrgId,
    );
    chooseOrg(response.memberships, stillMember ? currentOrgId : null);
    return undefined;
  }, [loadMe, currentOrgId, chooseOrg]);

  const selectOrg = useCallback(
    (organizationId: string) => {
      const exists = meRef.current?.memberships.some((m) => m.organizationId === organizationId);
      if (!exists) return;
      setCurrentOrgId(organizationId);
      writeStoredOrg(organizationId);
      router.push("/workspace");
    },
    [router],
  );

  const value = useMemo<AuthContextValue>(() => {
    const memberships = me?.memberships ?? [];
    const currentOrg =
      memberships.find((m) => m.organizationId === currentOrgId) ?? null;
    return {
      status,
      user: me?.user ?? null,
      memberships,
      currentOrg,
      selectOrg,
      login,
      register,
      logout,
      reloadMe,
    };
  }, [status, me, currentOrgId, selectOrg, login, register, logout, reloadMe]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Where an authenticated user should land.
 *
 * <p>A user without an organization has nothing to see in the workspace — the
 * workspace layout would bounce them to /onboarding anyway. Deciding it here
 * avoids a pointless round trip through /workspace.</p>
 */
export function dashboardPath(memberships: MembershipBrief[] = []): string {
  return memberships.length > 0 ? "/workspace" : "/onboarding";
}

function readStoredOrg(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(ORG_STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStoredOrg(id: string | null): void {
  if (typeof window === "undefined") return;
  try {
    if (id) window.localStorage.setItem(ORG_STORAGE_KEY, id);
    else window.localStorage.removeItem(ORG_STORAGE_KEY);
  } catch {
    /* ignore */
  }
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside <AuthProvider>");
  return context;
}

/** Throws a readable error when an auth mutation fails. */
export function rethrow(error: unknown): never {
  throw new Error(errorMessage(error));
}
