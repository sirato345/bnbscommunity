import { readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const widgetPackagePath = resolve('node_modules/@lifi/widget/package.json')
const widgetPackage = JSON.parse(await readFile(widgetPackagePath, 'utf8'))

if (widgetPackage.version !== '3.38.1') {
  throw new Error(
    `Expected @lifi/widget 3.38.1, found ${widgetPackage.version}. Review the token-list patch before upgrading.`
  )
}

const tokenListPath = resolve(
  'node_modules/@lifi/widget/dist/esm/utils/tokenList.js'
)
const tokenListItemPath = resolve(
  'node_modules/@lifi/widget/dist/esm/components/TokenList/TokenListItem.js'
)
let source = await readFile(tokenListPath, 'utf8')

const replaceLine = (label, originalPattern, patchedPattern, replacement) => {
  if (patchedPattern.test(source)) {
    return
  }

  const matches = source.match(originalPattern)
  if (!matches || matches.length !== 1) {
    throw new Error(
      `Could not uniquely locate ${label} in @lifi/widget ${widgetPackage.version}. Review the token-list patch.`
    )
  }

  source = source.replace(originalPattern, replacement)
}

const helper = `const prioritizeFeaturedTokens = (tokens, configTokens, displayedTokens = tokens, prioritize = true) => {
    const key = (token) => \`\${token.chainId}-\${/^0x[0-9a-f]{40}$/i.test(token.address) ? token.address.toLowerCase() : token.address}\`;
    const featuredKeys = new Set((configTokens?.featured ?? []).map(key));
    const included = new Map((configTokens?.include ?? []).filter((token) => featuredKeys.has(key(token))).map((token) => [key(token), token]));
    const isCustom = (token) => {
        const configured = included.get(key(token));
        return configured && token.name === configured.name && token.symbol === configured.symbol && token.logoURI === configured.logoURI;
    };
    const customTokens = new Map((displayedTokens ?? []).filter(isCustom).map((token) => [key(token), token]));
    const unique = new Map();
    for (const token of tokens) {
        const tokenKey = key(token);
        const existing = unique.get(tokenKey);
        if (!existing) {
            unique.set(tokenKey, { ...token });
        } else {
            if (existing.amount == null && token.amount != null) {
                existing.amount = token.amount;
                existing.blockNumber = token.blockNumber;
            }
            if (existing.priceUSD == null && token.priceUSD != null) {
                existing.priceUSD = token.priceUSD;
            }
        }
    }
    for (const [tokenKey, token] of unique) {
        const custom = customTokens.get(tokenKey);
        if (custom) {
            unique.set(tokenKey, {
                ...token,
                ...custom,
                amount: token.amount,
                blockNumber: token.blockNumber,
            });
        }
    }
    const result = [...unique.values()];
    return prioritize ? result.sort((a, b) => Number(featuredKeys.has(key(b))) - Number(featuredKeys.has(key(a)))) : result;
};
`
const existingHelperPattern =
  /^const prioritizeFeaturedTokens = [^\r\n]*\r?\n[\s\S]*?^};\r?\n/gm
const existingHelpers = source.match(existingHelperPattern)
if (existingHelpers) {
  if (existingHelpers.length !== 1) {
    throw new Error('Could not uniquely locate the existing LI.FI priority helper.')
  }
  source = source.replace(existingHelperPattern, () => helper)
} else {
  const sortByVolumePattern = /^(const sortByVolume = .*;)(\r?\n)/gm
  const sortByVolumeMatches = [...source.matchAll(sortByVolumePattern)]
  if (sortByVolumeMatches.length !== 1) {
    throw new Error(
      `Could not uniquely locate the LI.FI volume sorter in @lifi/widget ${widgetPackage.version}. Review the token-list patch.`
    )
  }

  source = source.replace(sortByVolumePattern, `$1$2${helper}`)
}

replaceLine(
  'the loading-state token sorter',
  /^\s*const sortedTokens = [^\r\n]*\.sort\(sortByVolume\);/gm,
  /^\s*const sortedTokens = prioritizeFeaturedTokens\(/m,
  '            const sortedTokens = prioritizeFeaturedTokens([...(tokens ?? [])].sort(sortByVolume), configTokens);'
)
replaceLine(
  'the final token-list ordering',
  /^[ \t]*processedTokens: (?:\[\.\.\.sortedTokensWithBalances, \.\.\.tokensWithoutBalances\]|prioritizeFeaturedTokens\(\[\.\.\.sortedTokensWithBalances, \.\.\.tokensWithoutBalances\], configTokens\)),$/gm,
  /^[ \t]*processedTokens: prioritizeFeaturedTokens\(\[\.\.\.sortedTokensWithBalances, \.\.\.tokensWithoutBalances\], configTokens, tokens\),$/m,
  '            processedTokens: prioritizeFeaturedTokens([...sortedTokensWithBalances, ...tokensWithoutBalances], configTokens, tokens),'
)

replaceLine(
  'the loading-state categorized token list',
  /^[ \t]*return processedTypedTokens\(tokens \?\? \[\], \[\], selectedChainId, configTokens\);$/gm,
  /^[ \t]*return prioritizeCategorizedTokens\(processedTypedTokens\(tokens \?\? \[\], \[\], selectedChainId, configTokens\), configTokens, tokens\);$/m,
  '            return prioritizeCategorizedTokens(processedTypedTokens(tokens ?? [], [], selectedChainId, configTokens), configTokens, tokens);'
)
replaceLine(
  'the categorized token list with balances',
  /^[ \t]*return processedTypedTokens\(tokensWithoutBalances, sortedTokensWithBalances, selectedChainId, configTokens\);$/gm,
  /^[ \t]*return prioritizeCategorizedTokens\(processedTypedTokens\(tokensWithoutBalances, sortedTokensWithBalances, selectedChainId, configTokens\), configTokens, tokens\);$/m,
  '        return prioritizeCategorizedTokens(processedTypedTokens(tokensWithoutBalances, sortedTokensWithBalances, selectedChainId, configTokens), configTokens, tokens);'
)

if (!/^const prioritizeCategorizedTokens = /m.test(source)) {
  const marker = 'export const processTokenBalances = '
  const index = source.indexOf(marker)
  if (index < 0 || source.indexOf(marker, index + marker.length) >= 0) {
    throw new Error('Could not uniquely locate the LI.FI token-list processor.')
  }
  source = source.slice(0, index) +
    'const prioritizeCategorizedTokens = (result, configTokens, tokens) => ({ ...result, processedTokens: prioritizeFeaturedTokens(result.processedTokens, configTokens, tokens, false) });\n' +
    source.slice(index)
}

const warningHelper = `const isBnbsContract = (token) =>
    token.chainId === 56 && token.address.toLowerCase() === '0xc07ef1c7af6112c34a110809c6c8efb343e63a64';
export const isVisibleToken = (token) =>
    isBnbsContract(token) || ![token.symbol, token.name].some((value) => value?.toLowerCase().includes('bnbs'));
export const shouldShowTokenWarning = (token) =>
    !token.verified && !isBnbsContract(token);
`
if (!/^export const isVisibleToken = /m.test(source)) {
  const oldWarningPattern =
    /^export const shouldShowTokenWarning = \(token\) =>\r?\n[^\r\n]*\r?\n/gm
  const oldWarnings = source.match(oldWarningPattern)
  if (oldWarnings) {
    if (oldWarnings.length !== 1) {
      throw new Error('Could not uniquely locate the LI.FI warning helper.')
    }
    source = source.replace(oldWarningPattern, () => warningHelper)
  } else {
    const marker = 'export const processTokenBalances = '
    const index = source.indexOf(marker)
    if (index < 0 || source.indexOf(marker, index + marker.length) >= 0) {
      throw new Error('Could not uniquely locate the LI.FI token-list processor.')
    }
    source = source.slice(0, index) + warningHelper + source.slice(index)
  }
}

replaceLine(
  'the token-list processor inputs',
  /^export const processTokenBalances = \(isBalanceLoading, noCategories, configTokens, selectedChainId, tokens, tokensWithBalances\) => \{$/gm,
  /^    tokens = tokens\?\.filter\(isVisibleToken\);$/m,
  `export const processTokenBalances = (isBalanceLoading, noCategories, configTokens, selectedChainId, tokens, tokensWithBalances) => {
    tokens = tokens?.filter(isVisibleToken);
    tokensWithBalances = tokensWithBalances?.filter(isVisibleToken);`
)

let itemSource = await readFile(tokenListItemPath, 'utf8')
const warningImport = "import { shouldShowTokenWarning } from '../../utils/tokenList.js';"
if (!itemSource.includes(warningImport)) {
  const importMarker = "import { shortenAddress } from '../../utils/wallet.js';"
  const index = itemSource.indexOf(importMarker)
  if (index < 0 || itemSource.indexOf(importMarker, index + importMarker.length) >= 0) {
    throw new Error('Could not uniquely locate the LI.FI token-list imports.')
  }
  itemSource = itemSource.replace(importMarker, `${warningImport}\n${importMarker}`)
}
const originalWarning = '!token.verified && (_jsx(Tooltip,'
const patchedWarning = 'shouldShowTokenWarning(token) && (_jsx(Tooltip,'
if (!itemSource.includes(patchedWarning)) {
  const index = itemSource.indexOf(originalWarning)
  if (index < 0 || itemSource.indexOf(originalWarning, index + originalWarning.length) >= 0) {
    throw new Error('Could not uniquely locate the LI.FI unverified token warning.')
  }
  itemSource = itemSource.replace(originalWarning, patchedWarning)
}

await writeFile(tokenListPath, source)
await writeFile(tokenListItemPath, itemSource)
