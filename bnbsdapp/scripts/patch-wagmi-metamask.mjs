import { readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const connectorPath = resolve('node_modules/@wagmi/connectors/dist/esm/metaMask.js')
const packagePath = resolve('node_modules/@wagmi/connectors/package.json')
const { version } = JSON.parse(await readFile(packagePath, 'utf8'))

if (version !== '6.2.0') {
  throw new Error(
    `Expected @wagmi/connectors 6.2.0, found ${version}. Review the MetaMask chain-sync patch before upgrading.`
  )
}

let source = await readFile(connectorPath, 'utf8')

// The MetaMask SDK caches eth_chainId and only refreshes it after the user has approved
// the switch in the MetaMask app. The stock 20 x 50ms retry (about 1s) fails before the
// user returns from the app, so the widget reports "User rejected switch after adding network."
const original = /delay: 50,\s*retryCount: 20, \/\/ android device encryption is slower/
const patched = 'delay: 500,\n                    retryCount: 120, // wait up to 60s for the user to approve in the MetaMask app'

if (!source.includes('retryCount: 120,')) {
  if (!original.test(source)) {
    throw new Error('Could not locate the MetaMask chain-sync retry in @wagmi/connectors 6.2.0.')
  }
  source = source.replace(original, patched)
  await writeFile(connectorPath, source)
}
