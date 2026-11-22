# KyberSwap 钱包集成记录

## 当前状态

- 页面已有未提交的 LI.FI 钱包管理集成，使用 `@lifi/wallet-management` 和 Wagmi。
- 钱包选择使用 LI.FI 原生菜单；已连接账户信息和网络选择仍由 KyberSwap 页面显示。
- Wagmi 网络配置使用稳定的数组，避免每次渲染重新同步连接器并触发重连。
- Wagmi 配置包含页面网络列表对应的 viem 链定义，供 WalletConnect 建立多链会话。增删页面网络时应同步调整钱包配置。
- LI.FI 钱包配置也保持稳定，避免不必要的钱包列表刷新。
- MUI 主题启用 `cssVariables`：LI.FI 钱包组件依赖 `theme.vars.palette` 和 `theme.vars.shape`，普通主题会导致运行时错误。
- 钱包弹窗采用圆角、描边和无阴影卡片，接近 LI.FI 参考样式；标题使用原生的 “Select a wallet”。钱包范围仍限制为 EVM，不显示与 KyberSwap 交易不兼容的其他生态。
- 交易使用当前 Wagmi 连接器对应的 EIP-1193 provider；切换钱包时不会继续使用旧 provider。
- 账户地址和链 ID 由 Wagmi 同步，交易仍由钱包签名，网络不匹配时禁止提交。
- KyberSwap 代币接口返回空白图标地址时，使用本地通用占位图，避免向控件传入空 `src`。
- 控件 2.5.2 自行导入的代币和路由中未知代币仍可能生成空图标地址；这些数据不经过页面的代币列表处理，需上游修复。
- 项目根目录的 `hs_err_pid24308.log` 显示 VS Code Java 语言服务发生原生内存分配失败；这不能单独证明是本页面代码造成的。
- 为降低编辑器再次卡死的风险，优先使用针对性、低资源的验证，暂不主动运行完整生产构建。

## 配置

- 如需使用自己的 WalletConnect 项目，设置公开环境变量 `NEXT_PUBLIC_WALLET_CONNECT_PROJECT_ID` 并重启应用；未设置时，LI.FI 钱包菜单使用其默认配置。
- Vercel 部署必须同时提交 `bnbscommunity/package.json` 和 `bnbscommunity/package-lock.json` 中的 LI.FI、Wagmi 等依赖修改。提交 `87930c5` 包含钱包代码，但不包含这些依赖声明；仅在本机安装依赖不会让远程构建可用。

## 验证

- 已测试接口返回空字符串、空白图标地址时使用占位图，正常地址保持不变，非字符串图标仍按无效数据报错。
- 使用实际 LI.FI `WalletTag` 服务端渲染复现普通主题的 `palette` 错误，并验证启用 CSS 变量后可正常渲染；同时检查了钱包弹窗使用的主题变量。
- 使用实际 LI.FI 钱包卡片进行服务端渲染，验证描边、无阴影及主题圆角配置；这不替代浏览器视觉确认。
- TypeScript 文件通过轻量语法转译检查；这不等同于类型检查或浏览器运行测试。
- 已核对页面的 24 个网络 ID 与 Wagmi 使用的 viem 链定义一致，`git diff --check` 通过。
- 因内存紧张，未运行完整类型检查或生产构建。
- 仍需在浏览器检查连接、切换账户和网络、断开以及交易提交，包括 WalletConnect 在各网络上的可用性。
- 本次弹窗样式修改尚需浏览器视觉确认。
