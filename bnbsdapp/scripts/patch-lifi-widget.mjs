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

if (!/^const prioritizeFeaturedTokens = /m.test(source)) {
  const sortByVolumePattern = /^(const sortByVolume = .*;)(\r?\n)/gm
  const sortByVolumeMatches = [...source.matchAll(sortByVolumePattern)]
  if (sortByVolumeMatches.length !== 1) {
    throw new Error(
      `Could not uniquely locate the LI.FI volume sorter in @lifi/widget ${widgetPackage.version}. Review the token-list patch.`
    )
  }

  const helper = `const prioritizeFeaturedTokens = (tokens, configTokens) => {
    const featuredTokenKeys = new Set((configTokens?.featured ?? []).map((token) => \`\${token.chainId}-\${token.address.toLowerCase()}\`));
    return tokens.sort((a, b) => Number(featuredTokenKeys.has(\`\${b.chainId}-\${b.address.toLowerCase()}\`)) -
        Number(featuredTokenKeys.has(\`\${a.chainId}-\${a.address.toLowerCase()}\`)));
};
`
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
  /^\s*processedTokens: \[\.\.\.sortedTokensWithBalances, \.\.\.tokensWithoutBalances\],$/gm,
  /^\s*processedTokens: prioritizeFeaturedTokens\(/m,
  '            processedTokens: prioritizeFeaturedTokens([...sortedTokensWithBalances, ...tokensWithoutBalances], configTokens),'
)

await writeFile(tokenListPath, source)
