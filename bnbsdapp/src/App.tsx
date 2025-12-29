import { LiFiWidget, WidgetConfig, WidgetFeeConfig } from '@lifi/widget'

// Basic advanced configuration
const basicFeeConfig: WidgetFeeConfig = {
  name: "BNBs DApp fee",
  logoURI: "BNBs.svg",
  fee: 0.003,
  showFeePercentage: true,
  showFeeTooltip: true,
};

const widgetConfig: WidgetConfig = {
  integrator: "BNBs",
  // Set fee parameter to 3%
  feeConfig: basicFeeConfig,
  fee: 0.003,
  // 启用此选项后，将采用混合方法，有效地结合外部和内部钱包管理。
  // 这种设置可在集成商的自定义钱包菜单和组件的原生钱包菜单之间实现灵活的平衡，
  // 确保在所有生态系统中都能提供流畅的用户体验，即使外部支持不完善或正在过渡中。
  walletConfig: {
    usePartialWalletManagement: true,
    forceInternalWalletManagement: true,
  },

  // Other options...
};

export function App() {
  return (
    <div className="App-div">
      <LiFiWidget integrator="BNBs" config={widgetConfig} />
    </div>
  )
}

export default App;