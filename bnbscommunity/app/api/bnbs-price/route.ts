import { NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';
export const revalidate = 0;

const BNBs_CONTRACT = '0xc07ef1c7af6112c34a110809c6c8efb343e63a64';
const BINANCE_PRICE_URL = `https://web3.binance.com/bapi/defi/v4/public/wallet-direct/buw/wallet/market/token/dynamic/info?chainId=56&contractAddress=0xC07ef1C7af6112C34A110809C6c8Efb343e63A64`;
const FALLBACK_PRICE_USD = 0.00001;
const FALLBACK_MARKET_CAP = 1_000;

async function tryBinancePrice() {
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
    const data = payload?.data && typeof payload.data === 'object' ? payload.data as Record<string, unknown> : null;

    const priceUsd = Number(data?.price ?? 0);
    const marketCap = Number(data?.marketCap ?? data?.market_cap ?? 0);

    if (Number.isFinite(priceUsd) && priceUsd > 0) {
      return {
        priceUsd,
        marketCap: Number.isFinite(marketCap) ? Math.trunc(marketCap) : 0,
        source: 'binance',
      };
    }

    console.warn('[bnbs-price] Binance response had no usable price field', JSON.stringify(payload).slice(0, 500));
  } catch (err) {
    console.error('[bnbs-price] Binance request threw', err);
  }

  return null;
}

export async function GET() {
  const stats = await tryBinancePrice();

  if (stats) {
    return NextResponse.json(
      {
        priceUsd: stats.priceUsd,
        marketCap: stats.marketCap,
        source: stats.source,
      },
      { status: 200 }
    );
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
