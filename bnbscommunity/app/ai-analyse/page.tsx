'use client';

import Header from "../../components/Header";

export default function AiAnalysePage() {
  return (
    <div>
      <Header />
      <div className="w-full bg-white" style={{ paddingTop: 64, height: '100vh' }}>
      <iframe
        src="https://godseye.bnbscommunity.com?homepage=true"
        className="w-full h-full border-0"
        title="AI Analyse"
        sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
      />
      </div>
    </div>
  );
}