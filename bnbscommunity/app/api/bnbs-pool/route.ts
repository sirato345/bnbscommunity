import { NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';
export const revalidate = 0;
export const runtime = 'nodejs';

const BNBs_CONTRACT = '0xc07ef1c7af6112c34a110809c6c8efb343e63a64';

// GeckoTerminal —— 无需 API Key
const GECKOTERMINAL_URL = `https://api.geckoterminal.com/api/v2/networks/bsc/tokens/${BNBs_CONTRACT}/pools`;

// ===== 缓存配置 =====
const CACHE_TTL_MS = 15 * 1000;

type GeckoTerminalPool = {
  attributes?: {
    reserve_in_usd?: string | null;
    name?: string;
    address?: string;
  };
};

type PoolResult = { totalPoolSizeUsd: number; poolCount: number };

let poolCache: { data: PoolResult | null; timestamp: number } | null = null;

async function tryGeckoTerminalPools(): Promise<PoolResult | null> {
  try {
    const res = await fetch(GECKOTERMINAL_URL, {
      headers: {
        Accept: 'application/json',
        'User-Agent': 'Mozilla/5.0',
      },
      cache: 'no-store',
    });

    if (!res.ok) {
      console.warn('[bnbs-pool] GeckoTerminal request failed with status', res.status);
      return null;
    }

    const payload = await res.json();
    const pools = payload?.data as GeckoTerminalPool[] | undefined;

    if (!Array.isArray(pools) || pools.length === 0) {
      console.warn('[bnbs-pool] GeckoTerminal returned no pools');
      return null;
    }

    const totalPoolSizeUsd = pools.reduce((sum, pool) => {
      const reserve = Number(pool.attributes?.reserve_in_usd ?? 0);
      return sum + (Number.isFinite(reserve) ? reserve : 0);
    }, 0);

    if (!Number.isFinite(totalPoolSizeUsd) || totalPoolSizeUsd <= 0) {
      console.warn('[bnbs-pool] GeckoTerminal total reserve not usable:', totalPoolSizeUsd);
      return null;
    }

    console.log(
      `[bnbs-pool] GeckoTerminal found ${pools.length} pools, total reserve: $${totalPoolSizeUsd}`
    );

    return { totalPoolSizeUsd, poolCount: pools.length };
  } catch (err) {
    console.error('[bnbs-pool] GeckoTerminal request threw', err);
    return null;
  }
}

export async function GET() {
  const now = Date.now();

  // 1. 命中缓存
  if (poolCache && now - poolCache.timestamp < CACHE_TTL_MS) {
    console.log('[bnbs-pool] pool cache hit');
    const cached = poolCache.data;
    return NextResponse.json(
      cached ?? { totalPoolSizeUsd: null, poolCount: 0 },
      { status: 200 }
    );
  }

  // 2. 请求真实数据
  const pools = await tryGeckoTerminalPools();

  if (pools) {
    poolCache = { data: pools, timestamp: now };
    return NextResponse.json(pools, { status: 200 });
  }

  // 3. 失败时返回旧缓存（如果有）
  if (poolCache?.data) {
    console.warn('[bnbs-pool] returning stale pool cache');
    return NextResponse.json(poolCache.data, { status: 200 });
  }

  // 4. 彻底失败
  poolCache = { data: null, timestamp: now };
  return NextResponse.json(
    { totalPoolSizeUsd: null, poolCount: 0 },
    { status: 200 }
  );
}