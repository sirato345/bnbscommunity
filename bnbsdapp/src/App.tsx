import { LiFiWidget, WidgetConfig, WidgetFeeConfig, WidgetSDKConfig } from '@lifi/widget'
import type { RouteOptions } from '@lifi/sdk'
import { useWidgetEvents, WidgetEvent } from '@lifi/widget';
import { useState, useEffect } from 'react';
import type { Route } from '@lifi/sdk';

const BNBs_TOKEN_ADDRESS = "0xc07ef1c7af6112c34a110809c6c8efb343e63a64";
const BSC_CHAIN_ID = 56; // BSC

// Basic advanced configuration
const basicFeeConfig: WidgetFeeConfig = {
  name: "BNBs DApp fee",
  logoURI: "BNBs.svg",
  fee: 0.0005,
  showFeePercentage: true,
  showFeeTooltip: true,
  // 0.000  2.18	基础费用（一个BNB，870u的情况下，约千分之二点五）
  // 0.001	3.05	0.87（增加千分之一）	
  // 0.002	3.93	0.88（增加千分之二）
  // 0.003	4.80	0.87（增加千分之三）
};

// Basic advanced configuration
const routeOptions: RouteOptions = {
  maxPriceImpact: 1,
};

// Basic advanced configuration
const sdkConfig: WidgetSDKConfig = {
  routeOptions: routeOptions,
};

function App() {
  const widgetEvents = useWidgetEvents();

  const [priceUSD, setPriceUSD] = useState<string>("0");

  useEffect(() => {
    const fetchPrice = async () => {
      try {
        const res = await fetch(
          `https://li.quest/v1/token?chain=${BSC_CHAIN_ID}&token=${BNBs_TOKEN_ADDRESS}`
        );
        const data = await res.json();
        setPriceUSD(data.priceUSD ?? "0");
      } catch (e) {
        console.error("Price fetch failed:", e);
      }
    };

    fetchPrice();
    // 60秒ごとに更新
    const timer = setInterval(fetchPrice, 60_000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const onRouteExecutionCompleted = (route: Route) => {
      console.log("fromAddress:" + route.fromAddress);
      console.log("toAddress:" + route.toAddress);
      console.log("fromAmountUSD:" + route.fromAmountUSD);
      console.log("toAmountUSD:" + route.toAmountUSD);
      console.log("fromChainId:" + route.fromChainId);
      console.log("toChainId:" + route.toChainId);
      console.log("gasCostUSD:" + route.gasCostUSD);
      // gasCostUSD maybe undefined
      // console.log("fee:" + (Number(route.fromAmountUSD) - Number(route.toAmountUSD) - Number(route.gasCostUSD)));
      // ChainId
      // Bitcoin 20000000000001
      // Solana 1151111081099710
      // Sui 9270000000000000
    };
    widgetEvents.on(WidgetEvent.RouteExecutionCompleted, onRouteExecutionCompleted);

    return () => widgetEvents.all.clear();
  }, [widgetEvents]);

    const widgetConfig: WidgetConfig = {
    integrator: "BNBs",
    // Set fee parameter to 3%
    feeConfig: basicFeeConfig,
    // 禁用深色模式
    appearance: 'light',
    // 低流动性对应
    sdkConfig: sdkConfig,
    // 启用此选项后，将采用混合方法，有效地结合外部和内部钱包管理。
    // 这种设置可在集成商的自定义钱包菜单和组件的原生钱包菜单之间实现灵活的平衡，
    // 确保在所有生态系统中都能提供流畅的用户体验，即使外部支持不完善或正在过渡中。
    walletConfig: {
      usePartialWalletManagement: true,
      forceInternalWalletManagement: true,
    },

    tokens: {
      include: [
        {
          chainId: 56,           // BSC の場合
          address: "0xc07ef1c7af6112c34a110809c6c8efb343e63a64",
          symbol: "BNBs",
          name: "BNBs Token",
          decimals: 18,
          logoURI: "BNBs.svg",  // ← カスタムロゴ
          priceUSD: priceUSD
        }
      ],
    },

    chains: {
      to: {
        deny: [204],  // opBNB を除外
      },
    },

    fromChain: 56, // BNB Chain
    toChain: 56, // BNB Chain
    fromToken: "0x55d398326f99059ff775485246999027b3197955", // USDT
    toToken: "0xC07ef1C7af6112C34A110809C6c8Efb343e63A64", // BNBs
  };

  return (
    <div className="App-div">
      <LiFiWidget integrator="BNBs" config={widgetConfig} />
    </div>
  )
}

export default App;