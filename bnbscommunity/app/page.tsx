'use client';
import './globals.css';
import { useRef, useState, useEffect } from 'react';
import Stats from '../components/Stats';
import Timeline from '../components/Timeline';
import Header from '../components/Header';

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

const HEADER_H = 64;

export default function HomePage() {
  const { ref: logoRef, inView: _logoInView } = useInView();
  const { ref: statsRef, inView: statsInView } = useInView();
  const { ref: historyRef, inView: historyInView } = useInView();

  return (
    // overflow-hidden でページ全体のスクロールを無効化
    <div className="h-screen overflow-hidden">
      <Header />

      {/* Header 分を除いた高さでスクロール領域を作る */}
      <div
        className="flex overflow-y-auto"
        style={{ height: `calc(100vh - ${HEADER_H}px)`, marginTop: HEADER_H }}
      >
        <div className="flex flex-col md:flex-row">
          {/* 左カラム: Logo (上) + Stats (下) */}
          <Stats
            logoSectionRef={logoRef}
            statsSectionRef={statsRef}
            statsInView={statsInView}
          />

          {/* 右カラム: Timeline */}
          <Timeline sectionRef={historyRef} inView={historyInView} />
        </div>
      </div>
    </div>
  );
}
