'use client';
import './globals.css';
import { useRef, useState, useEffect } from 'react';
import React from 'react';

// Intersection observer hook
function useInView(threshold = 0.15) {
  const ref = useRef<HTMLDivElement>(null);
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => setInView(entry.isIntersecting),
      { threshold }
    );
    if (ref.current) observer.observe(ref.current);
    return () => observer.disconnect();
  }, [threshold]);
  return { ref, inView };
}

// Animated counter hook - 支持从小数值开始
function useCounter(target: number, duration: number = 2000, start: boolean = false, isPrice: boolean = false) {
  const [count, setCount] = useState(isPrice ? 0.00001 : 0); // 价格从 0.0001 开始
  
  useEffect(() => {
    if (!start) return;
    
    let startTime: number | null = null;
    const startValue = isPrice ? 0.00001 : 0; // 价格从 0.0001 开始
    const endValue = target;
    
    const step = (timestamp: number) => {
      if (!startTime) startTime = timestamp;
      const progress = Math.min((timestamp - startTime) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      
      // 对于价格，保留小数精度
      if (isPrice) {
        const currentValue = startValue + (endValue - startValue) * eased;
        setCount(currentValue);
      } else {
        setCount(Math.floor(eased * target));
      }
      
      if (progress < 1) requestAnimationFrame(step);
    };
    
    requestAnimationFrame(step);
  }, [target, duration, start, isPrice]);
  
  return count;
}

// 格式化数值函数 - 避免科学计数法
function formatValue(value: number, isPrice: boolean = false): string {
  // 处理价格
  if (isPrice) {
    // 确保不显示科学计数法
    if (value < 0.00001) {
      // 极小值：显示完整小数（最多8位）
      return value.toFixed(5).replace(/\.?0+$/, '');
    } else if (value < 0.001) {
      // 小于 0.001：显示小数点后 6 位
      return value.toFixed(5);
    } else if (value < 0.01) {
      // 小于 0.01：显示小数点后 5 位
      return value.toFixed(5);
    } else if (value < 0.1) {
      // 小于 0.1：显示小数点后 4 位
      return value.toFixed(5);
    } else if (value < 1) {
      // 小于 1：显示小数点后 3 位
      return value.toFixed(5);
    } else if (value < 1000) {
      // 正常显示小数点后 2 位
      return value.toFixed(5);
    } else {
      // 大数值用 K/M 缩写
      if (value >= 1000000) {
        return (value / 1000000).toFixed(2) + "M";
      } else if (value >= 1000) {
        return (value / 1000).toFixed(1) + "K";
      }
      return value.toFixed(0);
    }
  }
  
  // 普通数值处理
  if (value >= 1000000) {
    return (value / 1000000).toFixed(1) + "M";
  } else if (value >= 1000) {
    return (value / 1000).toFixed(0) + "K";
  }
  return value.toString();
}

// Stat card component
function StatCard({ stat, start }: { stat: { label: string; value: number; prefix?: string; suffix?: string; unit?: string; isPrice?: boolean }; start: boolean }) {
  const isPrice = stat.isPrice || false;
  const count = useCounter(stat.value, 2000, start, isPrice);
  const formatted = formatValue(count, isPrice);

  return (
    <div className="minimal-card rounded-lg p-6 text-center transition-all duration-300 hover:scale-105">
      <div
        className="text-3xl lg:text-4xl font-black mb-1"
        style={{
          fontFamily: "'Orbitron', sans-serif",
          background: "linear-gradient(135deg, rgb(91, 127, 255) 0%, rgb(0, 208, 132) 100%)",
          WebkitBackgroundClip: "text",
          backgroundClip: "text",
          WebkitTextFillColor: "transparent",
          color: "transparent", // 备用方案
        }}
      >
        {stat.prefix}{formatted}{stat.suffix}{stat.unit}
      </div>
      <div className="text-sm" style={{ color: "#999", fontFamily: "'Noto Sans SC', sans-serif" }}>
        {stat.label}
      </div>
    </div>
  );
}

export default function HomePage() {
  const { ref: statsRef, inView: statsInView } = useInView();

  return (
    <div className="min-h-screen bg-white">
      <div className="absolute top-0 left-0 w-1/2 h-1/2 flex items-center justify-center">
        <div className="transform translate-y-[60px]">
          <div
            className="relative w-45 h-45 lg:w-56 lg:h-56 rounded-full flex items-center justify-center"
            style={{
              background: "radial-gradient(circle, rgba(91, 127, 255, 0.1) 0%, rgba(248, 249, 250, 0.8) 100%)",
              border: "1px solid rgba(91, 127, 255, 0.2)",
              boxShadow: "0 0 60px rgba(91, 127, 255, 0.1), inset 0 0 60px rgba(91, 127, 255, 0.05)",
            }}
          >
            <img
              src="/logo-b.png"
              alt="BNBs Token"
              className="w-40 h-40 lg:w-52 lg:h-52 object-contain animate-float"
            />
          </div>
        </div>
      </div>

      <section ref={statsRef} className="py-16 absolute bottom-0 left-0 w-1/2 h-1/2" style={{ background: "#FFFFFF" }}>
        <div className="container">
          <div className="grid grid-cols-2 gap-x-1 gap-y-3">
            {[
              { label: "TOTAL SUPPLY", value: 21000000 },
              { label: "HOLDERS", value: 3953 },
              { label: "MARKET CAP", value: 50000000, prefix: "$" },
              { label: "PRICE", value: 0.00126, prefix: "$", isPrice: true },
            ].map((stat) => (
              <div key={stat.label} className="w-[calc(100%-20px)] mx-auto">
                <StatCard key={stat.label} stat={stat} start={statsInView} />
              </div>
            ))}
          </div>
        </div>
      </section>
      
    </div>
  );
}