import { NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';
export const revalidate = 0;
export const runtime = 'nodejs';

const BNBs_CONTRACT = '0xc07ef1c7af6112c34a110809c6c8efb343e63a64';
const BINANCE_PRICE_URL = `https://web3.binance.com/bapi/defi/v4/public/wallet-direct/buw/wallet/market/token/dynamic/info?chainId=56&contractAddress=${BNBs_CONTRACT}`;

const FALLBACK_PRICE_USD = 0.00001;
const FALLBACK_MARKET_CAP = 1_000;

// ===== 缓存配置 =====
const CACHE_TTL_MS = 8 * 1000;

type PriceResult = {
  priceUsd: number;
  marketCap: number;
  source: string;
};

let priceCache: { data: PriceResult | null; timestamp: number } | null = null;

async function tryBinancePrice(): Promise<PriceResult | null> {
  try {
    const res = await fetch(BINANCE_PRICE_URL, {
      headers: {
        Accept: 'application/json, text/plain, */*',
        'User-Agent': 'Mozilla/5.0',
      },
      cache: 'no-store',
    });

    if (!res.ok) {
      console.warn('[bnbs-price] Binance request failed with status', res.status);
      return null;
    }

    const payload = (await res.json()) as Record<string, unknown>;
    const data =
      payload.data && typeof payload.data === 'object'
        ? (payload.data as Record<string, unknown>)
        : null;

    const priceUsd = Number(data?.price ?? 0);
    const marketCap = Number(data?.marketCap ?? data?.market_cap ?? 0);

    if (Number.isFinite(priceUsd) && priceUsd > 0) {
      return {
        priceUsd,
        marketCap: Number.isFinite(marketCap) ? Math.trunc(marketCap) : 0,
        source: 'binance',
      };
    }

    console.warn(
      '[bnbs-price] Binance response had no usable price field',
      JSON.stringify(payload).slice(0, 500)
    );
  } catch (err) {
    console.error('[bnbs-price] Binance request threw', err);
  }

  return null;
}

export async function GET() {
  const now = Date.now();

  // 1. 命中缓存
  if (priceCache && now - priceCache.timestamp < CACHE_TTL_MS) {
    console.log('[bnbs-price] price cache hit');
    const cached = priceCache.data;
    return NextResponse.json(
      cached ?? {
        priceUsd: FALLBACK_PRICE_USD,
        marketCap: FALLBACK_MARKET_CAP,
        source: 'fallback',
      },
      { status: 200 }
    );
  }

  // 2. 请求真实数据
  const stats = await tryBinancePrice();

  if (stats) {
    priceCache = { data: stats, timestamp: now };
    return NextResponse.json(stats, { status: 200 });
  }

  // 3. 失败时返回旧缓存（如果有）
  if (priceCache?.data) {
    console.warn('[bnbs-price] returning stale price cache');
    return NextResponse.json(priceCache.data, { status: 200 });
  }

  // 4. 彻底失败，写 fallback 到缓存
  const fallback: PriceResult = {
    priceUsd: FALLBACK_PRICE_USD,
    marketCap: FALLBACK_MARKET_CAP,
    source: 'fallback',
  };
  priceCache = { data: fallback, timestamp: now };
  return NextResponse.json(fallback, { status: 200 });
}