'use client';

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
  
  const menuItems: MenuItem[] = [
    { name: 'BNBs', path: '/', isExternal: false },
    { name: 'Holders', path: '/holders', isExternal: false, externalUrl: 'https://bnbchain.bnbscommunity.com' },
    { name: 'AI DEX', path: '/ai-dex', isExternal: false, externalUrl: 'https://aiexchange.bnbscommunity.com' },
    { name: 'AI Analyse', path: '/ai-analyse', isExternal: false, externalUrl: 'https://godseye.bnbscommunity.com' },
    // { name: 'AI Trading', path: '/ai-trading', isExternal: false }
  ];

  const isActive = (item: MenuItem) => {
    return pathname === item.path;
  };

  return (
    <>
      {/* PC版 - 在 md 及以上屏幕显示 */}
      <header className="fixed top-0 left-0 right-0 bg-white border-b border-gray-200 z-50 hidden md:block">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-14">
            <Link href="/" className="flex items-center gap-3">
              <img
                src="/BNBs.svg"
                alt="BNBs Logo"
                className="w-8 h-8 rounded-full object-cover"
              />
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

      {/* 移动版 - 只在小于 md 屏幕显示 */}
      <header className="fixed top-0 left-0 right-0 bg-white border-b border-gray-200 z-50 block md:hidden">
        <div className="px-3">
          <Link href="/" className="flex items-center gap-2.5 no-underline h-11">
            <img
              src="/BNBs.svg"
              alt="BNBs Logo"
              className="w-7 h-7 rounded-full object-cover flex-shrink-0"
            />
            <div className="flex flex-col gap-0.5 leading-none">
              <span
                className="font-black text-base inline-block whitespace-nowrap"
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
                className="text-[7px] whitespace-nowrap"
                style={{
                  fontFamily: "'Montserrat', sans-serif",
                  color: "rgba(100, 100, 100, 0.6)",
                  letterSpacing: "0.1em",
                }}
              >
                Community
              </span>
            </div>
          </Link>
          
          <div className="flex gap-1 pb-1.5">
            {menuItems.map((item) => (
              <Link
                key={item.name}
                href={item.isExternal && item.externalUrl ? item.externalUrl : item.path}
                {...(item.isExternal ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
                className={`
                  flex-1 flex items-center justify-center px-1 py-1 rounded-md text-[11px] font-medium text-center no-underline whitespace-nowrap
                  ${isActive(item)
                    ? 'bg-gradient-to-r from-blue-500 to-purple-600 text-white'
                    : 'text-gray-600 bg-gray-100'
                  }
                `}
              >
                {item.name}
              </Link>
            ))}
          </div>
        </div>
      </header>
    </>
  );
}