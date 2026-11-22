'use client';

import { createConfig, type Config as BigmiConfig } from '@bigmi/client';
import { bitcoin, createClient, http } from '@bigmi/core';
import { BigmiProvider } from '@bigmi/react';
import { createNetworkConfig, SuiClientProvider, WalletProvider as SuiWalletProvider } from '@mysten/dapp-kit';
import { getFullnodeUrl } from '@mysten/sui/client';
import { ChainType } from '@lifi/sdk';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import {
  createDefaultWagmiConfig,
  useSyncWagmiConfig,
  WalletManagementProvider,
} from '@lifi/wallet-management';
import { ConnectionProvider, WalletProvider as SolanaWalletProvider } from '@solana/wallet-adapter-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useMemo, useState, type ReactNode } from 'react';
import {
  arbitrum,
  arc,
  avalanche,
  base,
  berachain,
  blast,
  bsc,
  etherlink,
  fantom,
  hyperEvm,
  kinetiqElysium,
  linea,
  mainnet,
  mantle,
  megaeth,
  monad,
  optimism,
  plasma,
  polygon,
  polygonZkEvm,
  rise,
  robinhood,
  sonic,
  zkSync,
} from 'viem/chains';
import { WagmiProvider } from 'wagmi';

const muiTheme = createTheme({
  cssVariables: true,
  shape: { borderRadius: 12 },
  components: {
    MuiCard: {
      styleOverrides: {
        root: {
          border: '1px solid #e0e0e0',
          boxShadow: 'none',
          cursor: 'pointer',
        },
      },
    },
  },
});
const { networkConfig } = createNetworkConfig({
  mainnet: { url: getFullnodeUrl('mainnet') },
});
const wagmiChains = [
  mainnet, bsc, arbitrum, optimism, polygon, avalanche, base, linea, polygonZkEvm,
  zkSync, blast, mantle, fantom, berachain, sonic, hyperEvm, plasma, etherlink,
  monad, megaeth, rise, robinhood, arc, kinetiqElysium,
];

function WagmiWalletConfig({ children }: { children: ReactNode }) {
  const projectId = process.env.NEXT_PUBLIC_WALLET_CONNECT_PROJECT_ID;
  const walletConnect = useMemo(
    () => (projectId ? { projectId } : undefined),
    [projectId],
  );
  const wagmi = useMemo(
    () =>
      createDefaultWagmiConfig({
        ...(walletConnect ? { walletConnect } : {}),
        wagmiConfig: { ssr: true },
      }),
    [walletConnect],
  );
  const walletConfig = useMemo(
    () => ({
      enabledChainTypes: [ChainType.EVM],
      ...(walletConnect ? { walletConnect } : {}),
    }),
    [walletConnect],
  );

  useSyncWagmiConfig(wagmi.config, wagmi.connectors, wagmiChains);

  return (
    <WagmiProvider config={wagmi.config} reconnectOnMount={false}>
      <ThemeProvider theme={muiTheme}>
        <WalletManagementProvider config={walletConfig}>
          {children}
        </WalletManagementProvider>
      </ThemeProvider>
    </WagmiProvider>
  );
}

export function KyberWalletProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => new QueryClient());
  const bigmiConfig = useMemo(
    () => createConfig({
      chains: [bitcoin],
      connectors: [],
      client({ chain }) {
        return createClient({ chain, transport: http() });
      },
      ssr: true,
    }) as BigmiConfig,
    [],
  );

  return (
    <QueryClientProvider client={queryClient}>
      <SuiClientProvider networks={networkConfig} defaultNetwork="mainnet">
        <SuiWalletProvider autoConnect={false}>
          <BigmiProvider config={bigmiConfig} reconnectOnMount={false}>
            <ConnectionProvider endpoint="https://api.mainnet-beta.solana.com">
              <SolanaWalletProvider wallets={[]} autoConnect={false}>
                <WagmiWalletConfig>{children}</WagmiWalletConfig>
              </SolanaWalletProvider>
            </ConnectionProvider>
          </BigmiProvider>
        </SuiWalletProvider>
      </SuiClientProvider>
    </QueryClientProvider>
  );
}
