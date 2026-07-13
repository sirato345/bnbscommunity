'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import Link from 'next/link';

function useCounter(
  target: number,
  duration: number = 2000,
  start: boolean = false,
  initValue: number
) {
  const [count, setCount] = useState(initValue);

  useEffect(() => {
    if (!start) {
      setCount(target);
      return;
    }

    let startTime: number | null = null;
    const step = (timestamp: number) => {
      if (!startTime) startTime = timestamp;
      const progress = Math.min((timestamp - startTime) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setCount(initValue + (target - initValue) * eased);
      if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }, [target, duration, start, initValue]);

  return count;
}

function formatInteger(value: number): string {
  const n = Math.floor(value);
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(0) + 'M';
  if (n >= 1_000) return (n / 1_000).toFixed(0) + 'K';
  return n.toLocaleString();
}

function formatPrice(value: number): string {
  return value.toFixed(5);
}

function formatDefault(value: number): string {
  if (value >= 1_000_000) return (value / 1_000_000).toFixed(1) + 'M';
  if (value >= 1_000) return (value / 1_000).toFixed(0) + 'K';
  return Math.floor(value).toLocaleString();
}

type StatType = 'integer' | 'price' | 'default';

interface StatConfig {
  label: string;
  value: number;
  initValue: number;
  prefix?: string;
  suffix?: string;
  type?: StatType;
}

function StatCard({ stat, start }: { stat: StatConfig; start: boolean }) {
  const count = useCounter(stat.value, 2000, start, stat.initValue);
  const formatted =
    stat.type === 'integer' ? formatInteger(count)
      : stat.type === 'price' ? formatPrice(count)
        : formatDefault(count);

  return (
    <div className="minimal-card rounded-lg p-3 sm:p-6 text-center transition-all duration-300 hover:scale-105">
      <div
        className="text-xl sm:text-2xl lg:text-4xl font-black mb-1 truncate"
        style={{
          fontFamily: "'Orbitron', sans-serif",
          background: 'linear-gradient(135deg, rgb(91, 127, 255) 0%, rgb(0, 208, 132) 100%)',
          WebkitBackgroundClip: 'text',
          backgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
          color: 'transparent',
        }}
      >
        {stat.prefix}{formatted}{stat.suffix}
      </div>
      <div
        className="text-xs sm:text-sm leading-tight"
        style={{ color: '#999', fontFamily: "'Noto Sans SC', sans-serif" }}
      >
        {stat.label}
      </div>
    </div>
  );
}

const BNBs_PRICE_API = '/api/bnbs-price';

const getBNBsInfo = async (): Promise<[number, number]> => {
  try {
    const res = await fetch(`${BNBs_PRICE_API}?t=${Date.now()}`, {
      headers: { Accept: 'application/json', 'Cache-Control': 'no-cache' },
      cache: 'no-store',
    });

    if (!res.ok) {
      throw new Error(`Request failed with status ${res.status}`);
    }

    const payload = await res.json();
    const price = Number(payload?.priceUsd ?? 0);
    const marketCap = Math.trunc(Number(payload?.marketCap ?? 0));

    if (Number.isFinite(price) && Number.isFinite(marketCap) && price > 0) {
      return [price, marketCap];
    }
  } catch (error) {
    console.warn('BNBs price request failed:', error);
  }

  throw new Error('Unable to load BNBs price data');
};

const BASE_STATS: StatConfig[] = [
  { label: 'TOTAL SUPPLY', value: 21_000_000, initValue: 1_000_000, type: 'integer' },
  { label: 'HOLDERS', value: 3_953, initValue: 1_000 },
  { label: 'MARKET CAP', value: 1_000, initValue: 1_000, prefix: '$' },
  { label: 'PRICE', value: 0.00001, initValue: 0.00001, prefix: '$', type: 'price' },
];

interface StatsProps {
  logoSectionRef: React.RefObject<HTMLDivElement>;
  statsSectionRef: React.RefObject<HTMLDivElement>;
  statsInView: boolean;
}

export default function Stats({ logoSectionRef, statsSectionRef, statsInView }: StatsProps) {
  const [stats, setStats] = useState<StatConfig[]>(BASE_STATS);
  const retryCountRef = useRef(0);
  const retryTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const mountedRef = useRef(true);
  const isFirstLoadRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (retryTimeoutRef.current) clearTimeout(retryTimeoutRef.current);
    };
  }, []);

  const fetchStatsData = async (isRetry = false): Promise<boolean> => {
    try {
      const [price, marketCap] = await getBNBsInfo();

      if (!mountedRef.current) return false;

      console.log('Fetched BNBs stats:', { price, marketCap });

      setStats(prev => prev.map(s => {
        if (s.label === 'PRICE') return { ...s, value: price };
        if (s.label === 'MARKET CAP') return { ...s, value: marketCap };
        return s;
      }));

      retryCountRef.current = 0;
      isFirstLoadRef.current = false;
      return true;

    } catch (error) {
      console.error('Failed to fetch stats:', error);

      if (retryCountRef.current < 2 && !isRetry) {
        retryCountRef.current++;
        console.log(`请求失败，触发重试... (${retryCountRef.current}/2)`);
        if (retryTimeoutRef.current) clearTimeout(retryTimeoutRef.current);
        retryTimeoutRef.current = setTimeout(() => fetchStatsData(true), 3000);
      }
      return false;
    }
  };

  // 初始加载
  useEffect(() => {
    fetchStatsData();
  }, []);

  // 进入视口时获取最新数据
  useEffect(() => {
    if (statsInView) {
      retryCountRef.current = 0; // 重置重试计数
      fetchStatsData();
    }
  }, [statsInView]);

  // 进入视口后定期刷新
  useEffect(() => {
    if (!statsInView) return;
    
    const interval = setInterval(() => {
      retryCountRef.current = 0; // 重置重试计数
      fetchStatsData();
    }, 30000);
    
    return () => clearInterval(interval);
  }, [statsInView]);

  return (
    <div className="w-full md:w-1/2 flex flex-col">

      {/* ロゴセクション */}
      <div
        ref={logoSectionRef}
        className="flex items-center justify-center pt-7 pb-7 sm:pt-0 sm:pb-0"
        style={{ height: '47%' }}
      >
        <div
          className="relative w-28 h-28 sm:w-40 sm:h-40 lg:w-52 lg:h-52 rounded-full flex items-center justify-center"
          style={{
            background: 'radial-gradient(circle, rgba(91,127,255,0.1) 0%, rgba(248,249,250,0.8) 100%)',
            border: '1px solid rgba(91,127,255,0.2)',
            boxShadow: '0 0 60px rgba(91,127,255,0.1), inset 0 0 60px rgba(91,127,255,0.05)',
          }}
        >
          <img
            src="/logo-b.png"
            alt="BNBs Token"
            className="w-24 h-24 sm:w-36 sm:h-36 lg:w-48 lg:h-48 object-contain animate-float"
          />
        </div>
      </div>

      {/* Statsセクション */}
      <section
        ref={statsSectionRef}
        className="flex flex-col items-center justify-center px-2 sm:px-6 gap-3 sm:gap-6"
        style={{ height: '50%' }}
      >
        <div className="w-full grid grid-cols-2 gap-2 sm:gap-x-2 sm:gap-y-3">
          {stats.map((stat) => (
            <div key={stat.label} className="w-full px-1">
              <StatCard stat={stat} start={statsInView} />
            </div>
          ))}
        </div>

        {/* ソーシャルリンク */}
        <div className="flex items-center gap-2 sm:gap-3">

          {/* X (Twitter) */}
          <a
            href="https://x.com/BNBS_BSC20"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center justify-center gap-1 sm:gap-2 w-16 sm:w-24 h-8 sm:h-10 rounded-full transition-all duration-200 hover:scale-105 hover:opacity-90"
            style={{
              background: '#111',
              fontSize: 'clamp(10px, 2.5vw, 13px)',
              fontWeight: 700,
              color: '#fff',
              letterSpacing: '0.06em',
              fontFamily: "'Orbitron', sans-serif",
              whiteSpace: 'nowrap',
            }}
            aria-label="X (Twitter)"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-4.714-6.231-5.401 6.231H2.744l7.737-8.835L1.254 2.25H8.08l4.253 5.622L18.244 2.25zm-1.161 17.52h1.833L7.084 4.126H5.117L17.083 19.77z" fill="#fff" />
            </svg>
            X
          </a>

          {/* Telegram */}
          <a
            href="https://t.me/BNBSGlobalCommunity"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center justify-center gap-1 sm:gap-2 w-16 sm:w-24 h-8 sm:h-10 rounded-full transition-all duration-200 hover:scale-105 hover:opacity-90"
            style={{
              background: '#229ED9',
              fontSize: 'clamp(10px, 2.5vw, 13px)',
              fontWeight: 700,
              color: '#fff',
              letterSpacing: '0.06em',
              fontFamily: "'Orbitron', sans-serif",
              whiteSpace: 'nowrap',
            }}
            aria-label="Telegram"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm4.93 6.686-1.685 7.944c-.126.57-.458.71-.927.44l-2.564-1.89-1.237 1.19c-.137.136-.252.252-.516.252l.185-2.614 4.762-4.302c.207-.184-.045-.286-.32-.102L7.67 14.383l-2.53-.79c-.55-.172-.56-.55.114-.814l9.875-3.808c.458-.165.858-.112.8.715z" fill="#fff" />
            </svg>
            TG
          </a>

          {/* BUY ボタン */}
          <Link
            href="/ai-dex"
            className="flex items-center justify-center gap-1 sm:gap-2 w-16 sm:w-24 h-8 sm:h-10 rounded-full transition-all duration-200 hover:scale-105 hover:opacity-90"
            style={{
              background: 'linear-gradient(135deg, #5B7FFF 0%, #00D084 100%)',
              fontSize: 'clamp(10px, 2.5vw, 13px)',
              fontWeight: 700,
              color: '#fff',
              letterSpacing: '0.06em',
              fontFamily: "'Orbitron', sans-serif",
              whiteSpace: 'nowrap',
            }}
            aria-label="Buy BNBs"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="9" cy="21" r="1.5" fill="#fff" />
              <circle cx="20" cy="21" r="1.5" fill="#fff" />
              <path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 001.97-1.67L23 6H6" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            BUY
          </Link>
        </div>
      </section>
    </div>
  );
}