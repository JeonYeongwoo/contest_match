import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Contest Mate | 맞춤형 공모전 추천",
  description: "관심사와 참여 조건으로 대학생 공모전을 추천합니다.",
  other: {
    "codex-preview": "development",
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body className="antialiased">{children}</body>
    </html>
  );
}
