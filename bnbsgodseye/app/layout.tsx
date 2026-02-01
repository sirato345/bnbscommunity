import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import Link from "next/link";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  icons: "BNBs.svg",
  title: "BNBs God's eye",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
 return (
    <html lang="zh">
      <body>
        {/* 添加简单导航 */}
        <nav style={{ padding: '20px', background: '#f0f0f0' }}>
          <Link href="/">首页</Link>
        </nav>
        <main>{children}</main>
      </body>
    </html>
  );
}
