'use client';

export default function HoldersPage() {
  return (
    <div className="w-full h-[calc(100vh-64px)] bg-white">
      <iframe
        src="https://bnbchain.bnbscommunity.com"
        className="w-full h-full border-0"
        title="BNB Chain Community"
        sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
      />
    </div>
  );
}