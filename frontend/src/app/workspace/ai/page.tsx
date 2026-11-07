"use client";

import { useEffect, useState } from "react";
import { Badge, Button, Card, ErrorCallout, Spinner } from "@/components/ui";
import {
  useAiRecommendations,
  useChatbotConfig,
  useGenerateAiRecommendations,
  usePublicChat,
  useUpdateAiRecommendationStatus,
  useUpdateChatbotConfig,
} from "@/lib/api/hooks";
import type { RecommendationCategory, RecommendationStatus } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/auth-context";

export default function AiWorkspacePage() {
  const { currentOrg } = useAuth();
  const orgId = currentOrg?.organizationId ?? "";
  const orgSlug = currentOrg?.slug ?? "";

  const [activeTab, setActiveTab] = useState<"recommendations" | "chatbot" | "test">("recommendations");
  const [statusFilter, setStatusFilter] = useState<RecommendationStatus | "ALL">("ALL");

  // AI recommendations hooks
  const recsQuery = useAiRecommendations(
    orgId,
    statusFilter === "ALL" ? undefined : statusFilter,
    0,
  );
  const generateMutation = useGenerateAiRecommendations(orgId);
  const updateStatusMutation = useUpdateAiRecommendationStatus(orgId);

  // Chatbot config hooks
  const configQuery = useChatbotConfig(orgId);
  const updateConfigMutation = useUpdateChatbotConfig(orgId);

  // Chatbot config form state
  const [botName, setBotName] = useState("");
  const [greetingMessage, setGreetingMessage] = useState("");
  const [promptInstructions, setPromptInstructions] = useState("");
  const [primaryColor, setPrimaryColor] = useState("#0f766e");
  const [isEnabled, setIsEnabled] = useState(true);
  const [faqItems, setFaqItems] = useState<Array<{ question: string; answer: string }>>([]);
  const [newFaqQuestion, setNewFaqQuestion] = useState("");
  const [newFaqAnswer, setNewFaqAnswer] = useState("");
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Chatbot Simulator state
  const [chatSessionId] = useState(() => `sim-${Math.random().toString(36).substring(2, 9)}`);
  const [chatMessages, setChatMessages] = useState<Array<{ role: "USER" | "ASSISTANT"; content: string }>>([]);
  const [chatInput, setChatInput] = useState("");
  const [visitorName, setVisitorName] = useState("");
  const [visitorPhone, setVisitorPhone] = useState("");
  const chatMutation = usePublicChat(orgSlug);

  useEffect(() => {
    if (configQuery.data) {
      setBotName(configQuery.data.botName);
      setGreetingMessage(configQuery.data.greetingMessage);
      setPromptInstructions(configQuery.data.promptInstructions || "");
      setPrimaryColor(configQuery.data.primaryColor);
      setIsEnabled(configQuery.data.isEnabled);
      setFaqItems(configQuery.data.faqItems || []);

      // Seed simulator initial greeting
      setChatMessages([
        { role: "ASSISTANT", content: configQuery.data.greetingMessage },
      ]);
    }
  }, [configQuery.data]);

  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await updateConfigMutation.mutateAsync({
        botName,
        greetingMessage,
        promptInstructions,
        primaryColor,
        faqItems,
        isEnabled,
      });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch {
      // Handled by UI
    }
  };

  const handleAddFaq = () => {
    if (!newFaqQuestion.trim() || !newFaqAnswer.trim()) return;
    setFaqItems([...faqItems, { question: newFaqQuestion.trim(), answer: newFaqAnswer.trim() }]);
    setNewFaqQuestion("");
    setNewFaqAnswer("");
  };

  const handleRemoveFaq = (index: number) => {
    setFaqItems(faqItems.filter((_, i) => i !== index));
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim() || chatMutation.isPending) return;

    const userText = chatInput.trim();
    setChatInput("");
    setChatMessages((prev) => [...prev, { role: "USER", content: userText }]);

    try {
      const res = await chatMutation.mutateAsync({
        sessionId: chatSessionId,
        message: userText,
        visitorName: visitorName || undefined,
        visitorPhone: visitorPhone || undefined,
      });

      setChatMessages((prev) => [
        ...prev,
        { role: "ASSISTANT", content: res.content },
      ]);
    } catch {
      setChatMessages((prev) => [
        ...prev,
        {
          role: "ASSISTANT",
          content: "Sorry, I encountered an issue processing your request. Please try again or contact us directly.",
        },
      ]);
    }
  };

  const recommendations = recsQuery.data?.content ?? [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            AI Business Insights & Chatbot
          </h1>
          <p className="text-sm text-slate-500">
            Actionable growth recommendations based on your real business data and intelligent website visitor chatbot
          </p>
        </div>

        {activeTab === "recommendations" && (
          <Button
            variant="primary"
            onClick={() => generateMutation.mutate()}
            loading={generateMutation.isPending}
          >
            Generate Fresh Insights
          </Button>
        )}
      </div>

      {/* Primary Tabs */}
      <div className="border-b border-slate-200">
        <nav className="flex gap-6">
          <button
            onClick={() => setActiveTab("recommendations")}
            className={`border-b-2 pb-3 text-sm font-medium transition-colors ${
              activeTab === "recommendations"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            Growth Recommendations ({recommendations.length})
          </button>
          <button
            onClick={() => setActiveTab("chatbot")}
            className={`border-b-2 pb-3 text-sm font-medium transition-colors ${
              activeTab === "chatbot"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            Chatbot Configuration & FAQ
          </button>
          <button
            onClick={() => setActiveTab("test")}
            className={`border-b-2 pb-3 text-sm font-medium transition-colors ${
              activeTab === "test"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            Live Simulator & Embed Code
          </button>
        </nav>
      </div>

      {/* Tab 1: AI Recommendations */}
      {activeTab === "recommendations" && (
        <div className="space-y-4">
          {/* Status filters */}
          <div className="flex items-center gap-2">
            {(["ALL", "OPEN", "APPLIED", "DISMISSED"] as const).map((s) => (
              <button
                key={s}
                onClick={() => setStatusFilter(s)}
                className={`rounded-full px-3 py-1 text-xs font-semibold transition-colors ${
                  statusFilter === s
                    ? "bg-teal-700 text-white"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                }`}
              >
                {s}
              </button>
            ))}
          </div>

          {recsQuery.isLoading ? (
            <div className="flex justify-center py-12">
              <Spinner className="size-8 text-teal-600" />
            </div>
          ) : recsQuery.isError ? (
            <ErrorCallout message="Failed to load recommendations. Please try again." />
          ) : recommendations.length === 0 ? (
            <Card className="text-center py-12">
              <p className="text-slate-600 font-medium">No recommendations found for this filter.</p>
              <p className="text-xs text-slate-400 mt-1">
                Click &quot;Generate Fresh Insights&quot; to analyze your latest website and CRM data.
              </p>
            </Card>
          ) : (
            <div className="grid grid-cols-1 gap-4">
              {recommendations.map((rec) => (
                <Card key={rec.id} className="relative transition hover:shadow-md">
                  <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
                    <div className="space-y-2 max-w-3xl">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge status={rec.priority} />
                        <span className="rounded-md bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">
                          {rec.category}
                        </span>
                        <Badge status={rec.status} />
                      </div>

                      <h3 className="text-lg font-bold text-slate-900">{rec.title}</h3>
                      <p className="text-sm text-slate-700">{rec.problem}</p>

                      {rec.evidence && (
                        <div className="rounded-lg bg-slate-50 p-2.5 text-xs text-slate-600 font-mono">
                          <span className="font-semibold text-slate-800">Evidence: </span>
                          {rec.evidence}
                        </div>
                      )}

                      {rec.expectedBenefit && (
                        <div className="flex items-center gap-1.5 text-xs font-medium text-teal-700 bg-teal-50/70 rounded-md px-2.5 py-1.5 border border-teal-100">
                          <span>Expected Benefit:</span>
                          <span>{rec.expectedBenefit}</span>
                        </div>
                      )}

                      <div className="pt-2">
                        <div className="text-xs font-semibold text-slate-800 uppercase tracking-wider mb-1">
                          Recommended Action:
                        </div>
                        <p className="text-sm text-slate-800 leading-relaxed font-medium bg-amber-50/50 p-3 rounded-lg border border-amber-100">
                          {rec.actionRecommendation}
                        </p>
                      </div>
                    </div>

                    {/* Action buttons */}
                    {rec.status === "OPEN" && (
                      <div className="flex sm:flex-col gap-2 shrink-0">
                        <Button
                          variant="primary"
                          onClick={() =>
                            updateStatusMutation.mutate({
                              recommendationId: rec.id,
                              status: "APPLIED",
                            })
                          }
                          loading={updateStatusMutation.isPending}
                        >
                          Mark Applied
                        </Button>
                        <Button
                          variant="secondary"
                          onClick={() =>
                            updateStatusMutation.mutate({
                              recommendationId: rec.id,
                              status: "DISMISSED",
                            })
                          }
                          loading={updateStatusMutation.isPending}
                        >
                          Dismiss
                        </Button>
                      </div>
                    )}
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Chatbot Configuration */}
      {activeTab === "chatbot" && (
        <form onSubmit={handleSaveConfig} className="space-y-6 max-w-4xl">
          {saveSuccess && (
            <div className="rounded-lg bg-emerald-50 p-3 text-sm font-medium text-emerald-800 border border-emerald-200">
              Chatbot configuration saved successfully!
            </div>
          )}

          <Card className="space-y-4">
            <h3 className="text-base font-bold text-slate-900 border-b border-slate-100 pb-3">
              General Bot Settings
            </h3>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">Bot Name</label>
                <input
                  type="text"
                  value={botName}
                  onChange={(e) => setBotName(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 p-2 text-sm text-slate-800 shadow-sm"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">Theme Color</label>
                <div className="flex items-center gap-2">
                  <input
                    type="color"
                    value={primaryColor}
                    onChange={(e) => setPrimaryColor(e.target.value)}
                    className="size-9 rounded border border-slate-300 cursor-pointer"
                  />
                  <input
                    type="text"
                    value={primaryColor}
                    onChange={(e) => setPrimaryColor(e.target.value)}
                    className="w-full rounded-lg border border-slate-300 p-2 text-sm text-slate-800 font-mono"
                  />
                </div>
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Greeting Message</label>
              <textarea
                value={greetingMessage}
                onChange={(e) => setGreetingMessage(e.target.value)}
                rows={2}
                className="w-full rounded-lg border border-slate-300 p-2 text-sm text-slate-800 shadow-sm"
                required
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Special Prompt Instructions (Persona & Guidelines)
              </label>
              <textarea
                value={promptInstructions}
                onChange={(e) => setPromptInstructions(e.target.value)}
                rows={3}
                placeholder="e.g. Always respond in polite Ethiopian business etiquette. Emphasize fast phone responses."
                className="w-full rounded-lg border border-slate-300 p-2 text-sm text-slate-800 shadow-sm"
              />
            </div>

            <div className="flex items-center gap-3 pt-2">
              <input
                type="checkbox"
                id="botEnabled"
                checked={isEnabled}
                onChange={(e) => setIsEnabled(e.target.checked)}
                className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
              />
              <label htmlFor="botEnabled" className="text-sm font-medium text-slate-800">
                Enable Public Chatbot Widget for Visitors
              </label>
            </div>
          </Card>

          {/* FAQ Knowledge Base */}
          <Card className="space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-base font-bold text-slate-900">Custom Business Knowledge (FAQ)</h3>
                <p className="text-xs text-slate-500">
                  Add answers to frequent questions so the AI responds accurately to your customers.
                </p>
              </div>
            </div>

            {/* List existing FAQs */}
            <div className="space-y-3">
              {faqItems.map((item, idx) => (
                <div key={idx} className="flex items-start justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50/50 p-3">
                  <div className="space-y-1">
                    <p className="text-xs font-bold text-slate-800">Q: {item.question}</p>
                    <p className="text-xs text-slate-600">A: {item.answer}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemoveFaq(idx)}
                    className="text-xs text-rose-600 hover:text-rose-800 font-medium"
                  >
                    Delete
                  </button>
                </div>
              ))}
            </div>

            {/* Add FAQ form */}
            <div className="rounded-lg border border-dashed border-slate-300 p-4 space-y-3 bg-white">
              <h4 className="text-xs font-semibold text-slate-800 uppercase tracking-wider">Add New FAQ Item</h4>
              <div>
                <input
                  type="text"
                  placeholder="Visitor Question (e.g. What are your delivery options in Addis Ababa?)"
                  value={newFaqQuestion}
                  onChange={(e) => setNewFaqQuestion(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 p-2 text-xs text-slate-800 shadow-sm"
                />
              </div>
              <div>
                <textarea
                  placeholder="Accurate Answer for the AI to provide"
                  value={newFaqAnswer}
                  onChange={(e) => setNewFaqAnswer(e.target.value)}
                  rows={2}
                  className="w-full rounded-lg border border-slate-300 p-2 text-xs text-slate-800 shadow-sm"
                />
              </div>
              <Button type="button" variant="secondary" onClick={handleAddFaq}>
                Add to Knowledge Base
              </Button>
            </div>
          </Card>

          <Button type="submit" variant="primary" loading={updateConfigMutation.isPending}>
            Save Chatbot Settings
          </Button>
        </form>
      )}

      {/* Tab 3: Live Simulator & Embed Code */}
      {activeTab === "test" && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {/* Chatbot Simulator */}
          <Card className="flex flex-col h-[520px]">
            {/* Header */}
            <div
              className="flex items-center justify-between rounded-t-lg p-3 text-white"
              style={{ backgroundColor: primaryColor }}
            >
              <div className="flex items-center gap-2">
                <span className="size-2 rounded-full bg-emerald-400" />
                <span className="font-bold text-sm">{botName}</span>
              </div>
              <span className="text-xs opacity-80">BizGrow Assistant</span>
            </div>

            {/* Messages Scroll Area */}
            <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-slate-50">
              {chatMessages.map((msg, i) => (
                <div
                  key={i}
                  className={`flex ${msg.role === "USER" ? "justify-end" : "justify-start"}`}
                >
                  <div
                    className={`max-w-[80%] rounded-2xl px-3.5 py-2 text-xs leading-relaxed shadow-sm ${
                      msg.role === "USER"
                        ? "bg-teal-700 text-white rounded-br-none"
                        : "bg-white text-slate-800 ring-1 ring-slate-200 rounded-bl-none"
                    }`}
                  >
                    {msg.content}
                  </div>
                </div>
              ))}
              {chatMutation.isPending && (
                <div className="flex justify-start">
                  <div className="bg-white px-3 py-2 rounded-2xl rounded-bl-none ring-1 ring-slate-200">
                    <Spinner className="size-3 text-teal-600" />
                  </div>
                </div>
              )}
            </div>

            {/* Input Form */}
            <form onSubmit={handleSendMessage} className="border-t border-slate-200 p-3 bg-white space-y-2">
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="text"
                  placeholder="Visitor Name (optional)"
                  value={visitorName}
                  onChange={(e) => setVisitorName(e.target.value)}
                  className="rounded-md border border-slate-200 px-2 py-1 text-[11px]"
                />
                <input
                  type="text"
                  placeholder="Visitor Phone (optional)"
                  value={visitorPhone}
                  onChange={(e) => setVisitorPhone(e.target.value)}
                  className="rounded-md border border-slate-200 px-2 py-1 text-[11px]"
                />
              </div>
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Type a message to test the chatbot..."
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  className="flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-800 focus:border-teal-500 focus:outline-none"
                />
                <Button type="submit" variant="primary" loading={chatMutation.isPending}>
                  Send
                </Button>
              </div>
            </form>
          </Card>

          {/* Embed Script Snippet */}
          <div className="space-y-4">
            <Card className="space-y-3">
              <h3 className="font-bold text-slate-900 text-sm">Website Embed Snippet</h3>
              <p className="text-xs text-slate-500">
                Paste this script tag right before the closing <code className="text-teal-700">&lt;/body&gt;</code> tag on any HTML, WordPress, or Next.js website to embed your interactive customer assistant.
              </p>

              <pre className="overflow-x-auto rounded-lg bg-slate-900 p-4 text-xs text-teal-300 font-mono leading-relaxed">
{`<script
  src="https://cdn.bizgrow.et/widget.js"
  data-org="${orgSlug}"
  data-color="${primaryColor}"
  data-position="bottom-right"
  async>
</script>`}
              </pre>

              <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800">
                <span className="font-bold">Lead Pipeline Auto-Capture: </span>
                When a visitor provides their phone number or email in the chat widget, BizGrow automatically captures them directly into your CRM pipeline under <span className="font-semibold">Leads & CRM</span>!
              </div>
            </Card>

            <Card className="space-y-2">
              <h4 className="font-bold text-slate-800 text-xs">Public Chat API Endpoint</h4>
              <p className="text-xs text-slate-500">
                You can also integrate this bot into custom mobile applications or Telegram bots via our public API:
              </p>
              <div className="rounded bg-slate-100 p-2 font-mono text-[11px] text-slate-700">
                POST /api/v1/public/chat/{orgSlug}
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
