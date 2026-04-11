import type { Metadata } from 'next';
import './globals.css';
import Header from '../components/Header';

export const metadata: Metadata = {
  icons: {
    icon: [
      { 
        url: 'BNBs.svg?v=' + Date.now(), // 添加时间戳
        type: 'image/svg+xml',
      },
      ],
    }, 
  title: "BNBs Community",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      {/* 解决字体问题 */}
      <head>
        <link 
          href="https://fonts.googleapis.com/css2?family=Orbitron:wght@400;500;600;700;800;900&display=swap" 
          rel="stylesheet" 
        />
      </head>
      <body className=" bg-white">
        <main>
          {children}
        </main>
      </body>
    </html>
  );
}