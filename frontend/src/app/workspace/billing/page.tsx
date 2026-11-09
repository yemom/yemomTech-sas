"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import {
  usePlans,
  useSubscription,
  useInitializePayment,
  useVerifyPayment,
  useCancelSubscription,
  usePaymentTransactions,
  useBillingHistory,
} from "@/lib/api/hooks";
import type { SubscriptionPlan, PaymentTransaction } from "@/lib/api/types";
import { Badge, Button, EmptyState, ErrorCallout } from "@/components/ui";

function formatEtb(value?: number): string {
  if (value === undefined || value === null) return "ETB 0.00";
  return `ETB ${Number(value).toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return "N/A";
  try {
    return new Date(dateStr).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return dateStr;
  }
}

export default function BillingPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-slate-500">Loading billing details...</div>}>
      <BillingContent />
    </Suspense>
  );
}

function BillingContent() {
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? "";
  const searchParams = useSearchParams();
  const txRefFromUrl = searchParams.get("tx_ref");

  const [txRefToVerify, setTxRefToVerify] = useState<string | null>(txRefFromUrl);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [initiatingSlug, setInitiatingSlug] = useState<string | null>(null);
  const [showCancelModal, setShowCancelModal] = useState(false);

  // Queries & Mutations
  const { data: plans, isLoading: loadingPlans } = usePlans();
  const { data: subscription, isLoading: loadingSub, refetch: refetchSub } = useSubscription(orgId);
  const { data: verifyResult, isLoading: verifying } = useVerifyPayment(orgId, txRefToVerify ?? "");
  const { data: transactionsPage, isLoading: loadingTx } = usePaymentTransactions(orgId);
  const { data: historyPage, isLoading: loadingHistory } = useBillingHistory(orgId);

  const initPaymentMutation = useInitializePayment(orgId);
  const cancelSubscriptionMutation = useCancelSubscription(orgId);

  // When verifyResult arrives and is SUCCESS, refresh subscription
  useEffect(() => {
    if (verifyResult?.status === "SUCCESS") {
      void refetchSub();
    }
  }, [verifyResult, refetchSub]);

  const handleSelectPlan = async (plan: SubscriptionPlan) => {
    setCheckoutError(null);
    setInitiatingSlug(plan.slug);

    try {
      const returnUrl = typeof window !== "undefined"
        ? `${window.location.origin}/workspace/billing`
        : "/workspace/billing";

      const res = await initPaymentMutation.mutateAsync({
        planSlug: plan.slug,
        returnUrl,
      });

      if (res.checkoutUrl) {
        if (res.checkoutUrl.startsWith("http")) {
          window.location.href = res.checkoutUrl;
        } else {
          // Internal redirect for free plan
          window.location.href = res.checkoutUrl;
        }
      }
    } catch (err: unknown) {
      setCheckoutError((err as Error).message ?? "Failed to initialize payment.");
      setInitiatingSlug(null);
    }
  };

  const handleConfirmCancel = async () => {
    try {
      await cancelSubscriptionMutation.mutateAsync();
      setShowCancelModal(false);
      void refetchSub();
    } catch (err: unknown) {
      alert((err as Error).message ?? "Failed to cancel subscription.");
    }
  };

  const currentPlanSlug = subscription?.plan?.slug ?? "free";
  const transactions: PaymentTransaction[] = transactionsPage?.content ?? [];
  const history = historyPage?.content ?? [];

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          Subscription & Billing
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          Manage your subscription plan, local Ethiopian payment methods (Chapa, Telebirr, CBE Birr), and invoices.
        </p>
      </div>

      {/* Payment Verification Banner */}
      {txRefToVerify && (
        <div
          className={`rounded-xl border p-4 shadow-sm ${
            verifying
              ? "border-blue-200 bg-blue-50 text-blue-900"
              : verifyResult?.status === "SUCCESS"
              ? "border-emerald-200 bg-emerald-50 text-emerald-900"
              : "border-rose-200 bg-rose-50 text-rose-900"
          }`}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              {verifying && (
                <div className="h-5 w-5 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
              )}
              {!verifying && verifyResult?.status === "SUCCESS" && (
                <svg className="h-5 w-5 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              )}
              {!verifying && verifyResult?.status !== "SUCCESS" && (
                <svg className="h-5 w-5 text-rose-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              )}
              <div>
                <p className="font-semibold">
                  {verifying
                    ? "Verifying payment with Chapa..."
                    : verifyResult?.status === "SUCCESS"
                    ? "Payment Verified Successfully!"
                    : "Payment Verification Incomplete or Failed"}
                </p>
                <p className="text-xs opacity-90">
                  {verifying
                    ? `Reference: ${txRefToVerify}`
                    : verifyResult?.message ?? `Reference: ${txRefToVerify}`}
                </p>
              </div>
            </div>
            <button
              onClick={() => setTxRefToVerify(null)}
              className="text-xs font-medium underline opacity-80 hover:opacity-100"
            >
              Dismiss
            </button>
          </div>
        </div>
      )}

      {checkoutError && (
        <ErrorCallout message={checkoutError} />
      )}

      {/* Active Subscription Card */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                Current Subscription
              </span>
              <Badge status={subscription?.status ?? "ACTIVE"}>
                {subscription?.status ?? "ACTIVE"}
              </Badge>
            </div>
            <h2 className="mt-1 text-2xl font-bold text-slate-900">
              {subscription?.plan?.name ?? "Free"} Plan
            </h2>
            <p className="text-sm text-slate-600">
              {formatEtb(subscription?.plan?.priceEtb ?? 0)} / {subscription?.plan?.billingInterval.toLowerCase()}
              {subscription?.currentPeriodEnd && (
                <span className="ml-2 text-slate-400">
                  • {subscription.cancelAtPeriodEnd ? "Expires on " : "Renews on "}
                  {formatDate(subscription.currentPeriodEnd)}
                </span>
              )}
            </p>
          </div>

          <div className="flex items-center gap-2">
            {subscription?.status === "ACTIVE" && currentPlanSlug !== "free" && !subscription.cancelAtPeriodEnd && (
              <Button
                variant="secondary"
                onClick={() => setShowCancelModal(true)}
              >
                Cancel Subscription
              </Button>
            )}
            {subscription?.cancelAtPeriodEnd && (
              <span className="text-xs font-medium text-amber-600">
                Scheduled for cancellation at period end
              </span>
            )}
          </div>
        </div>

        {/* Plan Limits Grid */}
        <div className="mt-6 grid grid-cols-2 gap-4 border-t border-slate-100 pt-6 sm:grid-cols-3 lg:grid-cols-6">
          <div className="rounded-lg bg-slate-50 p-3 text-center">
            <span className="text-xs text-slate-500">Websites</span>
            <p className="mt-1 font-bold text-slate-900">{subscription?.plan?.maxWebsites ?? 1}</p>
          </div>
          <div className="rounded-lg bg-slate-50 p-3 text-center">
            <span className="text-xs text-slate-500">Team Members</span>
            <p className="mt-1 font-bold text-slate-900">{subscription?.plan?.maxMembers ?? 3}</p>
          </div>
          <div className="rounded-lg bg-slate-50 p-3 text-center">
            <span className="text-xs text-slate-500">Leads Capacity</span>
            <p className="mt-1 font-bold text-slate-900">{(subscription?.plan?.maxLeads ?? 200).toLocaleString()}</p>
          </div>
          <div className="rounded-lg bg-slate-50 p-3 text-center">
            <span className="text-xs text-slate-500">Marketing Campaigns</span>
            <p className="mt-1 font-bold text-slate-900">{subscription?.plan?.maxCampaigns ?? 1}</p>
          </div>
          <div className="rounded-lg bg-slate-50 p-3 text-center">
            <span className="text-xs text-slate-500">AI Growth Insights</span>
            <p className="mt-1 font-bold text-slate-900">
              {subscription?.plan?.hasAiInsights ? "✓ Included" : "— Locked"}
            </p>
          </div>
          <div className="rounded-lg bg-slate-50 p-3 text-center">
            <span className="text-xs text-slate-500">Agency Workspace</span>
            <p className="mt-1 font-bold text-slate-900">
              {subscription?.plan?.hasAgencyWorkspace ? "✓ Included" : "— Locked"}
            </p>
          </div>
        </div>
      </div>

      {/* Plan Selection Cards */}
      <div>
        <div className="mb-4">
          <h2 className="text-lg font-bold text-slate-900">Available Plans</h2>
          <p className="text-sm text-slate-500">
            Scale seamlessly with verified Ethiopian payment options: Telebirr, CBE Birr, and Visa/Mastercard via Chapa.
          </p>
        </div>

        {loadingPlans ? (
          <div className="p-8 text-center text-slate-400">Loading plan options...</div>
        ) : (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
            {plans?.map((plan) => {
              const isCurrent = plan.slug === currentPlanSlug;
              const isPopular = plan.slug === "growth";
              const isInitiating = initiatingSlug === plan.slug;

              return (
                <div
                  key={plan.id}
                  className={`relative flex flex-col justify-between rounded-2xl border p-6 transition-all ${
                    isCurrent
                      ? "border-teal-500 bg-teal-50/20 shadow-md ring-2 ring-teal-500"
                      : isPopular
                      ? "border-indigo-400 bg-white shadow-md ring-1 ring-indigo-400"
                      : "border-slate-200 bg-white shadow-sm hover:border-slate-300"
                  }`}
                >
                  {isPopular && (
                    <div className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-indigo-600 px-3 py-0.5 text-xs font-semibold text-white shadow-sm">
                      Most Popular
                    </div>
                  )}

                  <div>
                    <h3 className="text-lg font-bold text-slate-900">{plan.name}</h3>
                    <p className="mt-1 min-h-[40px] text-xs text-slate-500">{plan.description}</p>

                    <div className="my-4">
                      <span className="text-3xl font-extrabold text-slate-900">
                        {formatEtb(plan.priceEtb)}
                      </span>
                      <span className="text-xs text-slate-500"> / month</span>
                    </div>

                    <ul className="space-y-2 border-t border-slate-100 pt-4 text-xs text-slate-600">
                      <li className="flex items-center gap-2">
                        <span className="text-emerald-600 font-bold">✓</span>
                        {plan.maxWebsites} {plan.maxWebsites === 1 ? "Website" : "Websites"}
                      </li>
                      <li className="flex items-center gap-2">
                        <span className="text-emerald-600 font-bold">✓</span>
                        Up to {plan.maxMembers} Team Members
                      </li>
                      <li className="flex items-center gap-2">
                        <span className="text-emerald-600 font-bold">✓</span>
                        {plan.maxLeads.toLocaleString()} Leads Capacity
                      </li>
                      <li className="flex items-center gap-2">
                        <span className="text-emerald-600 font-bold">✓</span>
                        {plan.maxCampaigns} Active Campaigns
                      </li>
                      <li className="flex items-center gap-2">
                        <span className={plan.hasAiInsights ? "text-emerald-600 font-bold" : "text-slate-300 font-bold"}>
                          {plan.hasAiInsights ? "✓" : "—"}
                        </span>
                        AI Recommendations & Insights
                      </li>
                      <li className="flex items-center gap-2">
                        <span className={plan.hasAgencyWorkspace ? "text-emerald-600 font-bold" : "text-slate-300 font-bold"}>
                          {plan.hasAgencyWorkspace ? "✓" : "—"}
                        </span>
                        Agency Multi-Client Workspace
                      </li>
                    </ul>
                  </div>

                  <div className="mt-6 pt-4 border-t border-slate-100">
                    {isCurrent ? (
                      <Button disabled className="w-full">
                        Current Plan
                      </Button>
                    ) : (
                      <Button
                        onClick={() => handleSelectPlan(plan)}
                        disabled={isInitiating}
                        className="w-full"
                        variant={isPopular ? "primary" : "secondary"}
                      >
                        {isInitiating
                          ? "Connecting to Chapa..."
                          : plan.priceEtb === 0
                          ? "Switch to Free"
                          : `Upgrade with Chapa`}
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Payment Transactions Table */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-bold text-slate-900">Payment Transactions</h2>
        <p className="text-sm text-slate-500">Recent payment invoices processed via Chapa payment gateway.</p>

        {loadingTx ? (
          <div className="py-6 text-center text-slate-400">Loading transactions...</div>
        ) : transactions.length === 0 ? (
          <div className="py-8">
            <EmptyState
              title="No transactions yet"
              description="Your payment invoices and receipts will appear here after your first upgrade."
            />
          </div>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-100 text-xs uppercase text-slate-400">
                <tr>
                  <th className="py-3 px-2">Date</th>
                  <th className="py-3 px-2">Plan</th>
                  <th className="py-3 px-2">Transaction Ref</th>
                  <th className="py-3 px-2">Amount</th>
                  <th className="py-3 px-2">Provider</th>
                  <th className="py-3 px-2">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {transactions.map((tx) => (
                  <tr key={tx.id} className="hover:bg-slate-50/50">
                    <td className="py-3 px-2 text-slate-600">{formatDate(tx.createdAt)}</td>
                    <td className="py-3 px-2 font-medium text-slate-900">{tx.planName}</td>
                    <td className="py-3 px-2 font-mono text-xs text-slate-500">{tx.txRef}</td>
                    <td className="py-3 px-2 font-semibold text-slate-900">{formatEtb(tx.amountEtb)}</td>
                    <td className="py-3 px-2 text-xs text-slate-600">{tx.provider}</td>
                    <td className="py-3 px-2">
                      <Badge status={tx.status}>{tx.status}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Billing Event History Trail */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-bold text-slate-900">Billing History Log</h2>
        <p className="text-sm text-slate-500">Immutable audit log of all subscription and payment events.</p>

        {loadingHistory ? (
          <div className="py-6 text-center text-slate-400">Loading history...</div>
        ) : history.length === 0 ? (
          <p className="mt-4 text-xs text-slate-400">No billing events recorded.</p>
        ) : (
          <div className="mt-4 space-y-3">
            {history.map((event) => (
              <div
                key={event.id}
                className="flex items-center justify-between rounded-lg border border-slate-100 p-3 text-xs"
              >
                <div>
                  <span className="font-semibold text-slate-800">{event.eventType.replace(/_/g, " ")}</span>
                  <p className="text-slate-500">{event.description}</p>
                </div>
                <div className="text-right">
                  {event.amountEtb ? (
                    <span className="font-medium text-slate-900">{formatEtb(event.amountEtb)}</span>
                  ) : (
                    <span className="text-slate-400">—</span>
                  )}
                  <p className="text-[10px] text-slate-400">{formatDate(event.createdAt)}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Cancel Confirmation Modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
            <h3 className="text-lg font-bold text-slate-900">Cancel Subscription?</h3>
            <p className="mt-2 text-sm text-slate-600">
              Your subscription will remain active until the end of your current billing period (
              {formatDate(subscription?.currentPeriodEnd)}). After that, your organization will revert to the Free plan.
            </p>

            <div className="mt-6 flex justify-end gap-3">
              <Button
                variant="secondary"
                onClick={() => setShowCancelModal(false)}
              >
                Keep Subscription
              </Button>
              <Button
                variant="danger"
                onClick={handleConfirmCancel}
              >
                Confirm Cancellation
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
