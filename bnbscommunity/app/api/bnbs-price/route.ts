import { NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';
export const revalidate = 0;

const BNBs_CONTRACT = '0xc07ef1c7af6112c34a110809c6c8efb343e63a64';
const FALLBACK_PRICE_USD = 0.00001;
const FALLBACK_MARKET_CAP = 1_000;

async function tryMexc() {
  try {
    const res = await fetch(
      'https://www.mexc.com/api/dex/v1/onchain/get_token_price_info?chain_id=56&token_cas=0xc07ef1c7af6112c34a110809c6c8efb343e63a64',
      {
        headers: {
          Accept: 'application/json',
          'User-Agent': 'Mozilla/5.0',
        },
        cache: 'no-store',
      }
    );

    if (!res.ok) return null;

    const payload = (await res.json()) as Record<string, unknown>;
    const data = payload?.data && typeof payload.data === 'object' ? payload.data as Record<string, unknown> : null;
    const tokenList = Array.isArray(data?.token_list) ? data.token_list as Array<Record<string, unknown>> : [];
    const firstToken = tokenList[0];
    const priceUsd = Number(firstToken?.price ?? data?.price ?? data?.price_usd ?? data?.usd ?? 0);
    const marketCap = Number(firstToken?.market_cap ?? firstToken?.marketCap ?? data?.market_cap ?? data?.marketCap ?? data?.usd_market_cap ?? 0);

    if (Number.isFinite(priceUsd) && priceUsd > 0) {
      return {
        priceUsd,
        marketCap: Math.trunc(marketCap),
        source: 'mexc',
      };
    }
  } catch {
    // Fallback to other providers.
  }

  return null;
}

export async function GET() {
  const mexcStats = await tryMexc();
  if (mexcStats) {
    return NextResponse.json({
      priceUsd: mexcStats.priceUsd,
      marketCap: mexcStats.marketCap,
      source: mexcStats.source,
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
