'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';

const TIMELINE_BASE = [
  { date: 'CA',  desc: '0xC07ef1C7af6112C34A110809C6c8Efb343e63A64' },
  { date: 'What is BNBs?',  desc: 'BNBs is the leading BNB Chain inscription, publicly minted on 2023 via the EVM platform (evm.ink). The inscription has been fully converted into a meme token on Pinklock. BNBs is an inscription meme token that combines a fair launch mechanism — inherited from the inscription model — with a meme-style swap trading mechanism.' },
  { date: '2026.04',  desc: 'New official website launched: www.bnbscommunity.com' },
  { date: '2026.02',  desc: "BNBs' second real-world application — AI Analyse launched, capable of analyzing the trend strength of major tokens." },
  { date: '2025.12', desc: "BNBs' first real-world application — AI DEX — launched, enabling on-chain trading powered by AI. Transaction fees are only 0.25%, lower than the swap fees of most wallets and comparable to a CEX." },
  { date: '2025.07',  desc: 'The community mapped all inscriptions to meme on Pinklock, achieving full circulation of BNBs as a meme token.' },
  { date: '2024.12', desc: 'BNBs Swap launched; BNBs transitioned from a pure inscription to an inscription meme.' },
  { date: '2024.06',  desc: 'Mr.Bai took over the community and led its development.' },
  { date: '2024.03',  desc: 'Completed the inscription split, switching from per-contract trading to per-token trading.' },
  { date: '2023.12', desc: 'Reached a market cap of $40 million ($2 per token).' },
  { date: '2023.11.09', desc: 'BNBs inscription public mint, the BNBs community was established.' },
];

const BNBs_PRICE_API = '/api/bnbs-price';

function formatInteger(value: number): string {
  return value.toLocaleString('en-US', { maximumFractionDigits: 0 });
}

type BnbsData = {
  poolSize: number;
  marketCap: number;
};

async function getBnbsData(): Promise<BnbsData | null> {
  try {
    const res = await fetch(`${BNBs_PRICE_API}?t=${Date.now()}`, {
      headers: { Accept: 'application/json', 'Cache-Control': 'no-cache' },
      cache: 'no-store',
    });

    if (!res.ok) {
      throw new Error(`Request failed with status ${res.status}`);
    }

    const payload = await res.json();
    const totalPoolSizeUsd = Number(payload?.totalPoolSizeUsd ?? NaN);
    const marketCap = Number(payload?.marketCap ?? NaN);

    if (
      Number.isFinite(totalPoolSizeUsd) && totalPoolSizeUsd > 0 &&
      Number.isFinite(marketCap) && marketCap > 0
    ) {
      return { poolSize: totalPoolSizeUsd, marketCap };
    }
  } catch (error) {
    console.warn('BNBs data request failed:', error);
  }

  return null;
}

interface TimelineProps {
  sectionRef: React.RefObject<HTMLDivElement>;
  inView: boolean;
}

export default function Timeline({ sectionRef, inView }: TimelineProps) {
  const hasBeenInView = React.useRef(false);
  if (inView) hasBeenInView.current = true;
  const visible = hasBeenInView.current;

  const [data, setData] = useState<BnbsData | null>(null);
  const mountedRef = useRef(true);
  const retryCountRef = useRef(0);
  const retryTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (retryTimeoutRef.current) clearTimeout(retryTimeoutRef.current);
    };
  }, []);

  const fetchBnbsData = useCallback(async (isRetry = false) => {
    const value = await getBnbsData();
    if (!mountedRef.current) return;

    if (value !== null) {
      setData(value);
      retryCountRef.current = 0;
      return;
    }

    if (retryCountRef.current < 2 && !isRetry) {
      retryCountRef.current++;
      if (retryTimeoutRef.current) clearTimeout(retryTimeoutRef.current);
      retryTimeoutRef.current = setTimeout(() => fetchBnbsData(true), 3000);
    }
  }, []);

  // 初始加载
  useEffect(() => {
    fetchBnbsData();
  }, [fetchBnbsData]);

  // 进入视口时刷新
  useEffect(() => {
    if (inView) {
      retryCountRef.current = 0;
      fetchBnbsData();
    }
  }, [inView, fetchBnbsData]);

  // 进入视口后定期刷新（每 30 秒）
  useEffect(() => {
    if (!inView) return;

    const interval = setInterval(() => {
      retryCountRef.current = 0;
      fetchBnbsData();
    }, 30000);

    return () => clearInterval(interval);
  }, [inView, fetchBnbsData]);

  const timeline = React.useMemo(() => {
    const poolItem = {
      date: 'Pool Size（Pool Size vs Market Cap）',
      desc:
        data !== null
          ? `${formatInteger(data.poolSize)} USD（${((data.poolSize / data.marketCap) * 100).toFixed(2)} %）`
          : '—',
    };
    const next = [...TIMELINE_BASE];
    next.splice(1, 0, poolItem); // 插在 CA 之后
    return next;
  }, [data]);

  return (
    <section ref={sectionRef} className="w-full md:w-1/2 py-3 md:py-4 px-3 md:px-0 md:pr-4" style={{ marginBottom: 20 }}>
      <div
        className="relative z-10"
        style={{ transform: 'scale(0.95)', transformOrigin: 'top left' }}
      >
        <div className="relative">

          {/* Vertical line */}
          <div
            style={{
              position: 'absolute',
              top: 0,
              bottom: 0,
              left: '12px',
              width: '4px',
              background: 'linear-gradient(180deg, #5B7FFF 0%, #00D084 100%)',
              boxShadow: '0 0 20px rgba(91,127,255,0.3)',
              transform: `translateX(-50%) scaleY(${visible ? 1 : 0})`,
              transformOrigin: 'top',
              borderRadius: '2px',
              transition: 'transform 1.4s cubic-bezier(0.22, 1, 0.36, 1)',
              marginBottom: 20,
            }}
          />

          <div className="space-y-[10px] sm:space-y-[15px] pb-5">
            {timeline.map((item, i) => {
              const isEven = i % 2 === 0;
              return (
                <div
                  key={item.date + i}
                  className="relative"
                  style={{
                    opacity: visible ? 1 : 0,
                    transform: visible ? 'translateY(0)' : 'translateY(20px)',
                    transition: `all 0.6s ease-out ${i * 0.1}s`,
                  }}
                >
                  {/* Dot */}
                  <div
                    className="absolute z-20"
                    style={{ left: '12px', top: '0px', transform: 'translateX(-50%)' }}
                  >
                    <div
                      className="w-3 h-3 sm:w-4 sm:h-4 rounded-full"
                      style={{
                        background: isEven ? '#5B7FFF' : '#00D084',
                        boxShadow: isEven
                          ? '0 0 16px rgba(91,127,255,0.6)'
                          : '0 0 16px rgba(0,208,132,0.6)',
                        border: '3px solid #FFFFFF',
                      }}
                    />
                  </div>

                  {/* Card */}
                  <div style={{ paddingLeft: 'clamp(28px, 6vw, 38px)' }}>
                    <div
                      className="minimal-card rounded-xl hover:shadow-lg transition-all duration-300"
                      style={{
                        background: isEven
                          ? 'linear-gradient(135deg, #FFFFFF 0%, rgba(91,127,255,0.03) 100%)'
                          : 'linear-gradient(135deg, #FFFFFF 0%, rgba(0,208,132,0.03) 100%)',
                        border: isEven
                          ? '1px solid rgba(91,127,255,0.2)'
                          : '1px solid rgba(0,208,132,0.2)',
                        padding: 'clamp(8px, 2vw, 14.4px) clamp(10px, 2.5vw, 17.6px)',
                        width: '110%',
                        maxWidth: 'calc(100% - 10px)',
                        textAlign: 'left',
                      }}
                    >
                      <div
                        className="text-sm sm:text-base font-bold mb-1"
                        style={{
                          color: isEven ? '#5B7FFF' : '#00D084',
                          fontFamily: "'Orbitron', sans-serif",
                          letterSpacing: '0.05em',
                        }}
                      >
                        {item.date}
                      </div>
                      <p
                        className="text-sm sm:text-base lg:text-lg"
                        style={{
                          color: '#666',
                          lineHeight: 1.6,
                          fontFamily: "'Noto Sans SC', sans-serif",
                          wordBreak: 'break-all',
                          overflowWrap: 'break-word',
                        }}
                      >
                        {item.desc}
                      </p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

        </div>
      </div>
    </section>
  );
}