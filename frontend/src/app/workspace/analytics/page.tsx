"use client";

import { useState } from "react";
import { Badge, Button, Card, ErrorCallout, Spinner } from "@/components/ui";
import {
  useAnalyticsOverview,
  useDisconnectGoogle,
  useGoogleConnectUrl,
  useGoogleConnectionStatus,
  useSyncGoogleAnalytics,
  useWebsites,
} from "@/lib/api/hooks";
import { useAuth } from "@/lib/auth/auth-context";

export default function AnalyticsPage() {
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? "";

  const [selectedWebsiteId, setSelectedWebsiteId] = useState<string>("");
  const [dateRangeDays, setDateRangeDays] = useState<number>(30);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"overview" | "queries" | "pages">("overview");

  const websitesQuery = useWebsites(orgId, 0);
  const websites = websitesQuery.data?.content ?? [];

  // Active website
  const activeWebsiteId = selectedWebsiteId || (websites.length > 0 ? websites[0]?.id : undefined);

  // Compute date strings
  const today = new Date();
  const endDate = today.toISOString().split("T")[0];
  const startDateObj = new Date();
  startDateObj.setDate(today.getDate() - dateRangeDays);
  const startDate = startDateObj.toISOString().split("T")[0];

  const statusQuery = useGoogleConnectionStatus(orgId, activeWebsiteId);
  const overviewQuery = useAnalyticsOverview(orgId, activeWebsiteId, startDate, endDate);

  const connectMutation = useGoogleConnectUrl(orgId, activeWebsiteId);
  const disconnectMutation = useDisconnectGoogle(orgId);
  const syncMutation = useSyncGoogleAnalytics(orgId);

  const isConnected = statusQuery.data?.connected ?? false;
  const overview = overviewQuery.data;

  const handleConnect = async () => {
    if (!activeWebsiteId) return;
    setConnectError(null);
    try {
      const res = await connectMutation.mutateAsync();
      if (res.authUrl) {
        if (typeof window !== "undefined") {
          sessionStorage.setItem("google_oauth_website_id", activeWebsiteId);
          sessionStorage.setItem("google_oauth_org_id", orgId);
          window.location.href = res.authUrl;
        }
      } else {
        setConnectError("No Google authorization URL returned by server.");
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to initiate Google OAuth. Please ensure GOOGLE_CLIENT_ID is configured.";
      setConnectError(message);
    }
  };

  const handleDisconnect = async () => {
    if (!activeWebsiteId || !confirm("Disconnect Google Analytics and Search Console for this website?")) return;
    await disconnectMutation.mutateAsync(activeWebsiteId);
  };

  const handleSync = async () => {
    if (!activeWebsiteId) return;
    await syncMutation.mutateAsync(activeWebsiteId);
  };

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Analytics & Traffic Performance
          </h1>
          <p className="text-sm text-slate-500">
            Verified website metrics from Google Analytics 4 and Google Search Console
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Website Picker */}
          {websites.length > 0 && (
            <select
              value={activeWebsiteId || ""}
              onChange={(e) => setSelectedWebsiteId(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
            >
              {websites.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name} ({w.domain})
                </option>
              ))}
            </select>
          )}

          {/* Date Range Picker */}
          <select
            value={dateRangeDays}
            onChange={(e) => setDateRangeDays(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
          >
            <option value={7}>Last 7 Days</option>
            <option value={30}>Last 30 Days</option>
            <option value={90}>Last 90 Days</option>
          </select>
        </div>
      </div>

      {connectError && (
        <ErrorCallout message={connectError} />
      )}

      {/* Integration Banner / Card */}
      <Card className="border-teal-100 bg-gradient-to-r from-teal-50/50 via-white to-slate-50">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-4">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-teal-600 text-white shadow-sm font-bold text-lg">
              G
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-semibold text-slate-900">Google Services Integration</h3>
                {statusQuery.isLoading ? (
                  <Spinner className="size-4" />
                ) : isConnected ? (
                  <Badge status="ACTIVE" />
                ) : (
                  <Badge status="INACTIVE" />
                )}
              </div>
              <p className="text-xs text-slate-500 mt-0.5">
                {isConnected
                  ? `Connected as ${statusQuery.data?.googleAccountId || "Authorized Google Account"} — Real-time synchronization active`
                  : "Connect your Google Analytics 4 and Search Console accounts to import verified organic traffic and query rankings."}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {isConnected ? (
              <>
                <Button
                  variant="secondary"
                  onClick={handleSync}
                  loading={syncMutation.isPending}
                >
                  Sync Now
                </Button>
                <Button
                  variant="danger"
                  onClick={handleDisconnect}
                  loading={disconnectMutation.isPending}
                >
                  Disconnect
                </Button>
              </>
            ) : (
              <Button
                variant="primary"
                onClick={handleConnect}
                loading={connectMutation.isPending}
              >
                Connect Google Account
              </Button>
            )}
          </div>
        </div>
      </Card>

      {/* Overview Metrics Cards */}
      {overviewQuery.isLoading ? (
        <div className="flex justify-center py-12">
          <Spinner className="size-8 text-teal-600" />
        </div>
      ) : overviewQuery.isError ? (
        <ErrorCallout message="Failed to load analytics data. Please try again." />
      ) : overview ? (
        <div className="space-y-6">
          {/* Top KPI Grid */}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Card>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Total Sessions
              </div>
              <div className="mt-2 text-2xl font-bold text-slate-900">
                {overview.totalSessions.toLocaleString()}
              </div>
              <div className="mt-1 text-xs text-teal-600 font-medium">
                {overview.totalUsers.toLocaleString()} unique users
              </div>
            </Card>

            <Card>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Page Views
              </div>
              <div className="mt-2 text-2xl font-bold text-slate-900">
                {overview.totalPageViews.toLocaleString()}
              </div>
              <div className="mt-1 text-xs text-slate-500">
                Bounce rate: {overview.avgBounceRate}%
              </div>
            </Card>

            <Card>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Google Search Clicks
              </div>
              <div className="mt-2 text-2xl font-bold text-teal-700">
                {overview.totalClicks.toLocaleString()}
              </div>
              <div className="mt-1 text-xs text-slate-500">
                {(overview.avgCtr * 100).toFixed(1)}% organic CTR
              </div>
            </Card>

            <Card>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Search Impressions
              </div>
              <div className="mt-2 text-2xl font-bold text-slate-900">
                {overview.totalImpressions.toLocaleString()}
              </div>
              <div className="mt-1 text-xs text-slate-500">
                Avg. position: #{overview.avgPosition.toFixed(1)}
              </div>
            </Card>
          </div>

          {/* Daily Trend Chart (SVG Sparkline / Trend visualization) */}
          <Card>
            <div className="flex items-center justify-between border-b border-slate-100 pb-4">
              <div>
                <h3 className="text-base font-semibold text-slate-900">Traffic & Search Trend</h3>
                <p className="text-xs text-slate-500">Daily sessions and organic search clicks over the selected timeframe</p>
              </div>
              <div className="flex items-center gap-4 text-xs font-medium">
                <span className="flex items-center gap-1.5">
                  <span className="size-2.5 rounded-full bg-teal-600" /> Sessions
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="size-2.5 rounded-full bg-indigo-500" /> Search Clicks
                </span>
              </div>
            </div>

            {overview.dailyMetrics.length === 0 ? (
              <div className="py-12 text-center text-sm text-slate-500">
                No daily timeline points recorded for this period.
              </div>
            ) : (
              <div className="mt-6">
                {/* SVG Visualizer */}
                <div className="h-48 w-full">
                  <svg className="size-full overflow-visible" preserveAspectRatio="none" viewBox="0 0 1000 200">
                    {/* Grid lines */}
                    <line x1="0" y1="50" x2="1000" y2="50" stroke="#f1f5f9" strokeWidth="1" />
                    <line x1="0" y1="100" x2="1000" y2="100" stroke="#f1f5f9" strokeWidth="1" />
                    <line x1="0" y1="150" x2="1000" y2="150" stroke="#f1f5f9" strokeWidth="1" />

                    {/* Bars / Points */}
                    {overview.dailyMetrics.map((point, index) => {
                      const totalPoints = overview.dailyMetrics.length;
                      const x = (index / Math.max(totalPoints - 1, 1)) * 960 + 20;
                      const maxSessions = Math.max(...overview.dailyMetrics.map((p) => p.sessions), 10);
                      const maxClicks = Math.max(...overview.dailyMetrics.map((p) => p.clicks), 10);

                      const sessionHeight = (point.sessions / maxSessions) * 130;
                      const clickHeight = (point.clicks / maxClicks) * 110;

                      return (
                        <g key={point.date} className="group">
                          {/* Session bar */}
                          <rect
                            x={x - 6}
                            y={180 - sessionHeight}
                            width="5"
                            height={sessionHeight}
                            className="fill-teal-500 opacity-80 hover:opacity-100 transition-opacity"
                            rx="2"
                          >
                            <title>{`${point.date}: ${point.sessions} sessions`}</title>
                          </rect>
                          {/* Click bar */}
                          <rect
                            x={x}
                            y={180 - clickHeight}
                            width="5"
                            height={clickHeight}
                            className="fill-indigo-500 opacity-80 hover:opacity-100 transition-opacity"
                            rx="2"
                          >
                            <title>{`${point.date}: ${point.clicks} clicks`}</title>
                          </rect>
                        </g>
                      );
                    })}
                  </svg>
                </div>
                {/* Dates footer */}
                <div className="flex justify-between text-[11px] text-slate-400 mt-2 px-2">
                  <span>{overview.dailyMetrics[0]?.date}</span>
                  <span>{overview.dailyMetrics[Math.floor(overview.dailyMetrics.length / 2)]?.date}</span>
                  <span>{overview.dailyMetrics[overview.dailyMetrics.length - 1]?.date}</span>
                </div>
              </div>
            )}
          </Card>

          {/* Tabs for In-depth tables */}
          <div className="border-b border-slate-200">
            <nav className="flex gap-4">
              <button
                onClick={() => setActiveTab("overview")}
                className={`border-b-2 pb-2 text-sm font-medium transition-colors ${
                  activeTab === "overview"
                    ? "border-teal-600 text-teal-700"
                    : "border-transparent text-slate-500 hover:text-slate-800"
                }`}
              >
                Channels & Devices
              </button>
              <button
                onClick={() => setActiveTab("queries")}
                className={`border-b-2 pb-2 text-sm font-medium transition-colors ${
                  activeTab === "queries"
                    ? "border-teal-600 text-teal-700"
                    : "border-transparent text-slate-500 hover:text-slate-800"
                }`}
              >
                Top Search Queries ({overview.topQueries.length})
              </button>
              <button
                onClick={() => setActiveTab("pages")}
                className={`border-b-2 pb-2 text-sm font-medium transition-colors ${
                  activeTab === "pages"
                    ? "border-teal-600 text-teal-700"
                    : "border-transparent text-slate-500 hover:text-slate-800"
                }`}
              >
                Top Visited Pages ({overview.topPages.length})
              </button>
            </nav>
          </div>

          {/* Tab 1: Channels & Devices */}
          {activeTab === "overview" && (
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
              <Card>
                <h4 className="font-semibold text-slate-900 text-sm mb-3">Traffic Acquisition Channels</h4>
                {overview.channels.length === 0 ? (
                  <p className="text-xs text-slate-500">No channel data available.</p>
                ) : (
                  <div className="space-y-3">
                    {overview.channels.map((ch) => {
                      const totalChan = overview.channels.reduce((sum, c) => sum + c.sessions, 0) || 1;
                      const pct = Math.round((ch.sessions / totalChan) * 100);
                      return (
                        <div key={ch.channel}>
                          <div className="flex justify-between text-xs font-medium text-slate-700 mb-1">
                            <span>{ch.channel}</span>
                            <span>{ch.sessions.toLocaleString()} sessions ({pct}%)</span>
                          </div>
                          <div className="h-2 w-full rounded-full bg-slate-100 overflow-hidden">
                            <div className="h-full bg-teal-600 rounded-full" style={{ width: `${pct}%` }} />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </Card>

              <Card>
                <h4 className="font-semibold text-slate-900 text-sm mb-3">Device Category Breakdown</h4>
                {overview.devices.length === 0 ? (
                  <p className="text-xs text-slate-500">No device data available.</p>
                ) : (
                  <div className="space-y-3">
                    {overview.devices.map((dev) => {
                      const totalDev = overview.devices.reduce((sum, d) => sum + d.sessions, 0) || 1;
                      const pct = Math.round((dev.sessions / totalDev) * 100);
                      return (
                        <div key={dev.device}>
                          <div className="flex justify-between text-xs font-medium text-slate-700 mb-1">
                            <span>{dev.device}</span>
                            <span>{dev.sessions.toLocaleString()} sessions ({pct}%)</span>
                          </div>
                          <div className="h-2 w-full rounded-full bg-slate-100 overflow-hidden">
                            <div className="h-full bg-indigo-600 rounded-full" style={{ width: `${pct}%` }} />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </Card>
            </div>
          )}

          {/* Tab 2: Search Queries */}
          {activeTab === "queries" && (
            <Card>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
                    <tr>
                      <th className="py-2">Search Query</th>
                      <th className="py-2 text-right">Clicks</th>
                      <th className="py-2 text-right">Impressions</th>
                      <th className="py-2 text-right">Avg Position</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {overview.topQueries.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="py-6 text-center text-xs text-slate-500">
                          No query rankings found for this timeframe.
                        </td>
                      </tr>
                    ) : (
                      overview.topQueries.map((q, idx) => (
                        <tr key={idx} className="hover:bg-slate-50">
                          <td className="py-2.5 font-medium text-slate-800">{q.query}</td>
                          <td className="py-2.5 text-right font-semibold text-teal-700">{q.clicks}</td>
                          <td className="py-2.5 text-right text-slate-600">{q.impressions.toLocaleString()}</td>
                          <td className="py-2.5 text-right text-slate-600">
                            #{typeof q.position === "number" ? q.position.toFixed(1) : "-"}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </Card>
          )}

          {/* Tab 3: Visited Pages */}
          {activeTab === "pages" && (
            <Card>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
                    <tr>
                      <th className="py-2">Page URL</th>
                      <th className="py-2 text-right">Page Views</th>
                      <th className="py-2 text-right">Avg Duration</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {overview.topPages.length === 0 ? (
                      <tr>
                        <td colSpan={3} className="py-6 text-center text-xs text-slate-500">
                          No page views recorded for this timeframe.
                        </td>
                      </tr>
                    ) : (
                      overview.topPages.map((p, idx) => (
                        <tr key={idx} className="hover:bg-slate-50">
                          <td className="py-2.5 font-medium text-slate-800">{p.page}</td>
                          <td className="py-2.5 text-right font-semibold text-slate-800">{p.views?.toLocaleString() ?? 0}</td>
                          <td className="py-2.5 text-right text-slate-600">{p.avgTime ? `${p.avgTime}s` : "-"}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </Card>
          )}
        </div>
      ) : null}
    </div>
  );
}
