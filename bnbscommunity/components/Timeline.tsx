'use client';

import React from 'react';

const TIMELINE = [
  { date: '2026.04',  desc: 'New official website launched: www.bnbscommunity.com' },
  { date: '2026.02',  desc: "BNBs' second real-world application — AI Analyse launched, capable of analyzing the trend strength of major tokens." },
  { date: '2025.12', desc: "BNBs' first real-world application — AI DEX — launched, enabling on-chain trading powered by AI. Transaction fees are only 0.3%, lower than the swap fees of most wallets and comparable to a CEX." },
  { date: '2025.07',  desc: 'The community mapped all inscriptions to meme on Pinklock, achieving full circulation of BNBs as a meme token.' },
  { date: '2024.12', desc: 'BNBs Swap launched; BNBs transitioned from a pure inscription to an inscription meme.' },
  { date: '2024.06',  desc: 'Mr.Shirato took over the community and led its development.' },
  { date: '2024.03',  desc: 'Completed the inscription split, switching from per-contract trading to per-token trading.' },
  { date: '2023.12', desc: 'Reached a market cap of $40 million ($2 per token).' },
  { date: '2023.11.09', desc: 'BNBs inscription public mint, the BNBs community was established.' },
];

interface TimelineProps {
  sectionRef: React.RefObject<HTMLDivElement>;
  inView: boolean;
}

export default function Timeline({ sectionRef, inView }: TimelineProps) {
  return (
    <section ref={sectionRef} className="w-1/2 py-4 pr-4" style={{ marginBottom: 20 }}>
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
              transform: `translateX(-50%) scaleY(${inView ? 1 : 0})`,
              transformOrigin: 'bottom',
              borderRadius: '2px',
              transition: 'transform 1.4s cubic-bezier(0.22, 1, 0.36, 1)',
              marginBottom: 20,
            }}
          />

          <div className="space-y-[15px] pb-5">
            {TIMELINE.map((item, i) => {
              const isEven = i % 2 === 0;
              return (
                <div
                  key={i}
                  className="relative"
                  style={{
                    opacity: inView ? 1 : 0,
                    transform: inView ? 'translateY(0)' : 'translateY(20px)',
                    transition: `all 0.6s ease-out ${i * 0.1}s`,
                  }}
                >
                  {/* Dot */}
                  <div
                    className="absolute z-20"
                    style={{ left: '12px', top: '0px', transform: 'translateX(-50%)' }}
                  >
                    <div
                      className="w-4 h-4 rounded-full"
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
                  <div style={{ paddingLeft: '38px' }}>
                    <div
                      className="minimal-card rounded-xl hover:shadow-lg transition-all duration-300"
                      style={{
                        background: isEven
                          ? 'linear-gradient(135deg, #FFFFFF 0%, rgba(91,127,255,0.03) 100%)'
                          : 'linear-gradient(135deg, #FFFFFF 0%, rgba(0,208,132,0.03) 100%)',
                        border: isEven
                          ? '1px solid rgba(91,127,255,0.2)'
                          : '1px solid rgba(0,208,132,0.2)',
                        padding: '14.4px 17.6px',
                        width: '110%',
                        maxWidth: 'calc(100% - 10px)',
                        textAlign: 'left',
                      }}
                    >
                      <div
                        className="text-sm font-bold mb-1"
                        style={{
                          color: isEven ? '#5B7FFF' : '#00D084',
                          fontFamily: "'Orbitron', sans-serif",
                          letterSpacing: '0.05em',
                        }}
                      >
                        {item.date}
                      </div>
                      <p
                        className="text-sm lg:text-base"
                        style={{ color: '#666', lineHeight: 1.6, fontFamily: "'Noto Sans SC', sans-serif" }}
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
