# KyberSwap 钱包集成记录

## 当前状态

- 页面已有未提交的 LI.FI 钱包管理集成，使用 `@lifi/wallet-management` 和 Wagmi。
- 钱包选择使用 LI.FI 原生菜单；已连接账户信息和网络选择仍由 KyberSwap 页面显示。
- Wagmi 网络配置使用稳定的数组，避免每次渲染重新同步连接器并触发重连。
- Wagmi 配置包含页面网络列表对应的 viem 链定义，供 WalletConnect 建立多链会话。增删页面网络时应同步调整钱包配置。
- LI.FI 钱包配置也保持稳定，避免不必要的钱包列表刷新。
- MUI 主题启用 `cssVariables`：LI.FI 钱包组件依赖 `theme.vars.palette` 和 `theme.vars.shape`，普通主题会导致运行时错误。
- 钱包弹窗采用圆角、描边和无阴影卡片，接近 LI.FI 参考样式；标题使用原生的 “Select a wallet”。钱包范围仍限制为 EVM，不显示与 KyberSwap 交易不兼容的其他生态。
- 交易改用 Wagmi 的 `useSendTransaction`（每次发送时由连接器重新取得 provider），不再缓存 EIP-1193 provider 后直接调用 `eth_sendTransaction`。原因：手机 Chrome 抓取的日志显示，从 MetaMask 返回后页面经 Back-Forward Cache 恢复，MetaMask SDK 重连成功，但点击兑换后中继通道上没有任何交易消息发出，怀疑缓存的 provider 已失效（推断，未被日志直接证实）。切链使用 Wagmi 的 `useSwitchChain`（链未添加时由 Wagmi 按配置自动添加），页面不再缓存任何 EIP-1193 provider。需真机验证。
- KyberSwap `connectedAccount.address` 直接使用 Wagmi 已确认连接后的地址，不等待异步取得 EIP-1193 provider；Widget 会据此立即刷新当前代币余额。EIP-1193 provider 仍仅用于切链和交易签名。
- 代币列表和默认 BSC USDT 地址统一转为小写；KyberSwap Widget 按地址字符串键存取余额，统一大小写可避免 USDT 查询结果与默认输入代币键不匹配。
- 账户地址和链 ID 由 Wagmi 同步，交易仍由钱包签名，网络不匹配时禁止提交。
- 为保留手机钱包在站内导航期间的会话，首次打开 KyberSwap 后将页面和钱包提供者保留在根布局中，离开时隐藏页面并关闭钱包菜单。隐藏期间页面仍在运行；刷新、关闭标签页或钱包主动断开后的恢复不由此保证。
- KyberSwap 代币接口返回空白图标地址时，使用本地通用占位图，避免向控件传入空 `src`。
- 控件 2.5.2 自行导入的代币和路由中未知代币仍可能生成空图标地址；这些数据不经过页面的代币列表处理，需上游修复。
- 项目根目录的 `hs_err_pid24308.log` 显示 VS Code Java 语言服务发生原生内存分配失败；这不能单独证明是本页面代码造成的。
- 为降低编辑器再次卡死的风险，优先使用针对性、低资源的验证，暂不主动运行完整生产构建。
- KyberSwap 使用导航栏下方的独立滚动区域，与持币页面的滚动条位置一致；移动端为较高的导航栏留出空间。
- 修正移动端滚动区域仍保留旧版顶部内边距而产生的大块空白。

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
- 会话组件的轻量逻辑检查覆盖首次访问前不加载、离开时隐藏和返回时显示；已检查相关 TypeScript 语法及补丁格式。未进行完整构建或浏览器、手机钱包实测。
- 手机 MetaMask 的站内切换保持连接仍需真机验证。
- 本次滚动调整已在桌面和模拟手机尺寸检查独立滚动及钱包选择弹窗；站内往返后所选网络保持不变，未连接钱包或提交交易。
- 测试切换到 Arbitrum 时，浏览器报告公共 RPC 跨域请求失败；尚未确认其对实际连接钱包和交易的影响。
- 顶部空白修复已在 320、390、767 像素宽的浏览器视口检查间距和滚动；尚未在手机真机验证。
- 余额加载修复将 Wagmi 已确认连接的地址立即传给 KyberSwap Widget；KyberSwap 的 BSC USDT 接口返回小写地址，与默认输入地址规范化后相同。相关 TypeScript 文件定向类型检查和 `git diff --check` 通过，尚未用真实钱包在浏览器验证余额显示。
