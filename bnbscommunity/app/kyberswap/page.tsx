'use client';

import dynamic from 'next/dynamic';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronDown, Copy, ExternalLink, Power } from 'lucide-react';
import type { TxData, WidgetProps } from '@kyberswap/widgets';
import { fetchKyberTokenList, type KyberToken } from './token-data';
import styles from './page.module.css';

const KyberWidget = dynamic(
  () => import('@kyberswap/widgets').then((module) => module.Widget),
  { ssr: false, loading: () => <div className={styles.loading}>Loading swap widget...</div> },
);

const BNB_CHAIN_ID = 56;
const BSC_USDT_ADDRESS = '0x55d398326f99059fF775485246999027B3197955';
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

interface Eip1193Provider {
  request: (args: { method: string; params?: unknown[] }) => Promise<unknown>;
  on?: (event: 'accountsChanged' | 'chainChanged', listener: (value: unknown) => void) => void;
  removeListener?: (
    event: 'accountsChanged' | 'chainChanged',
    listener: (value: unknown) => void,
  ) => void;
}

interface WalletOption {
  id: string;
  name: string;
  provider: Eip1193Provider;
  icon?: string;
}

interface Eip6963ProviderDetail {
  info: {
    uuid: string;
    name: string;
    rdns: string;
    icon?: string;
  };
  provider: Eip1193Provider;
}

declare global {
  interface Window {
    ethereum?: Eip1193Provider & { providers?: Eip1193Provider[] };
  }
}

interface WalletState {
  address?: string;
  chainId: number;
}

function getChainId(value: unknown): number | undefined {
  if (typeof value !== 'string' || !/^0x[0-9a-f]+$/i.test(value)) {
    return undefined;
  }
  return Number.parseInt(value, 16);
}

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

function getInjectedWalletName(provider: Eip1193Provider, index: number): string {
  const flags = provider as Eip1193Provider & Record<string, unknown>;
  if (flags.isMetaMask) return 'MetaMask';
  if (flags.isRabby) return 'Rabby';
  if (flags.isCoinbaseWallet) return 'Coinbase Wallet';
  if (flags.isBraveWallet) return 'Brave Wallet';
  if (flags.isTrust) return 'Trust Wallet';
  if (flags.isOkxWallet) return 'OKX Wallet';
  return index === 0 ? 'Browser wallet' : `Browser wallet ${index + 1}`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export default function KyberSwapPage() {
  const [wallet, setWallet] = useState<WalletState>({ chainId: BNB_CHAIN_ID });
  const [walletError, setWalletError] = useState('');
  const [tokenError, setTokenError] = useState('');
  const [tokenList, setTokenList] = useState<KyberToken[]>([]);
  const [tokensLoading, setTokensLoading] = useState(true);
  const [reloadTokenList, setReloadTokenList] = useState(0);
  const [selectedChainId, setSelectedChainId] = useState(BNB_CHAIN_ID);
  const [widgetWidth, setWidgetWidth] = useState(375);
  const [walletOptions, setWalletOptions] = useState<WalletOption[]>([]);
  const [selectedWalletId, setSelectedWalletId] = useState('');
  const [walletMenuOpen, setWalletMenuOpen] = useState(false);
  const [walletModalOpen, setWalletModalOpen] = useState(false);
  const [addressCopied, setAddressCopied] = useState(false);
  const widthRef = useRef<HTMLDivElement>(null);
  const walletAreaRef = useRef<HTMLDivElement>(null);
  const walletConnectedRef = useRef(false);
  const activeProvider = walletOptions.find((option) => option.id === selectedWalletId)?.provider;
  const selectedNetwork = NETWORKS.find((network) => network.chainId === selectedChainId) ?? NETWORKS[0];

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
    const detected = new Map<string, WalletOption>();
    let injectedIndex = 0;

    const publish = () => {
      const options = [...detected.values()];
      setWalletOptions(options);
      setSelectedWalletId((current) => current || options[0]?.id || '');
    };

    const addProvider = (
      id: string,
      name: string,
      provider: Eip1193Provider,
      icon?: string,
    ) => {
      const existing = [...detected.values()].find((option) => option.provider === provider);
      if (existing) {
        if (existing.id.startsWith('injected-') && !id.startsWith('injected-')) {
          detected.set(existing.id, { ...existing, name, icon });
          publish();
        }
        return;
      }
      detected.set(id, { id, name, provider, icon });
      publish();
    };

    const onAnnounceProvider = (event: Event) => {
      const detail = (event as CustomEvent<unknown>).detail;
      if (!isRecord(detail) || !isRecord(detail.info) || !isRecord(detail.provider)) return;
      if (
        typeof detail.info.uuid !== 'string' ||
        typeof detail.info.name !== 'string' ||
        typeof detail.info.rdns !== 'string' ||
        typeof detail.provider.request !== 'function' ||
        (detail.info.icon !== undefined && typeof detail.info.icon !== 'string')
      ) {
        return;
      }

      const providerDetail = detail as unknown as Eip6963ProviderDetail;
      addProvider(
        providerDetail.info.uuid,
        providerDetail.info.name,
        providerDetail.provider,
        providerDetail.info.icon,
      );
    };

    window.addEventListener('eip6963:announceProvider', onAnnounceProvider);
    window.dispatchEvent(new Event('eip6963:requestProvider'));

    const injectedProviders = window.ethereum?.providers ?? (window.ethereum ? [window.ethereum] : []);
    for (const provider of injectedProviders) {
      addProvider(
        `injected-${injectedIndex}`,
        getInjectedWalletName(provider, injectedIndex),
        provider,
      );
      injectedIndex += 1;
    }

    const discoveryTimeout = window.setTimeout(publish, 300);
    return () => {
      window.clearTimeout(discoveryTimeout);
      window.removeEventListener('eip6963:announceProvider', onAnnounceProvider);
    };
  }, []);

  useEffect(() => {
    walletConnectedRef.current = false;
    if (!activeProvider) return;
    let disposed = false;

    const syncChain = async () => {
      const chain = await activeProvider.request({ method: 'eth_chainId' });
      if (!disposed) {
        setWallet((current) => ({ ...current, chainId: getChainId(chain) ?? current.chainId }));
      }
    };

    const onAccountsChanged = (accounts: unknown) => {
      if (!walletConnectedRef.current) return;
      const address =
        Array.isArray(accounts) && typeof accounts[0] === 'string' ? accounts[0] : undefined;
      setWallet((current) => ({ ...current, address }));
      if (!address) walletConnectedRef.current = false;
    };
    const onChainChanged = (chain: unknown) => {
      setWallet((current) => ({ ...current, chainId: getChainId(chain) ?? current.chainId }));
    };

    void syncChain().catch((error: unknown) => setWalletError(getWalletError(error)));
    activeProvider.on?.('accountsChanged', onAccountsChanged);
    activeProvider.on?.('chainChanged', onChainChanged);

    return () => {
      disposed = true;
      activeProvider.removeListener?.('accountsChanged', onAccountsChanged);
      activeProvider.removeListener?.('chainChanged', onChainChanged);
    };
  }, [activeProvider]);

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

  useEffect(() => {
    if (!walletModalOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setWalletModalOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [walletModalOpen]);

  const switchToSelectedChain = useCallback(async () => {
    const provider = activeProvider;
    if (!provider) {
      setWalletError('No EVM wallet found. Install or enable a browser wallet to continue.');
      return;
    }

    try {
      try {
        await provider.request({
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: `0x${selectedChainId.toString(16)}` }],
        });
      } catch (error) {
        if (getErrorCode(error) !== 4902 || selectedChainId !== BNB_CHAIN_ID) throw error;

        await provider.request({
          method: 'wallet_addEthereumChain',
          params: [{
            chainId: '0x38',
            chainName: 'BNB Smart Chain',
            nativeCurrency: { name: 'BNB', symbol: 'BNB', decimals: 18 },
            rpcUrls: ['https://bsc-dataseed.binance.org/'],
            blockExplorerUrls: ['https://bscscan.com'],
          }],
        });
      }

      setWallet((current) => ({ ...current, chainId: selectedChainId }));
      setWalletError('');
    } catch (error) {
      setWalletError(
        getErrorCode(error) === 4902
          ? `${selectedNetwork.name} is not added to your wallet. Add this network to your wallet, then try again.`
          : getWalletError(error),
      );
    }
  }, [activeProvider, selectedChainId, selectedNetwork.name]);

  const connectWallet = useCallback(async (walletOption?: WalletOption) => {
    const option = walletOption ?? walletOptions.find((item) => item.id === selectedWalletId);
    const provider = option?.provider;
    if (!provider) {
      setWalletError('No EVM wallet found. Install or enable a browser wallet to continue.');
      return;
    }

    walletConnectedRef.current = false;
    setSelectedWalletId(option.id);
    setWallet((current) => ({ ...current, address: undefined }));
    setWalletError('');

    try {
      try {
        await provider.request({
          method: 'wallet_requestPermissions',
          params: [{ eth_accounts: {} }],
        });
      } catch (error) {
        const code = getErrorCode(error);
        if (code !== -32601 && code !== 4200) throw error;
      }

      const accounts = await provider.request({ method: 'eth_requestAccounts' });
      if (!Array.isArray(accounts) || typeof accounts[0] !== 'string') {
        throw new Error('The wallet did not return an account.');
      }
      const chain = await provider.request({ method: 'eth_chainId' });
      walletConnectedRef.current = true;
      if (option) setSelectedWalletId(option.id);
      setWallet({ address: accounts[0], chainId: getChainId(chain) ?? BNB_CHAIN_ID });
      setWalletError('');
      setWalletMenuOpen(false);
      setWalletModalOpen(false);
    } catch (error) {
      setWalletError(getWalletError(error));
    }
  }, [selectedWalletId, walletOptions]);

  const disconnectWallet = useCallback(() => {
    walletConnectedRef.current = false;
    setWallet((current) => ({ ...current, address: undefined }));
    setWalletError('');
    setWalletMenuOpen(false);
  }, []);

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
      const provider = activeProvider;
      if (!provider || !wallet.address) {
        throw new Error('Connect a wallet before submitting a swap.');
      }
      if (wallet.chainId !== selectedChainId) {
        throw new Error(`Switch your wallet to ${selectedNetwork.name} before submitting a swap.`);
      }

      const hash = await provider.request({
        method: 'eth_sendTransaction',
        params: [
          {
            from: wallet.address,
            to: transaction.to,
            value: transaction.value,
            data: transaction.data,
            gas: transaction.gasLimit,
          },
        ],
      });
      if (typeof hash !== 'string') {
        throw new Error('The wallet did not return a transaction hash.');
      }
      return hash;
    },
    [activeProvider, selectedChainId, selectedNetwork.name, wallet.address, wallet.chainId],
  );

  const walletButtonLabel = wallet.address ? `Switch to ${selectedNetwork.name}` : 'Connect wallet';
  const selectedWallet = walletOptions.find((option) => option.id === selectedWalletId);

  return (
    <div className={styles.page}>
      <section className={styles.content}>
        <div className={styles.walletRow}>
          <div className={styles.networkControl}>
            <p className={styles.networkNotice} role="note">
              Only same-chain swaps are supported
            </p>
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
                  {selectedWallet?.icon ? (
                    <img className={styles.walletIcon} src={selectedWallet.icon} alt="" />
                  ) : (
                    <span className={styles.walletIconFallback}>
                      {selectedWallet?.name.slice(0, 1) ?? 'W'}
                    </span>
                  )}
                  <span>{wallet.address.slice(0, 6)}...{wallet.address.slice(-5)}</span>
                  <ChevronDown aria-hidden="true" className={styles.chevron} size={18} strokeWidth={2.2} />
                </button>
                {walletMenuOpen && (
                  <div className={styles.accountMenu} role="dialog" aria-label="Connected wallet">
                    <div className={styles.accountDetails}>
                      {selectedWallet?.icon ? (
                        <img className={styles.walletIconLarge} src={selectedWallet.icon} alt="" />
                      ) : (
                        <span className={styles.walletIconLargeFallback}>
                          {selectedWallet?.name.slice(0, 1) ?? 'W'}
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
                          onClick={disconnectWallet}
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
                        setWalletModalOpen(true);
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
                  setWalletModalOpen(true);
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

        {selectedChainId === BNB_CHAIN_ID && (
          <div className={styles.featuredToken}>
            <img src="/BNBs.svg" alt="" />
            <span>BNBs token is pinned at the top of the token list</span>
          </div>
        )}

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
      {walletModalOpen && (
        <div
          className={styles.modalBackdrop}
          onClick={(event) => {
            if (event.target === event.currentTarget) setWalletModalOpen(false);
          }}
        >
          <section
            aria-labelledby="wallet-modal-title"
            aria-modal="true"
            className={styles.walletModal}
            role="dialog"
          >
            <div className={styles.modalHeader}>
              <h2 id="wallet-modal-title">Connect a wallet</h2>
              <button
                aria-label="Close"
                className={styles.modalClose}
                onClick={() => setWalletModalOpen(false)}
                type="button"
              >
                ×
              </button>
            </div>
            {walletOptions.length > 0 ? (
              <div className={styles.walletOptionList}>
                {walletOptions.map((option) => (
                  <button
                    className={styles.walletOption}
                    key={option.id}
                    onClick={() => void connectWallet(option)}
                    type="button"
                  >
                    {option.icon ? (
                      <img className={styles.walletIconLarge} src={option.icon} alt="" />
                    ) : (
                      <span className={styles.walletIconLargeFallback}>
                        {option.name.slice(0, 1)}
                      </span>
                    )}
                    <span>{option.name}</span>
                    {option.id === selectedWalletId && wallet.address && (
                      <span className={styles.connectedLabel}>Connected</span>
                    )}
                  </button>
                ))}
              </div>
            ) : (
              <p className={styles.noWallets}>
                No browser wallets were detected. Install a supported wallet extension, then refresh
                this page.
              </p>
            )}
            {walletError && (
              <p className={styles.modalError} role="alert">
                {walletError}
              </p>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
