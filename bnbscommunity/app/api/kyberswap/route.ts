import { NextRequest, NextResponse } from 'next/server';

const NETWORKS = new Set(['ethereum', 'arbitrum', 'base', 'bsc', 'robinhood', 'hyperevm', 'elysium']);
const ADDRESS_PATTERN = /^0x[a-fA-F0-9]{40}$/;
const TOKEN_PATTERN = /^0x[a-fA-F0-9]{40}$/;
const NATIVE_TOKEN = '0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function jsonResponse(status: number, error: string) {
  return NextResponse.json({ error }, { status });
}

async function getResponseError(response: Response): Promise<string> {
  const text = await response.text();
  try {
    const payload: unknown = JSON.parse(text);
    if (isRecord(payload) && typeof payload.message === 'string') return payload.message;
    if (isRecord(payload) && isRecord(payload.data) && typeof payload.data.message === 'string') {
      return payload.data.message;
    }
  } catch {
    return `KyberSwap request failed (${response.status}).`;
  }
  return `KyberSwap request failed (${response.status}).`;
}

export async function POST(request: NextRequest) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return jsonResponse(400, 'Request body must be valid JSON.');
  }
  if (!isRecord(body)) return jsonResponse(400, 'Request body must be an object.');

  const { chain, tokenIn, tokenOut, amountIn, address } = body;
  if (typeof chain !== 'string' || !NETWORKS.has(chain)) {
    return jsonResponse(400, 'This network is not supported for same-chain swaps.');
  }
  if (
    typeof tokenIn !== 'string' ||
    (!TOKEN_PATTERN.test(tokenIn) && tokenIn.toLowerCase() !== NATIVE_TOKEN) ||
    typeof tokenOut !== 'string' ||
    (!TOKEN_PATTERN.test(tokenOut) && tokenOut.toLowerCase() !== NATIVE_TOKEN)
  ) {
    return jsonResponse(400, 'Choose valid input and output tokens.');
  }
  if (tokenIn.toLowerCase() === tokenOut.toLowerCase()) {
    return jsonResponse(400, 'Choose two different tokens.');
  }
  if (typeof address !== 'string' || !ADDRESS_PATTERN.test(address)) {
    return jsonResponse(400, 'Connect a wallet with a valid address.');
  }
  if (
    typeof amountIn !== 'string' ||
    amountIn.length > 78 ||
    !/^[0-9]+$/.test(amountIn) ||
    BigInt(amountIn) <= BigInt(0) ||
    BigInt(amountIn) >= BigInt(2) ** BigInt(256)
  ) {
    return jsonResponse(400, 'Enter a valid amount greater than zero.');
  }

  const headers = {
    accept: 'application/json',
    'X-Client-Id': process.env.KYBERSWAP_CLIENT_ID ?? 'BNBs',
  };
  const routeUrl = new URL(
    `https://aggregator-api.kyberswap.com/${chain}/api/v1/routes`,
  );
  routeUrl.search = new URLSearchParams({
    tokenIn,
    tokenOut,
    amountIn,
    origin: address,
    gasInclude: 'true',
  }).toString();

  try {
    const routeResponse = await fetch(routeUrl, { headers, cache: 'no-store' });
    if (!routeResponse.ok) {
      return jsonResponse(routeResponse.status, await getResponseError(routeResponse));
    }
    const routePayload: unknown = await routeResponse.json();
    if (
      !isRecord(routePayload) ||
      !isRecord(routePayload.data) ||
      !isRecord(routePayload.data.routeSummary) ||
      typeof routePayload.data.routerAddress !== 'string' ||
      !ADDRESS_PATTERN.test(routePayload.data.routerAddress)
    ) {
      return jsonResponse(502, 'KyberSwap returned an invalid same-chain route.');
    }

    const buildResponse = await fetch(
      `https://aggregator-api.kyberswap.com/${chain}/api/v1/route/build`,
      {
        method: 'POST',
        headers: { ...headers, 'content-type': 'application/json' },
        body: JSON.stringify({
          routeSummary: routePayload.data.routeSummary,
          sender: address,
          recipient: address,
          slippageTolerance: 50,
        }),
        cache: 'no-store',
      },
    );
    if (!buildResponse.ok) {
      return jsonResponse(buildResponse.status, await getResponseError(buildResponse));
    }
    const buildPayload: unknown = await buildResponse.json();
    if (
      !isRecord(buildPayload) ||
      !isRecord(buildPayload.data) ||
      typeof buildPayload.data.data !== 'string' ||
      !/^0x(?:[a-fA-F0-9]{2})*$/.test(buildPayload.data.data) ||
      (typeof buildPayload.data.routerAddress === 'string' &&
        !ADDRESS_PATTERN.test(buildPayload.data.routerAddress))
    ) {
      return jsonResponse(502, 'KyberSwap returned invalid same-chain transaction data.');
    }
    const routerAddress =
      typeof buildPayload.data.routerAddress === 'string'
        ? buildPayload.data.routerAddress
        : routePayload.data.routerAddress;

    const amountOut = routePayload.data.routeSummary.amountOut;
    if (typeof amountOut !== 'string' || !/^[0-9]+$/.test(amountOut)) {
      return jsonResponse(502, 'KyberSwap returned an invalid output amount.');
    }

    return NextResponse.json({
      success: true,
      data: {
        expected_output_amount: amountOut,
        routerAddress,
        tx: {
          to: routerAddress,
          data: buildPayload.data.data,
          value: tokenIn.toLowerCase() === NATIVE_TOKEN ? amountIn : '0',
          ...(typeof buildPayload.data.gas === 'string' ? { gas: buildPayload.data.gas } : {}),
        },
      },
    });
  } catch (error) {
    console.error('KyberSwap same-chain route request failed:', error);
    return jsonResponse(502, 'Could not reach KyberSwap. Please try again.');
  }
}
