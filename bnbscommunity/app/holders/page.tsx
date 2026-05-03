'use client';

export default function HoldersPage() {
  return (
    <div className="w-full bg-white" style={{ paddingTop: 64, height: '100vh' }}>
      <iframe
        src="https://bnbchain.bnbscommunity.com"
        className="w-full border-0"
        style={{ height: 'calc(100vh - 64px)' }}
        title="BNB Chain Community"
        sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
      />
    </div>
  );
}