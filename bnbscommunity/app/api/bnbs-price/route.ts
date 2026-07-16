import { NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';
export const revalidate = 0;

const BNBs_CONTRACT = '0xc07ef1c7af6112c34a110809c6c8efb343e63a64';
const FALLBACK_PRICE_USD = 0.00001;
const FALLBACK_MARKET_CAP = 1_000;

// GeckoTerminal indexes on-chain DEX pool data (like DexScreener), and does
// have this token indexed. It's a public, keyless API and, like DexScreener,
// is designed for server-side consumption so it shouldn't block requests
// coming from Vercel's IPs.
async function tryGeckoTerminal() {
  try {
    const res = await fetch(
      `https://api.geckoterminal.com/api/v2/networks/bsc/tokens/${BNBs_CONTRACT}`,
      {
        headers: { Accept: 'application/json' },
        cache: 'no-store',
      }
    );

    if (!res.ok) {
      console.warn('[bnbs-price] GeckoTerminal request failed with status', res.status);
      return null;
    }

    const payload = (await res.json()) as Record<string, unknown>;
    const data = payload?.data && typeof payload.data === 'object' ? payload.data as Record<string, unknown> : null;
    const attributes = data?.attributes && typeof data.attributes === 'object' ? data.attributes as Record<string, unknown> : null;

    const priceUsd = Number(attributes?.price_usd ?? 0);
    const marketCap = Number(attributes?.market_cap_usd ?? attributes?.fdv_usd ?? 0);

    if (Number.isFinite(priceUsd) && priceUsd > 0) {
      return {
        priceUsd,
        marketCap: Math.trunc(marketCap),
        source: 'geckoterminal',
      };
    }

    console.warn('[bnbs-price] GeckoTerminal response had no usable price field', JSON.stringify(payload).slice(0, 500));
  } catch (err) {
    console.error('[bnbs-price] GeckoTerminal request threw', err);
  }

  return null;
}

export async function GET() {
  const stats = await tryGeckoTerminal();

  if (stats) {
    return NextResponse.json({
      priceUsd: stats.priceUsd,
      marketCap: stats.marketCap,
      source: stats.source,
    }, { status: 200 });
  }

  return NextResponse.json(
    {
      priceUsd: FALLBACK_PRICE_USD,
      marketCap: FALLBACK_MARKET_CAP,
      source: 'fallback',
      error: 'Unable to load live BNBs price data',
    },
    { status: 200 }
  );
}