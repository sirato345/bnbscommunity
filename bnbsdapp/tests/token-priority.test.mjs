import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { processTokenBalances, shouldShowTokenWarning } from '../node_modules/@lifi/widget/dist/esm/utils/tokenList.js'

const custom = {
  chainId: 56,
  address: '0xc07ef1c7af6112c34a110809c6c8efb343e63a64',
  symbol: 'BNBs',
  name: 'BNBs Inscription',
  decimals: 18,
  logoURI: 'BNBs.svg',
  priceUSD: '1',
}
const api = {
  ...custom,
  address: '0xC07EF1C7AF6112C34A110809C6C8EFB343E63A64',
  name: 'BNBs',
  logoURI: undefined,
  volumeUSD24H: 100,
}
const other = {
  ...api,
  address: '0x1111111111111111111111111111111111111111',
  symbol: 'OTHER',
  name: 'Other token',
  volumeUSD24H: 1000,
}
const config = { featured: [custom], include: [custom] }

for (const loading of [true, false]) {
  for (const chainId of [56, undefined]) {
    test(`custom BNBs is unique and first while loading=${loading} chain=${chainId ?? 'all'}`, () => {
      const result = processTokenBalances(loading, true, config, chainId, [
        api,
        other,
        custom,
      ], [])
      assert.equal(result.processedTokens[0].name, custom.name)
      assert.equal(result.processedTokens[0].logoURI, custom.logoURI)
      assert.equal(result.processedTokens.length, 2)
      assert.equal(result.processedTokens[1].address, other.address)
    })
  }
}

test('custom BNBs is unique and retains the API duplicate wallet balance', () => {
  const result = processTokenBalances(false, true, config, 56, [
    api,
    other,
    custom,
  ], [{ ...api, amount: 10n ** 18n, blockNumber: 123n }])
  assert.equal(result.processedTokens[0].name, custom.name)
  assert.equal(result.processedTokens[0].logoURI, custom.logoURI)
  assert.equal(result.processedTokens[0].amount, 10n ** 18n)
  assert.equal(result.processedTokens[0].blockNumber, 123n)
  assert.equal(result.processedTokens.length, 2)
})

test('a custom token that does not match the search is not added', () => {
  const result = processTokenBalances(false, true, config, 56, [
    api,
    other,
  ], [{ ...api, amount: 10n ** 18n }])
  assert.equal(result.processedTokens[0].name, api.name)
  assert.equal(result.processedTokens.length, 2)
})

test('other chains and addresses do not get BNBs priority', () => {
  const result = processTokenBalances(true, true, config, undefined, [
    { ...custom, chainId: 1, symbol: 'NOTBNB', name: 'Other chain token', volumeUSD24H: 1000 },
    other,
    custom,
  ], [])
  assert.equal(result.processedTokens[0].chainId, 56)
  assert.equal(result.processedTokens[0].name, custom.name)
  assert.equal(result.processedTokens.length, 3)
})

for (const loading of [true, false]) {
  test(`categorized list does not duplicate featured BNBs while loading=${loading}`, () => {
    const result = processTokenBalances(loading, false, config, 56, [
      api,
      custom,
      other,
    ], [{ ...api, amount: 123n, blockNumber: 456n }])
    assert.equal(result.processedTokens[0].name, custom.name)
    assert.equal(result.processedTokens[0].logoURI, custom.logoURI)
    assert.equal(result.processedTokens.length, 2)
    if (!loading) {
      assert.equal(result.processedTokens[0].amount, 123n)
      assert.equal(result.processedTokens[0].blockNumber, 456n)
    }
  })
}

test('case-sensitive non-EVM addresses remain distinct', () => {
  const first = { ...other, chainId: 1151111081099710, address: 'AbCd123' }
  const second = { ...first, address: 'aBcD123' }
  const result = processTokenBalances(true, true, config, undefined, [first, second], [])
  assert.equal(result.processedTokens.length, 2)
})

test('only the configured BNBs contract suppresses the unverified warning', () => {
  assert.equal(shouldShowTokenWarning({ ...api, verified: false }), false)
  assert.equal(shouldShowTokenWarning({ ...custom, verified: false }), false)
  assert.equal(shouldShowTokenWarning({ ...other, verified: false }), true)
  assert.equal(shouldShowTokenWarning({ ...custom, chainId: 1, verified: false }), true)
  assert.equal(shouldShowTokenWarning({ ...other, verified: true }), false)
})

test('the token list uses the warning visibility check', async () => {
  const component = await readFile(
    new URL('../node_modules/@lifi/widget/dist/esm/components/TokenList/TokenListItem.js', import.meta.url),
    'utf8'
  )
  assert.match(component, /shouldShowTokenWarning\(token\) && \(_jsx\(Tooltip,/)
})

for (const loading of [true, false]) {
  for (const noCategories of [true, false]) {
    test(`filters other bnbs names and symbols with loading=${loading} categories=${!noCategories}`, () => {
      const byName = { ...other, address: '0x2222222222222222222222222222222222222222', name: 'BNBShare' }
      const bySymbol = { ...other, address: '0x3333333333333333333333333333333333333333', symbol: 'xBnBs' }
      const otherChain = { ...custom, chainId: 1 }
      const balanceOnly = { ...other, address: '0x4444444444444444444444444444444444444444', symbol: 'BNBS', amount: 10n }
      const result = processTokenBalances(loading, noCategories, config, 56, [
        api, custom, other, byName, bySymbol, otherChain,
      ], [{ ...api, amount: 1n }, balanceOnly])
      assert.equal(result.processedTokens.length, 2)
      assert.equal(result.processedTokens[0].address, custom.address)
      assert.equal(result.processedTokens[0].name, custom.name)
      assert.equal(result.processedTokens[1].address, other.address)
      if (!loading) assert.equal(result.processedTokens[0].amount, 1n)
    })
  }
}
