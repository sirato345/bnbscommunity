'use client';

import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function AiDexFrame() {
  const isActive = usePathname() === '/ai-dex';
  const [visited, setVisited] = useState(isActive);

  useEffect(() => {
    if (isActive) setVisited(true);
  }, [isActive]);

  if (!visited && !isActive) return null;

  return (
    <div
      className="w-full"
      hidden={!isActive}
      style={{ paddingTop: 64, height: '100vh', backgroundColor: '#fff' }}
    >
      <iframe
        src="https://aiexchange.bnbscommunity.com?homepage=true"
        className="w-full h-full border-0"
        title="AI DEX Exchange"
        sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
      />
    </div>
  );
}
