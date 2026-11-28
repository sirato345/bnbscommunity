'use client';

import dynamic from 'next/dynamic';
import { usePathname } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronDown, Copy, ExternalLink, Power } from 'lucide-react';
import { getConnectorIcon, useWalletMenu } from '@lifi/wallet-management';
import type { TxData, WidgetProps } from '@kyberswap/widgets';
import { useAccount as useWagmiAccount, useDisconnect, useSendTransaction, useSwitchChain } from 'wagmi';
import { fetchKyberTokenList, type KyberToken } from './token-data';
import { KyberWalletProviders } from './wallet-providers';
import styles from './page.module.css';

const KyberWidget = dynamic(
  () => import('@kyberswap/widgets').then((module) => module.Widget),
  { ssr: false, loading: () => <div className={styles.loading}>Loading swap widget...</div> },
);

const BNB_CHAIN_ID = 56;
const BSC_USDT_ADDRESS = '0x55d398326f99059ff775485246999027b3197955';
const BNBS_TOKEN_ADDRESS = '0xc07ef1c7af6112c34a110809c6c8efb343e63a64';

const NETWORKS = [
  { chainId: 1, name: 'Ethereum' },
  { chainId: 56, name: 'BNB Chain' },
  { chainId: 42161, name: 'Arbitrum' },
  { chainId: 10, name: 'Optimism' },
  { chainId: 137, name: 'Polygon' },
  { chainId: 43114, name: 'Avalanche' },
  { chainId: 8453, name: 'Base' },
  { chainId: 59144, name: 'Linea' },
  { chainId: 1101, name: 'Polygon zkEVM' },
  { chainId: 324, name: 'zkSync Era' },
  { chainId: 81457, name: 'Blast' },
  { chainId: 5000, name: 'Mantle' },
  { chainId: 250, name: 'Fantom' },
  { chainId: 80094, name: 'Berachain' },
  { chainId: 146, name: 'Sonic' },
  { chainId: 999, name: 'HyperEVM' },
  { chainId: 9745, name: 'Plasma' },
  { chainId: 42793, name: 'Etherlink' },
  { chainId: 143, name: 'Monad' },
  { chainId: 4326, name: 'MegaETH' },
  { chainId: 4153, name: 'Rise' },
  { chainId: 4663, name: 'Robinhood' },
  { chainId: 5042, name: 'Arc' },
  { chainId: 99901, name: 'Elysium' },
] as const;

const EXPLORERS: Record<number, string> = {
  1: 'https://etherscan.io/address/',
  56: 'https://bscscan.com/address/',
  42161: 'https://arbiscan.io/address/',
  10: 'https://optimistic.etherscan.io/address/',
  137: 'https://polygonscan.com/address/',
  43114: 'https://snowtrace.io/address/',
  8453: 'https://basescan.org/address/',
  59144: 'https://lineascan.build/address/',
  1101: 'https://zkevm.polygonscan.com/address/',
  324: 'https://explorer.zksync.io/address/',
  81457: 'https://blastscan.io/address/',
  5000: 'https://mantlescan.xyz/address/',
  250: 'https://ftmscan.com/address/',
  80094: 'https://berascan.com/address/',
  146: 'https://sonicscan.org/address/',
  999: 'https://hyperevmscan.io/address/',
  9745: 'https://plasmascan.to/address/',
  42793: 'https://explorer.etherlink.com/address/',
};

const BNBS_TOKEN: KyberToken = {
  chainId: BNB_CHAIN_ID,
  address: BNBS_TOKEN_ADDRESS,
  symbol: 'BNBs',
  name: 'BNBs Inscription',
  decimals: 18,
  logoURI: '/BNBs.svg',
};

const theme: NonNullable<WidgetProps['theme']> = {
  text: '#202431',
  subText: '#72798A',
  primary: '#FFFFFF',
  dialog: '#FFFFFF',
  secondary: '#F7F8FB',
  interactive: '#F1F3F8',
  stroke: '#E5E8EF',
  accent: '#5B6FF5',
  success: '#178A68',
  warning: '#C07A18',
  error: '#D94A54',
  fontFamily: 'Arial, sans-serif',
  borderRadius: '20px',
  buttonRadius: '20px',
  boxShadow: '0 4px 16px rgba(31, 41, 55, 0.06)',
};

function getWalletError(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (
    typeof error === 'object' &&
    error !== null &&
    'message' in error &&
    typeof error.message === 'string'
  ) {
    return error.message;
  }
  return 'Wallet request failed. Please try again.';
}

function getErrorCode(error: unknown): number | undefined {
  if (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    typeof error.code === 'number'
  ) {
    return error.code;
  }
  return undefined;
}

function KyberSwapContent() {
  const [walletError, setWalletError] = useState('');
  const [tokenError, setTokenError] = useState('');
  const [tokenList, setTokenList] = useState<KyberToken[]>([]);
  const [tokensLoading, setTokensLoading] = useState(true);
  const [reloadTokenList, setReloadTokenList] = useState(0);
  const [selectedChainId, setSelectedChainId] = useState(BNB_CHAIN_ID);
  const [widgetWidth, setWidgetWidth] = useState(375);
  const [walletMenuOpen, setWalletMenuOpen] = useState(false);
  const [addressCopied, setAddressCopied] = useState(false);
  const widthRef = useRef<HTMLDivElement>(null);
  const walletAreaRef = useRef<HTMLDivElement>(null);
  const account = useWagmiAccount();
  const { disconnectAsync } = useDisconnect();
  const { sendTransactionAsync } = useSendTransaction();
  const { switchChainAsync } = useSwitchChain();
  const { closeWalletMenu, openWalletMenu } = useWalletMenu();
  const pathname = usePathname();
  const wallet = {
    address: account.isConnected ? account.address : undefined,
    chainId: account.chainId ?? BNB_CHAIN_ID,
  };
  const selectedWalletName = account.connector?.name;
  const selectedWalletIcon = account.connector
    ? getConnectorIcon(account.connector)
    : undefined;
  const selectedNetwork = NETWORKS.find((network) => network.chainId === selectedChainId) ?? NETWORKS[0];

  useEffect(() => {
    if (pathname !== '/kyberswap') {
      closeWalletMenu();
      setWalletMenuOpen(false);
    }
  }, [closeWalletMenu, pathname]);

  useEffect(() => {
    const controller = new AbortController();
    setTokensLoading(true);
    setTokenError('');
    setTokenList([]);

    void fetchKyberTokenList(selectedChainId, controller.signal)
      .then((tokens) => {
        setTokenList(
          selectedChainId === BNB_CHAIN_ID
            ? [
                BNBS_TOKEN,
                ...tokens.filter((token) => token.address.toLowerCase() !== BNBS_TOKEN_ADDRESS),
              ]
            : tokens,
        );
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setTokenError(getWalletError(error));
      })
      .finally(() => {
        if (!controller.signal.aborted) setTokensLoading(false);
      });

    return () => controller.abort();
  }, [reloadTokenList, selectedChainId]);

  useEffect(() => {
    const element = widthRef.current;
    if (!element) return;

    const resizeObserver = new ResizeObserver(([entry]) => {
      setWidgetWidth(Math.max(300, Math.min(375, Math.floor(entry.contentRect.width))));
    });
    resizeObserver.observe(element);
    return () => resizeObserver.disconnect();
  }, []);

  useEffect(() => {
    if (!walletMenuOpen) return;

    const closeOnOutsideClick = (event: PointerEvent) => {
      if (event.target instanceof Node && !walletAreaRef.current?.contains(event.target)) {
        setWalletMenuOpen(false);
      }
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setWalletMenuOpen(false);
    };

    document.addEventListener('pointerdown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [walletMenuOpen]);

  const switchToSelectedChain = useCallback(async () => {
    if (!account.isConnected) {
      setWalletError('No EVM wallet found. Install or enable a browser wallet to continue.');
      return;
    }

    try {
      await switchChainAsync({ chainId: selectedChainId });
      setWalletError('');
    } catch (error) {
      setWalletError(
        getErrorCode(error) === 4902
          ? `${selectedNetwork.name} is not added to your wallet. Add this network to your wallet, then try again.`
          : getWalletError(error),
      );
    }
  }, [account.isConnected, selectedChainId, selectedNetwork.name, switchChainAsync]);

  const disconnectWallet = useCallback(async () => {
    setWalletMenuOpen(false);
    try {
      await disconnectAsync();
      setWalletError('');
    } catch (error) {
      setWalletError(getWalletError(error));
    }
  }, [disconnectAsync]);

  const copyWalletAddress = useCallback(async () => {
    if (!wallet.address) return;
    try {
      await navigator.clipboard.writeText(wallet.address);
      setAddressCopied(true);
      window.setTimeout(() => setAddressCopied(false), 1500);
    } catch (error) {
      setWalletError(getWalletError(error));
    }
  }, [wallet.address]);

  const submitTransaction = useCallback(
    async (transaction: TxData) => {
      if (!account.isConnected || !wallet.address) {
        throw new Error('Connect a wallet before submitting a swap.');
      }
      if (wallet.chainId !== selectedChainId) {
        throw new Error(`Switch your wallet to ${selectedNetwork.name} before submitting a swap.`);
      }

      return sendTransactionAsync({
        account: wallet.address,
        chainId: selectedChainId,
        to: transaction.to as `0x${string}`,
        value: BigInt(transaction.value || '0'),
        data: transaction.data as `0x${string}`,
        gas: transaction.gasLimit ? BigInt(transaction.gasLimit) : undefined,
      });
    },
    [account.isConnected, selectedChainId, selectedNetwork.name, sendTransactionAsync, wallet.address, wallet.chainId],
  );

  const walletButtonLabel = wallet.address ? `Switch to ${selectedNetwork.name}` : 'Connect wallet';

  return (
    <div className={styles.page}>
      <section className={styles.content}>
        <div className={styles.walletRow}>
          <div className={styles.networkControl}>
            <label className={styles.networkPicker}>
              <span>Network</span>
              <select
                aria-label="Select blockchain"
                onChange={(event) => {
                  setSelectedChainId(Number(event.target.value));
                  setWalletError('');
                }}
                value={selectedChainId}
              >
                {NETWORKS.map((network) => (
                  <option key={network.chainId} value={network.chainId}>
                    {network.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className={styles.walletArea} ref={walletAreaRef}>
            {wallet.address ? (
              <>
                <button
                  aria-expanded={walletMenuOpen}
                  aria-haspopup="dialog"
                  className={styles.accountButton}
                  onClick={() => setWalletMenuOpen((open) => !open)}
                  type="button"
                >
                  {selectedWalletIcon ? (
                    <img className={styles.walletIcon} src={selectedWalletIcon} alt="" />
                  ) : (
                    <span className={styles.walletIconFallback}>
                      {selectedWalletName?.slice(0, 1) ?? 'W'}
                    </span>
                  )}
                  <span>{wallet.address.slice(0, 6)}...{wallet.address.slice(-5)}</span>
                  <ChevronDown aria-hidden="true" className={styles.chevron} size={18} strokeWidth={2.2} />
                </button>
                {walletMenuOpen && (
                  <div className={styles.accountMenu} role="dialog" aria-label="Connected wallet">
                    <div className={styles.accountDetails}>
                      {selectedWalletIcon ? (
                        <img className={styles.walletIconLarge} src={selectedWalletIcon} alt="" />
                      ) : (
                        <span className={styles.walletIconLargeFallback}>
                          {selectedWalletName?.slice(0, 1) ?? 'W'}
                        </span>
                      )}
                      <span className={styles.accountMenuAddress}>
                        {wallet.address.slice(0, 6)}...{wallet.address.slice(-5)}
                      </span>
                      <div className={styles.accountActions}>
                        <button
                          aria-label={addressCopied ? 'Address copied' : 'Copy address'}
                          className={styles.accountAction}
                          onClick={() => void copyWalletAddress()}
                          title={addressCopied ? 'Copied' : 'Copy address'}
                          type="button"
                        >
                          <Copy aria-hidden="true" size={23} strokeWidth={2.2} />
                        </button>
                        {EXPLORERS[selectedChainId] && (
                          <a
                            aria-label="View address in block explorer"
                            className={styles.accountAction}
                            href={`${EXPLORERS[selectedChainId]}${wallet.address}`}
                            rel="noopener noreferrer"
                            target="_blank"
                            title="View in block explorer"
                          >
                            <ExternalLink aria-hidden="true" size={23} strokeWidth={2.2} />
                          </a>
                        )}
                        <button
                          aria-label="Disconnect wallet"
                          className={styles.accountAction}
                          onClick={() => void disconnectWallet()}
                          title="Disconnect"
                          type="button"
                        >
                          <Power aria-hidden="true" size={24} strokeWidth={2.2} />
                        </button>
                      </div>
                    </div>
                    {wallet.chainId !== selectedChainId && (
                      <button
                        className={styles.switchNetworkButton}
                        onClick={() => void switchToSelectedChain()}
                        type="button"
                      >
                        Switch to {selectedNetwork.name}
                      </button>
                    )}
                    <button
                      className={styles.connectAnotherButton}
                      onClick={() => {
                        setWalletMenuOpen(false);
                        openWalletMenu();
                      }}
                      type="button"
                    >
                      <Power aria-hidden="true" size={24} strokeWidth={2.4} />
                      Connect another wallet
                    </button>
                  </div>
                )}
              </>
            ) : (
              <button
                className={styles.walletButton}
                onClick={() => {
                  setWalletError('');
                  openWalletMenu();
                }}
                type="button"
              >
                {walletButtonLabel}
              </button>
            )}
          </div>
        </div>

        {walletError && (
          <p className={styles.error} role="alert">
            {walletError}
          </p>
        )}

        <div className={styles.featuredToken}>
          <img src="/BNBs.svg" alt="" />
          <span>Only same-chain swaps are supported</span>
        </div>

        <div className={styles.widgetCard} ref={widthRef}>
          {tokensLoading ? (
            <div className={styles.loading}>Loading network tokens...</div>
          ) : tokenError ? (
            <div className={styles.tokenError} role="alert">
              <p>{tokenError}</p>
              <button onClick={() => setReloadTokenList((current) => current + 1)} type="button">
                Retry
              </button>
            </div>
          ) : (
            <KyberWidget
              key={selectedChainId}
              client="BNBs"
              chainId={selectedChainId}
              connectedAccount={{ address: wallet.address, chainId: wallet.chainId }}
              onSubmitTx={submitTransaction}
              onSwitchChain={switchToSelectedChain}
              onError={(error) => setWalletError(getWalletError(error))}
              tokenList={tokenList}
              {...(selectedChainId === BNB_CHAIN_ID
                ? { defaultTokenIn: BSC_USDT_ADDRESS, defaultTokenOut: BNBS_TOKEN_ADDRESS }
                : {})}
              theme={theme}
              width={widgetWidth}
            />
          )}
        </div>

        <p className={styles.disclaimer}>
          Transactions are signed in your connected wallet. Always verify token details and
          transaction information before confirming.
        </p>
      </section>
    </div>
  );
}

export default function KyberSwapPage() {
  return (
    <KyberWalletProviders>
      <KyberSwapContent />
    </KyberWalletProviders>
  );
}
