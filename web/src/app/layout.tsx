import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Tabs } from "./tabs";

export const metadata: Metadata = {
  title: "캠퍼스 알리미",
  description: "가톨릭대 학생에게 돈이 걸린 공지와 학생 혜택을, 출처와 확인일이 붙은 것만 알려 드립니다.",
  manifest: "/manifest.webmanifest",
  icons: { icon: "/icon.svg", apple: "/icon.svg" },
  appleWebApp: { capable: true, title: "캠퍼스 알리미", statusBarStyle: "default" },
};

export const viewport: Viewport = {
  themeColor: "#ffffff",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <main>{children}</main>
        <Tabs />
      </body>
    </html>
  );
}
