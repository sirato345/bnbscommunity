"""
apps/signals.py
技术指标的计算逻辑（服务层）+ DRF 视图（视图层）合并为一个文件
TradingView 完全兼容的指标计算（warmup预热版）
"""
from __future__ import annotations

import math
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import ccxt
import pandas as pd
import numpy as np
from django.conf import settings
from rest_framework.decorators import api_view
from rest_framework.request import Request
from rest_framework.response import Response

# ─────────────────────────────────────────────
# 常量定义
# ─────────────────────────────────────────────
# 实际使用的K线数量（最终返回的K线数）
KLINE_LIMIT = 500

# 预热需要的额外K线数量（计算指标时丢弃前N根，消除初始值影响）
WARMUP_BARS = 300

# 从交易所获取的总K线数量 = 使用数量 + 预热数量
TOTAL_KLINE_LIMIT = KLINE_LIMIT + WARMUP_BARS  # = 800

# 全局共享的 exchange 实例
_exchange = None
_exchange_lock = threading.Lock()


def get_exchange():
    """获取全局共享的 exchange 实例（线程安全）"""
    global _exchange
    if _exchange is None:
        with _exchange_lock:
            if _exchange is None:
                _exchange = ccxt.binance({
                    "enableRateLimit": True,
                    "rateLimit": 50,
                    "timeout": 20000,
                    "options": {
                        "defaultType": "spot",
                    }
                })
    return _exchange


EXCHANGES = [
    ("binance", get_exchange),
]

# 线程安全的内存缓存
_cache: dict[str, tuple[float, list]] = {}
_cache_lock = threading.Lock()


# ─────────────────────────────────────────────
# 数据获取（预热版）
# ─────────────────────────────────────────────
def get_exchange_data(time_frame: str, symbol: str, kline_limit: int = TOTAL_KLINE_LIMIT) -> pd.DataFrame:
    """
    依次尝试多个交易所获取 OHLCV 数据。
    为了预热会获取比实际需求更多的K线。
    """
    last_error = None
    for name, exchange_provider in EXCHANGES:
        try:
            print(f"[signals] 尝试 {name} {symbol} {time_frame} (获取数量={kline_limit})...")
            exchange = exchange_provider()
            ohlcv = exchange.fetch_ohlcv(symbol=symbol, timeframe=time_frame, limit=kline_limit)
            df = pd.DataFrame(ohlcv, columns=["timestamp", "open", "high", "low", "close", "volume"])
            df["datetime"] = pd.to_datetime(df["timestamp"], unit="ms")
            df.set_index("datetime", inplace=True)
            print(f"[signals] 成功: {name} {symbol} {time_frame} (共 {len(df)} 根K线)")
            return df
        except Exception as e:
            print(f"[signals] {name} 失败: {e}")
            last_error = e

    raise RuntimeError(f"所有交易所均失败: {last_error}")


# ─────────────────────────────────────────────
# TradingView 完全兼容的指标计算
# ─────────────────────────────────────────────

def calculate_macd_tradingview(df: pd.DataFrame, fast: int = 12, slow: int = 26, signal: int = 9) -> pd.DataFrame:
    """
    TradingView 完全兼容的 MACD 指标
    
    TradingView 的 MACD 计算方式:
    - MACD Line = EMA(close, fast) - EMA(close, slow)
    - Signal Line = EMA(MACD Line, signal)
    - Histogram = MACD Line - Signal Line
    
    所有 EMA 均使用标准 EMA，而非 Wilder's Moving Average
    """
    # 标准 EMA 计算（与 TradingView 相同）
    ema_fast = df["close"].ewm(span=fast, adjust=False).mean()
    ema_slow = df["close"].ewm(span=slow, adjust=False).mean()
    
    macd_line = ema_fast - ema_slow
    signal_line = macd_line.ewm(span=signal, adjust=False).mean()
    histogram = macd_line - signal_line
    
    df["MACD"] = macd_line
    df["MACD_signal"] = signal_line
    df["MACD_hist"] = histogram
    
    return df


def calculate_kdj_tradingview(df: pd.DataFrame, length: int = 9, smooth_k: int = 3, smooth_d: int = 3) -> pd.DataFrame:
    """
    TradingView 完全兼容的 KDJ 指标（基于随机指标）
    
    TradingView 的标准 KDJ:
    - RSV = (close - lowest(low, length)) / (highest(high, length) - lowest(low, length)) * 100
    - K = SMA(RSV, smooth_k)
    - D = SMA(K, smooth_d)
    - J = 3 * K - 2 * D
    
    注意: TradingView 通常使用 SMA，而非 EMA
    """
    # 计算最高价和最低价
    highest_high = df["high"].rolling(window=length).max()
    lowest_low = df["low"].rolling(window=length).min()
    
    # RSV (原始随机值) 计算
    # 防止除零错误
    denominator = highest_high - lowest_low
    denominator = denominator.replace(0, np.nan)
    
    rsv = (df["close"] - lowest_low) / denominator * 100
    rsv = rsv.fillna(50)  # 初始值设为50
    
    # 计算 K, D, J（使用 SMA）
    k = rsv.rolling(window=smooth_k).mean()
    d = k.rolling(window=smooth_d).mean()
    j = 3 * k - 2 * d
    
    df["K"] = k
    df["D"] = d
    df["J"] = j
    
    return df


def calculate_sar_tradingview(
    df: pd.DataFrame,
    start: float = 0.02,
    increment: float = 0.02,
    maximum: float = 0.20
) -> pd.DataFrame:
    """
    TradingView 完全兼容的 PSAR（抛物线转向指标）
    
    TradingView 的 ta.sar() 完全相同的算法:
    - 初始趋势由前两根K线决定
    - AF（加速因子）从 start 开始，每次更新极值时增加 increment
    - AF 上限为 maximum
    - SAR 不能超越极值点（限制在前一周期范围内）
    
    参考: Wilder, J. Welles (1978). New Concepts in Technical Trading Systems
    """
    df = df.copy()
    high = df["high"].values
    low = df["low"].values
    n = len(df)
    
    # 初始化数组
    sar = np.full(n, np.nan, dtype=float)
    trend = np.zeros(n, dtype=int)  # 1: 上升趋势, -1: 下降趋势
    ep = np.full(n, np.nan, dtype=float)  # 极值点
    af = np.full(n, np.nan, dtype=float)  # 加速因子
    
    # 判断初始趋势（TradingView 的方法：比较前两根K线）
    # 上升趋势: 第一根K线的(high+low) < 第二根K线的(high+low)
    if high[0] + low[0] < high[1] + low[1]:
        trend[0] = 1  # 上升趋势
        sar[0] = low[0]
        ep[0] = high[0]
    else:
        trend[0] = -1  # 下降趋势
        sar[0] = high[0]
        ep[0] = low[0]
    af[0] = start
    
    # 主循环
    for i in range(1, n):
        prev_sar = sar[i-1]
        prev_ep = ep[i-1]
        prev_af = af[i-1]
        prev_trend = trend[i-1]
        
        # 1. 计算当前 SAR 值
        current_sar = prev_sar + prev_af * (prev_ep - prev_sar)
        
        # 2. 将 SAR 限制在前一周期范围内（TradingView 的重要特征）
        if prev_trend == 1:  # 上升趋势
            # SAR 不能低于前两个周期的最低价
            current_sar = min(current_sar, low[i-1])
            if i > 1:
                current_sar = min(current_sar, low[i-2])
        else:  # 下降趋势
            # SAR 不能高于前两个周期的最高价
            current_sar = max(current_sar, high[i-1])
            if i > 1:
                current_sar = max(current_sar, high[i-2])
        
        # 3. 检查趋势反转
        trend_changed = False
        
        if prev_trend == 1:  # 上升趋势中
            if current_sar > low[i]:
                # 反转为下降趋势
                trend[i] = -1
                sar[i] = prev_ep
                ep[i] = low[i]
                af[i] = start
                trend_changed = True
        else:  # 下降趋势中
            if current_sar < high[i]:
                # 反转为上升趋势
                trend[i] = 1
                sar[i] = prev_ep
                ep[i] = high[i]
                af[i] = start
                trend_changed = True
        
        # 4. 趋势延续的情况
        if not trend_changed:
            trend[i] = prev_trend
            sar[i] = current_sar
            
            # 更新极值点和加速因子
            if prev_trend == 1:  # 上升趋势
                if high[i] > prev_ep:
                    ep[i] = high[i]
                    af[i] = min(prev_af + increment, maximum)
                else:
                    ep[i] = prev_ep
                    af[i] = prev_af
            else:  # 下降趋势
                if low[i] < prev_ep:
                    ep[i] = low[i]
                    af[i] = min(prev_af + increment, maximum)
                else:
                    ep[i] = prev_ep
                    af[i] = prev_af
    
    # 将结果写入 DataFrame
    df["SAR"] = sar
    df["SAR_trend"] = trend
    df["SAR_long"] = pd.Series(sar).where(trend == 1, pd.NA)
    df["SAR_short"] = pd.Series(sar).where(trend == -1, pd.NA)
    
    return df


def get_all_indicators(time_frame: str, symbol: str) -> pd.DataFrame:
    """
    获取所有 TradingView 兼容的指标（预热版）
    
    处理流程:
    1. 获取 TOTAL_KLINE_LIMIT (800根) 的K线数据
    2. 对所有数据计算指标
    3. 丢弃开头的预热期间 (WARMUP_BARS = 300根)
    4. 返回剩余的 KLINE_LIMIT (500根) 数据
    """
    # 1. 获取包含预热数据在内的完整数据（共800根）
    df_full = get_exchange_data(time_frame, symbol, TOTAL_KLINE_LIMIT)
    
    # 2. 对所有数据计算指标
    df_full = calculate_macd_tradingview(df_full)
    df_full = calculate_kdj_tradingview(df_full)
    df_full = calculate_sar_tradingview(df_full)
    
    # 3. 丢弃开头的预热数据
    #    预热期间的指标值不稳定，从最终结果中剔除
    df_result = df_full.iloc[WARMUP_BARS:].copy()
    
    print(f"[signals] 预热完成: 丢弃前 {WARMUP_BARS} 根K线，返回 {len(df_result)} 根K线给 {symbol}")
    
    return df_result


def build_display(symbol: str, df: pd.DataFrame) -> list:
    """判断最新信号并以列表形式返回（TradingView 兼容）"""
    latest = df.iloc[-1]
    
    # SAR: SAR_long 不为空表示上升趋势（〇）
    sar = "〇" if not pd.isna(latest["SAR_long"]) else "×"
    
    # MACD: MACD 线高于信号线时为 〇
    macd = "〇" if latest["MACD"] > latest["MACD_signal"] else "×"
    
    # KDJ: K 高于 D 时为 〇（TradingView 标准）
    kdj = "〇" if latest["K"] > latest["D"] else "×"
    
    # KDJ 超买/超卖判断
    if latest["K"] > 80 or latest["D"] > 80:
        kdj_over = "OverBuy"
    elif latest["K"] < 20 or latest["D"] < 20:
        kdj_over = "OverSell"
    else:
        kdj_over = "Normal"
    
    # 价格格式化（根据不同币种调整小数位数）
    price = latest['close']
    if 'DOGE' in symbol:
        price_str = f"{price:.5f}"
    elif 'SHIB' in symbol or 'PEPE' in symbol:
        price_str = f"{price:.8f}"
    else:
        price_str = f"{price:.2f}"
    
    return [symbol, str(df.index[-1]), price_str, sar, macd, kdj, kdj_over]


# ─────────────────────────────────────────────
# 单个目标的处理函数（由线程调用）
# ─────────────────────────────────────────────
def _process_target(time_frame: str, symbol: str) -> dict:
    """
    检查缓存 → 如需要则获取指标 → 更新缓存
    返回值: {"key": cache_key, "data": display_list, "error": str | None}
    """
    cache_key = f"{time_frame}_{symbol}"
    now = time.time()
    
    # 从 settings 获取缓存时长（默认15秒）
    cache_duration = getattr(settings, 'CACHE_DURATION', 15)

    with _cache_lock:
        if cache_key in _cache:
            cached_at, data = _cache[cache_key]
            if now - cached_at < cache_duration:
                print(f"[signals] 缓存命中: {cache_key}")
                return {"key": cache_key, "data": data, "error": None}

    try:
        # 预热版：get_all_indicators 不再需要传入 KLINE_LIMIT
        df = get_all_indicators(time_frame, symbol)
        display = build_display(symbol, df)
        with _cache_lock:
            _cache[cache_key] = (time.time(), display)
        return {"key": cache_key, "data": display, "error": None}
    except Exception as e:
        print(f"[signals] 错误 {cache_key}: {e}")
        return {"key": cache_key, "data": None, "error": str(e)}


@api_view(["POST"])
def get_signals(request: Request) -> Response:
    """
    POST /signals
    请求体: {"targets": [{"timeframe": "1h", "symbol": "BTC/USDT"}, ...]}

    前端批量发送数据源，使用多线程逐行处理并返回。

    响应格式:
    {
      "results": {
        "1h_BTC/USDT": ["BTC/USDT", "2025-...", "94000.123", "〇", "〇", "×", "Normal"],
        "4h_BTC/USDT": ["BTC/USDT", "2025-...", "94000.123", "〇", "〇", "×", "Normal"],
        ...
      },
      "errors": {
        "4h_BNB/USDT": "超时错误..."
      }
    }
    """
    targets: list = request.data.get("targets", [])

    if not targets or not isinstance(targets, list):
        return Response({"error": "需要提供 targets 列表参数"}, status=400)

    # 输入验证
    for t in targets:
        if not isinstance(t, dict) or not t.get("timeframe") or not t.get("symbol"):
            return Response(
                {"error": "每个 target 必须包含 'timeframe' 和 'symbol' 字段"},
                status=400,
            )

    results: dict = {}
    errors: dict = {}

    # 根据目标数量动态分配线程
    with ThreadPoolExecutor(max_workers=len(targets)) as executor:
        futures = {
            executor.submit(_process_target, t["timeframe"], t["symbol"]): t
            for t in targets
        }
        for future in as_completed(futures):
            res = future.result()
            if res["error"]:
                errors[res["key"]] = res["error"]
            else:
                results[res["key"]] = res["data"]

    return Response({"results": results, "errors": errors})