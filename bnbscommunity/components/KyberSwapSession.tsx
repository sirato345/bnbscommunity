'use client';

import dynamic from 'next/dynamic';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';

const KyberSwap = dynamic(() => import('../app/kyberswap/kyberswap-client'), {
  ssr: false,
});

export default function KyberSwapSession() {
  const isActive = usePathname() === '/kyberswap';
  const [visited, setVisited] = useState(isActive);

  useEffect(() => {
    if (isActive) setVisited(true);
  }, [isActive]);

  if (!visited && !isActive) return null;

  return (
    <div hidden={!isActive}>
      <KyberSwap />
    </div>
  );
}
