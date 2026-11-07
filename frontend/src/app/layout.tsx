import type { Metadata } from "next";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "YemomTech — AI-powered growth for Ethiopian businesses",
    template: "%s · YemomTech",
  },
  description:
    "YemomTech (YemomBizGrow) helps Ethiopian businesses manage their websites, teams and growth work in one secure workspace.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" data-scroll-behavior="smooth">
      <body className="min-h-screen font-sans">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
