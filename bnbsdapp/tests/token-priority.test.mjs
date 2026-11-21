import assert from 'node:assert/strict'
import { test } from 'node:test'
import { processTokenBalances } from '../node_modules/@lifi/widget/dist/esm/utils/tokenList.js'

const custom = {
  chainId: 56,
  address: '0xc07ef1c7af6112c34a110809c6c8efb343e63a64',
  symbol: 'BNBs',
  name: 'BNBs Token',
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
  volumeUSD24H: 1000,
}
const config = { featured: [custom], include: [custom] }

for (const loading of [true, false]) {
  test(`custom BNBs precedes the API duplicate while loading=${loading}`, () => {
    const result = processTokenBalances(loading, true, config, 56, [
      api,
      other,
      custom,
    ], [])
    assert.equal(result.processedTokens[0].name, custom.name)
    assert.equal(result.processedTokens[0].logoURI, custom.logoURI)
    assert.equal(result.processedTokens[1].name, api.name)
    assert.equal(result.processedTokens.length, 3)
  })
}

test('custom BNBs remains first when its API duplicate has a wallet balance', () => {
  const result = processTokenBalances(false, true, config, 56, [
    api,
    other,
    custom,
  ], [{ ...api, amount: 10n ** 18n }])
  assert.equal(result.processedTokens[0].name, custom.name)
  assert.equal(result.processedTokens[0].logoURI, custom.logoURI)
  assert.equal(result.processedTokens[0].amount, 10n ** 18n)
  assert.equal(result.processedTokens[1].name, api.name)
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
    { ...custom, chainId: 1, volumeUSD24H: 1000 },
    other,
    custom,
  ], [])
  assert.equal(result.processedTokens[0].chainId, 56)
  assert.equal(result.processedTokens[0].name, custom.name)
})
