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

const helper = `const prioritizeFeaturedTokens = (tokens, configTokens, displayedTokens = tokens) => {
    const key = (token) => \`\${token.chainId}-\${token.address.toLowerCase()}\`;
    const featuredKeys = new Set((configTokens?.featured ?? []).map(key));
    const included = new Map((configTokens?.include ?? []).filter((token) => featuredKeys.has(key(token))).map((token) => [key(token), token]));
    const isCustom = (token) => {
        const configured = included.get(key(token));
        return configured && token.name === configured.name && token.symbol === configured.symbol && token.logoURI === configured.logoURI;
    };
    for (const token of displayedTokens ?? []) {
        if (!isCustom(token) || tokens.some((item) => key(item) === key(token) && isCustom(item))) {
            continue;
        }
        const balanceToken = tokens.find((item) => key(item) === key(token) && item.amount);
        if (balanceToken) {
            tokens.push({ ...token, amount: balanceToken.amount, blockNumber: balanceToken.blockNumber });
        }
    }
    const priority = (token) => featuredKeys.has(key(token)) ? (isCustom(token) ? 2 : 1) : 0;
    return tokens.sort((a, b) => priority(b) - priority(a));
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

await writeFile(tokenListPath, source)
