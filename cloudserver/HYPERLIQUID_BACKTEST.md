# Hyperliquid 现货指标回测

## 当前范围

`hyperliquid_backtest.py` 使用 Hyperliquid 主网的公开行情接口，只读 UETH/USDC 现货（当前市场代码 `@151`），不需要钱包、密钥或账户，也不会下单。程序每次启动会检查市场代码是否仍对应 UETH/USDC；UETH 是 Unit Ethereum，并非直接使用 Binance 的 ETH/USDT 行情。

首版只实现 finance 研究文档中的**组合 1：顺势回调**，用于观察行情事件，而不是模拟真实交易或证明策略收益。其他组合、实时监控、测试网交易和自动下单均未实现。

## 可复用的 finance 指标模块

`finance_indicators.py` 移植了 finance 中可计算的图表指标，现有回测也从此模块调用 MA、MACD、KDJ 和 SAR。模块仅使用标准库，不请求行情，不连接钱包。输入 K 线和输出序列均按**从旧到新**排列；`Candle(start, open, high, low, close)` 的 `start` 是调用方使用的时间戳，同一序列必须严格递增。需要自行保证同一市场、周期、时间间隔和已收盘状态；数据预热长度也由调用方决定。

| 调用 | 结果与 finance 对应关系 |
| --- | --- |
| `moving_average(candles, index, period)` | 指定位置的均价；period 可取 10、20、50、200。历史不足抛错 |
| `moving_averages(candles, period)` | 均线序列；对应 finance 图表，最旧的完整窗口也不绘制，因此首个结果在索引 `period` |
| `ema(values, period)`、`macd(candles)` | MACD 12/26/9，以简单均值初始化；依次返回 DIF、信号线、柱体 |
| `kdj_full(candles)` | 依次返回 K、D、J；9 周期，初始 K/D 为 50。`kdj(candles)` 保留原回测的 K、D 双返回值 |
| `sar(candles)` | SAR，步长 0.02、最大 0.2 |
| `bollinger_bands(closes)` | 20 周期、2 倍总体标准差；每个有效结果有 `middle`、`upper`、`lower` |
| `td_sequential(closes)` | 0 表示无信号，正负 8 或 9 表示方向和计数；与 finance 一样不是完整 TD Sequential |
| `average_candles(candles)` | 四根 OHLC 平滑后的 finance 自定义平均 K 线，并非标准 Heikin-Ashi |
| `direction_light(...)`、`position_light(...)` | 分别对应 J/DIF 方向灯、K/D 与 DIF/信号线位置灯；可传入 `inverted=True` |
| `macd_divergences(candles, dif, signal, ...)` | 按 finance 实际调用用 DIF 寻找背离，支持可视范围和反向坐标；返回背离和确认索引 |
| `fear_greed_period_key(day, timeframe)`、`fear_greed_latest_by_period(records, timeframe)` | 对外部恐慌贪婪指数记录按日、3 日、周、月、季度、年对齐；不计算或下载该外部指数 |

除 TD 无信号使用 0 外，指标序列预热不足的位置为 `None`。各函数对非有限值、无效参数等会报错；布林带遇到非有限值也会报错，而不是像图表一样将该窗口留空。调用示例：

```python
from finance_indicators import Candle, bollinger_bands, kdj_full, macd, sar

# 实际调用时提供按时间递增、已收盘且经过检查的历史 K 线。
candles = [Candle(0, 100, 102, 99, 101)]
dif, signal, histogram = macd(candles)
k, d, j = kdj_full(candles)
bands = bollinger_bands([c.close for c in candles])
sar_values = sar(candles)
```

### 时序和数值注意事项

- SAR 的第一个输出使用了第二根 K 线初始化，与 finance 图表一致；这个值不能在第一根收盘时使用。平均 K 线和均线也保留 finance 最早窗口的显示边界。
- 背离的 `pivot_index` 是图上标注位置，`confirmed_index` 是至少经过右侧 3 根 K 线后才可知的位置。`kind` 是**整个传入数据截止末尾的图表分类**，可能包含后续金叉/死叉；`cross_index` 和 `cross_confirmed_index` 指出交叉发生及可以确认的位置。历史回测必须用 `divergence.kind_at(当时索引)`，不能提前使用最终 `kind`。`visible_count` 模拟图表可视范围；若历史可视范围随时间变化，还需逐时点重新计算。
- 恐慌贪婪指数来自外部数据源，不是由 K 线推导的指标。本模块只做 finance 的周期对齐；回测前必须按真实发布时间过滤输入，不能用某周期结束后才发布的记录。人工绘制趋势线不是自动指标计算，因此不在模块中。
- Python 双精度与 Java 单精度、不同预热起点可能导致数值和临界信号不同。以上移植是计算方法复现，不保证与图表每次显示逐位一致。

## 本地执行

在 PowerShell 中：

```powershell
Set-Location D:\BNBs\cloudserver
.\venv\Scripts\python.exe .\hyperliquid_backtest.py
```

使用项目现有 Python 虚拟环境；脚本仅使用 Python 标准库，无需额外安装依赖。默认回看最近 14 天，也可以指定 1 至 14 天及回测结束时间：

```powershell
.\venv\Scripts\python.exe .\hyperliquid_backtest.py --days 7
.\venv\Scripts\python.exe .\hyperliquid_backtest.py --days 7 --end 2026-11-24T00:00:00Z
```

结束时间必须带时区且不晚于当前时间，会向下对齐到 5 分钟边界。固定结束时间有助于复现，但 Hyperliquid 可访问的历史 K 线有限；如果数据不足、缺失、重复或 OHLC 不合法，脚本会报错退出，不会填充或忽略缺口。接口不可用时也会报错退出。依赖网络访问公开 API。

运行测试：

```powershell
.\venv\Scripts\python.exe -m unittest -v test_finance_indicators test_hyperliquid_backtest
```

## 指标和事件定义

- 4 小时：最新已收盘 K 线的收盘价高于 MA20 和 MA50，且 SAR 低于收盘价。
- 1 小时：最新已收盘 K 线的 MACD DIF 高于信号线。
- 15 分钟：最新已收盘 K 线发生 KDJ 金叉，即前一根 K ≤ D、本根 K > D；金叉在这根 15 分钟 K 线成为最新已收盘 K 线期间有效。
- 5 分钟：最新已收盘 K 线的 MACD 柱由严格小于零变为严格大于零；恰好等于零不算。
- 使用项目的 MA、MACD 12/26/9（EMA 以简单均值初始化）、KDJ 9（K/D 初值 50）和 SAR 0.02/0.2 计算方法。每个周期从回测开始前请求至少 200 根 K 线预热；Python 双精度与 finance 的 Java 浮点、不同历史起点可能导致边界值差异。
- 每根 5 分钟 K 线收盘后判断；只使用当时已收盘的更大周期 K 线。符合条件时，将下一根 5 分钟 K 线的开盘价作为理想化入场参考；没有下一根 K 线的末尾信号不计入。
- 对每个信号分别统计入场开始后 4、8、24 小时内最高价是否**严格超过**入场价的 102%，以及窗口最后一根收盘价是否严格超过 102%。窗口不完整的信号不计入该窗口的分母。

输出的筛选计数是逐级累计的 5 分钟判断次数：先满足 4 小时趋势，再依次检查 1 小时、15 分钟和 5 分钟条件；它不是独立条件的命中次数。无信号或窗口不足时显示 `N/A`，不应解释为成功率为零。

结果未考虑成交可能性、手续费、价差、滑点、延迟、止损、重复信号或仓位，不能当作交易收益。基于有限历史的单次回测也不能代替按时间划分的样本外验证。

## 首次运行记录

使用公开行情回看 2026-11-10 00:35 至 2026-11-24 00:35 UTC：共检查 4032 个 5 分钟判断点，逐级通过 4 小时趋势、1 小时 MACD、15 分钟 KDJ 和 5 分钟 MACD 的数量分别为 48、48、3、0。最终没有信号，4、8、24 小时事件比例均无可用样本；不能据此判断策略表现。

## 未来部署

本次未更改现有 Django 服务、Dockerfile 或部署脚本，也未部署到 Google Cloud。该脚本执行一次后退出；未来若要定时运行，适合另行评估 Cloud Run Job 与 Cloud Scheduler，而不是假定当前 Cloud Run HTTP 服务会在后台持续运行。运行结果可输出到标准输出供日志系统收集；持久化结果、运行权限、调度及告警需要单独设计。

参考：[Hyperliquid Info API](https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint)。
