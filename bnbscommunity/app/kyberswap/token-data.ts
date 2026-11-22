import type { WidgetProps } from '@kyberswap/widgets';

export type KyberToken = NonNullable<WidgetProps['tokenList']>[number];

const TOKEN_LIST_URL = 'https://ks-setting.kyberswap.com/api/v1/tokens';
const PAGE_SIZE = 100;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function parseToken(value: unknown): KyberToken | undefined {
  if (
    !isRecord(value) ||
    typeof value.chainId !== 'number' ||
    typeof value.address !== 'string' ||
    typeof value.symbol !== 'string' ||
    typeof value.name !== 'string' ||
    typeof value.decimals !== 'number' ||
    typeof value.logoURI !== 'string'
  ) {
    return undefined;
  }

  return {
    chainId: value.chainId,
    address: value.address,
    symbol: value.symbol,
    name: value.name,
    decimals: value.decimals,
    logoURI: value.logoURI.trim() || '/token-placeholder.svg',
  };
}

async function fetchTokenPage(chainId: number, page: number, signal: AbortSignal) {
  const url = new URL(TOKEN_LIST_URL);
  url.search = new URLSearchParams({
    pageSize: String(PAGE_SIZE),
    isWhitelisted: 'true',
    chainIds: String(chainId),
    page: String(page),
  }).toString();

  const response = await fetch(url, { signal });
  if (!response.ok) {
    throw new Error(`KyberSwap token list request failed (${response.status}).`);
  }

  const result: unknown = await response.json();
  if (
    !isRecord(result) ||
    !isRecord(result.data) ||
    !Array.isArray(result.data.tokens) ||
    !isRecord(result.data.pagination) ||
    typeof result.data.pagination.totalItems !== 'number'
  ) {
    throw new Error('KyberSwap returned an invalid token list response.');
  }

  const tokens = result.data.tokens.map(parseToken);
  if (tokens.some((token) => !token)) {
    throw new Error('KyberSwap returned invalid token information.');
  }

  return {
    tokens: tokens.filter((token): token is KyberToken => token !== undefined),
    totalItems: result.data.pagination.totalItems,
  };
}

export async function fetchKyberTokenList(
  chainId: number,
  signal: AbortSignal,
): Promise<KyberToken[]> {
  const firstPage = await fetchTokenPage(chainId, 1, signal);
  const pageCount = Math.ceil(firstPage.totalItems / PAGE_SIZE);
  const rest = await Promise.all(
    Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) =>
      fetchTokenPage(chainId, index + 2, signal),
    ),
  );
  return [firstPage.tokens, ...rest.map((page) => page.tokens)].flat();
}
