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

const replaceOnce = (original, replacement) => {
  if (source.includes(replacement)) {
    return
  }

  const firstIndex = source.indexOf(original)
  if (firstIndex === -1 || source.indexOf(original, firstIndex + 1) !== -1) {
    throw new Error(
      'Could not uniquely locate the expected LI.FI token-list code. Review the widget patch.'
    )
  }

  source =
    source.slice(0, firstIndex) +
    replacement +
    source.slice(firstIndex + original.length)
}

replaceOnce(
  "const sortByVolume = (a, b) => (b.volumeUSD24H ?? 0) - (a.volumeUSD24H ?? 0);",
  `const sortByVolume = (a, b) => (b.volumeUSD24H ?? 0) - (a.volumeUSD24H ?? 0);
const deduplicateTokens = (tokens, configTokens) => {
    const includedTokens = new Map((configTokens?.include ?? []).map((token) => [\`\${token.chainId}-\${token.address.toLowerCase()}\`, token]));
    const uniqueTokens = new Map();
    for (const token of tokens) {
        const key = \`\${token.chainId}-\${token.address.toLowerCase()}\`;
        const configuredToken = includedTokens.get(key);
        const normalizedToken = configuredToken ? { ...token, ...configuredToken } : token;
        uniqueTokens.set(key, { ...uniqueTokens.get(key), ...normalizedToken });
    }
    return [...uniqueTokens.values()];
};
const prioritizeFeaturedTokens = (tokens, configTokens) => {
    const featuredTokenKeys = new Set((configTokens?.featured ?? []).map((token) => \`\${token.chainId}-\${token.address.toLowerCase()}\`));
    return tokens.sort((a, b) => Number(featuredTokenKeys.has(\`\${b.chainId}-\${b.address.toLowerCase()}\`)) -
        Number(featuredTokenKeys.has(\`\${a.chainId}-\${a.address.toLowerCase()}\`)));
};`
)

replaceOnce(
  'const sortedTokens = [...(tokens ?? [])].sort(sortByVolume);',
  'const sortedTokens = prioritizeFeaturedTokens(deduplicateTokens(tokens ?? [], configTokens).sort(sortByVolume), configTokens);'
)
replaceOnce(
  'const sortedTokensWithBalances = [...(tokensWithBalances ?? [])].sort(sortByBalances);',
  'const sortedTokensWithBalances = deduplicateTokens(tokensWithBalances ?? [], configTokens).sort(sortByBalances);'
)
replaceOnce(
  'const tokensWithoutBalances = tokens',
  'const tokensWithoutBalances = deduplicateTokens(tokens ?? [], configTokens)'
)
replaceOnce(
  'processedTokens: [...sortedTokensWithBalances, ...tokensWithoutBalances],',
  'processedTokens: prioritizeFeaturedTokens([...sortedTokensWithBalances, ...tokensWithoutBalances], configTokens),'
)

await writeFile(tokenListPath, source)
