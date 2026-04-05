'use client';

export default function AiAnalysePage() {
  return (
    <div className="w-full h-[calc(100vh-64px)] bg-white">
      <iframe
        src="https://godseye.bnbscommunity.com?homepage=true"
        className="w-full h-full border-0"
        title="AI Analyse"
        sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
      />
    </div>
  );
}