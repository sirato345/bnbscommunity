import { useSyncWagmiConfig } from '@lifi/wallet-management'
import { useAvailableChains } from '@lifi/widget'
import { injected, walletConnect } from '@wagmi/connectors'
import { type FC, type PropsWithChildren } from 'react'
import { createClient, http } from 'viem'
import { mainnet } from 'viem/chains'
import { createConfig, WagmiProvider } from 'wagmi'

// 去https://dashboard.reown.com/注册的ID
const projectId = import.meta.env.VITE_WALLET_CONNECT_PROJECT_ID

const connectors = [injected(), walletConnect({ projectId })]

export const WalletProvider: FC<PropsWithChildren> = ({ children }) => {
    const { chains } = useAvailableChains()

    const getConfig = () => {
        const config = createConfig({
            chains: [mainnet],
            client({ chain }) {
                return createClient({ chain, transport: http() })
            },
            ssr: true,
        })
        return config;
    };

    useSyncWagmiConfig(getConfig(), connectors, chains)

    return (
        <WagmiProvider config={getConfig()} reconnectOnMount={false}>
            {children}
        </WagmiProvider>
    )
}