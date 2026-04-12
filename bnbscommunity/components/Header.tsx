'use client';

import { useState } from 'react';
import { usePathname } from 'next/navigation';
import Link from 'next/link';

interface MenuItem {
  name: string;
  path: string;
  isExternal?: boolean;
  externalUrl?: string;
}

export default function Header() {
  const pathname = usePathname();
  
  const [menuItems] = useState<MenuItem[]>([
    { name: 'BNBs', path: '/', isExternal: false },
    { name: 'Holders', path: '/holders', isExternal: true, externalUrl: 'https://bnbchain.bnbscommunity.com' },
    { name: 'AI DEX', path: '/ai-dex', isExternal: true, externalUrl: 'https://aiexchange.bnbscommunity.com' },
    { name: 'AI Analyse', path: '/ai-analyse', isExternal: true, externalUrl: 'https://godseye.bnbscommunity.com' },
  ]);

  const isActive = (item: MenuItem) => {
    return pathname === item.path;
  };

  return (
    <div>
      <div className="hidden md:block">{/* PC */}
        <header className="fixed top-0 left-0 right-0 bg-white border-b border-gray-200 z-50">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-14">
              {/* 左侧 - Logo 和 Title */}
              <Link href="/" className="flex items-center gap-3">
                {/* Logo box */}
                <img
                  src="/BNBs.svg"
                  alt="BNBs Logo"
                  className="w-8 h-8 rounded-full object-cover"
                />
                {/* Logo text */}
                <div className="flex flex-col leading-none">
                  <span
                    className="font-black text-lg lg:text-xl"
                    style={{
                      fontFamily: "'Orbitron', sans-serif",
                      background: "linear-gradient(90deg, #BF953F, #FCF6B6, #B38728, #FBF5B7, #AA771C)",
                      WebkitBackgroundClip: "text",
                      backgroundClip: "text",
                      color: "transparent",
                      letterSpacing: "0.02em",
                      backgroundSize: "200% auto",
                      animation: "shine 3s linear infinite",
                    }}
                  >
                    BNBs
                  </span>
                  <span
                    className="text-xs"
                    style={{
                      fontFamily: "'Montserrat', sans-serif",
                      color: "rgba(100, 100, 100, 0.6)",
                      letterSpacing: "0.1em",
                      fontSize: "0.55rem",
                    }}
                  >
                    Community
                  </span>
                </div>
              </Link>

              {/* 中间和右侧 - 菜单 */}
              <nav className="flex items-center gap-1 sm:gap-2">
                {menuItems.map((item) => (
                  <Link
                    key={item.name}
                    href={item.path}
                    className={`
                      px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200
                      ${isActive(item)
                        ? 'bg-gradient-to-r from-blue-500 to-purple-600 text-white shadow-md'
                        : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                      }
                    `}
                  >
                    {item.name}
                  </Link>
                ))}
              </nav>
            </div>
          </div>
        </header>
      </div>
      <div className="block md:hidden">{/* モバイル */}
        <header style={{
          position: 'fixed', top: 0, left: 0, right: 0,
          backgroundColor: '#fff', borderBottom: '1px solid #e5e7eb', zIndex: 50,
        }}>
          <div style={{ padding: '0 12px' }}>
            {/* 1行目: ロゴ */}
            <Link href="/" style={{ display: 'flex', alignItems: 'center', gap: '10px', textDecoration: 'none', height: '44px' }}>
              <img src="/BNBs.svg" alt="BNBs Logo" style={{ width: '28px', height: '28px', borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
              <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', lineHeight: 1 }}>
                <span style={{
                  fontFamily: "'Orbitron', sans-serif", fontWeight: 900, fontSize: '16px',
                  background: 'linear-gradient(90deg, #BF953F, #FCF6B6, #B38728, #FBF5B7, #AA771C)',
                  WebkitBackgroundClip: 'text', backgroundClip: 'text', color: 'transparent',
                  letterSpacing: '0.02em', backgroundSize: '200% auto',
                  animation: 'shine 3s linear infinite', whiteSpace: 'nowrap', display: 'inline-block',
                }}>BNBs</span>
                <span style={{
                  fontFamily: "'Montserrat', sans-serif", color: 'rgba(100,100,100,0.6)',
                  letterSpacing: '0.1em', fontSize: '7px', whiteSpace: 'nowrap',
                }}>Community</span>
              </div>
            </Link>
            {/* 2行目: ナビ（均等分割・中央揃え） */}
            <div style={{
              display: 'flex',
              paddingBottom: '6px',
              gap: '4px',
            }}>
              {menuItems.map((item) => (
                <Link
                  key={item.name}
                  href={item.isExternal && item.externalUrl ? item.externalUrl : item.path}
                  {...(item.isExternal ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
                  style={{
                    flex: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '5px 2px',
                    borderRadius: '6px',
                    fontSize: '11px',
                    fontWeight: 500,
                    textAlign: 'center',
                    textDecoration: 'none',
                    whiteSpace: 'nowrap',
                    ...(isActive(item)
                      ? { background: 'linear-gradient(to right, #3b82f6, #9333ea)', color: '#fff' }
                      : { color: '#4b5563', background: '#f3f4f6' }),
                  }}
                >{item.name}</Link>
              ))}
            </div>
          </div>
        </header>
      </div>
    </div>
  );
}