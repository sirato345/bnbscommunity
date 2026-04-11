'use client';

import Header from "../../components/Header";

export default function AiDexPage() {
  return (
    <div>
      <Header />
      <div className="w-full bg-white" style={{ paddingTop: 64, height: '100vh' }}>
        <iframe
          src="https://aiexchange.bnbscommunity.com?homepage=true"
          className="w-full h-full border-0"
          title="AI DEX Exchange"
          sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
        />
      </div>
    </div>
  );
}