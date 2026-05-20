"""
apps/signals.py
技術指標の計算ロジック（services）+ DRF ビュー（views）を1ファイルに統合
"""
from __future__ import annotations

import math
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import ccxt
import pandas as pd
import pandas_ta as ta
from django.conf import settings
from rest_framework.decorators import api_view
from rest_framework.request import Request
from rest_framework.response import Response

# ─────────────────────────────────────────────
# 定数
# ─────────────────────────────────────────────
# 全局共享的 exchange 实例（避免重复创建）
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
                    "rateLimit": 50,  # 降低到 50ms
                    "timeout": 20000,
                    "options": {
                        "defaultType": "spot",  # 现货交易
                    }
                })
    return _exchange

EXCHANGES = [
    ("binance", get_exchange),  # 使用函数返回共享实例
]

# ─── スレッドセーフなインメモリキャッシュ ──────
_cache: dict[str, tuple[float, list]] = {}
_cache_lock = threading.Lock()


# ─────────────────────────────────────────────
# データ取得
# ─────────────────────────────────────────────
def get_exchange_data(time_frame: str, symbol: str, kline_limit: int = 60) -> pd.DataFrame:
    """複数取引所を順番に試して OHLCV を取得する。"""
    last_error = None
    for name, exchange_provider in EXCHANGES:
        try:
            print(f"[signals] trying {name} {symbol} {time_frame}...")
            exchange = exchange_provider()  # 获取共享实例
            ohlcv = exchange.fetch_ohlcv(symbol=symbol, timeframe=time_frame, limit=kline_limit)
            df = pd.DataFrame(ohlcv, columns=["timestamp", "open", "high", "low", "close", "volume"])
            df["datetime"] = pd.to_datetime(df["timestamp"], unit="ms")
            df.set_index("datetime", inplace=True)
            print(f"[signals] success: {name} {symbol} {time_frame}")
            return df
        except Exception as e:
            print(f"[signals] {name} failed: {e}")
            last_error = e

    raise RuntimeError(f"All exchanges failed: {last_error}")


# ─────────────────────────────────────────────
# 指標計算
# ─────────────────────────────────────────────
def calculate_macd(df: pd.DataFrame, fast: int = 12, slow: int = 26, signal: int = 9) -> pd.DataFrame:
    macd = ta.macd(df["close"], fast=fast, slow=slow, signal=signal)
    df["MACD"]        = macd[f"MACD_{fast}_{slow}_{signal}"]
    df["MACD_signal"] = macd[f"MACDs_{fast}_{slow}_{signal}"]
    df["MACD_hist"]   = macd[f"MACDh_{fast}_{slow}_{signal}"]
    return df


def calculate_kdj(df: pd.DataFrame) -> pd.DataFrame:
    kdj = ta.kdj(
        high=df["high"], low=df["low"], close=df["close"],
        length=9, signal=3, scalar=100, offset=0, append=True,
    )
    df["K"] = kdj["K_9_3"]
    df["D"] = kdj["D_9_3"]
    df["J"] = kdj["J_9_3"]
    return df

# def calculate_sar(df: pd.DataFrame) -> pd.DataFrame:
#     sar = ta.psar(high=df["high"], low=df["low"], acceleration=0.02, maximum=0.2)
#     df["SAR_long"]  = sar.iloc[:, 0]
#     df["SAR_short"] = sar.iloc[:, 1]
#     return df
def calculate_sar(
    df: pd.DataFrame,
    accel_init: float = 0.02,
    accel_max: float = 0.2,
    accel_step: float = 0.02
) -> pd.DataFrame:
    """
    手动计算 PSAR (Parabolic SAR) - 国内主流标准算法
    
    参数:
        df: 包含 high, low 两列的 DataFrame
        accel_init: 初始加速因子，默认 0.02
        accel_max: 最大加速因子，默认 0.2
        accel_step: 加速步长，默认 0.02
    
    返回:
        添加了 SAR（趋势转换后的SAR值）和 SAR_trend（1=上升趋势，-1=下降趋势）的 DataFrame
    """
    df = df.copy()
    n = len(df)
    
    # 初始化
    sar = pd.Series(index=df.index, dtype=float)
    trend = pd.Series(index=df.index, dtype=int)  # 1: 上升趋势, -1: 下降趋势
    ep = pd.Series(index=df.index, dtype=float)   # 极值点
    af = pd.Series(index=df.index, dtype=float)   # 加速因子
    
    # 计算初始趋势（前两个K线决定）
    if df.iloc[0]["high"] + df.iloc[0]["low"] < df.iloc[1]["high"] + df.iloc[1]["low"]:
        # 上升趋势
        trend.iloc[0] = 1
        sar.iloc[0] = df.iloc[0]["low"]  # 初始 SAR 为第一根最低价
        ep.iloc[0] = df.iloc[0]["high"]  # 初始极值点为第一根最高价
    else:
        # 下降趋势
        trend.iloc[0] = -1
        sar.iloc[0] = df.iloc[0]["high"]  # 初始 SAR 为第一根最高价
        ep.iloc[0] = df.iloc[0]["low"]    # 初始极值点为第一根最低价
    
    af.iloc[0] = accel_init
    
    # 递推计算后续 SAR
    for i in range(1, n):
        prev_sar = sar.iloc[i-1]
        prev_ep = ep.iloc[i-1]
        prev_af = af.iloc[i-1]
        prev_trend = trend.iloc[i-1]
        
        # 1. 计算当前 SAR 值
        current_sar = prev_sar + prev_af * (prev_ep - prev_sar)
        
        # 2. 根据趋势限制 SAR 范围
        if prev_trend == 1:  # 上升趋势
            # SAR 不能高于前两期的最低价
            current_sar = min(current_sar, df.iloc[i-1]["low"])
            if i > 1:
                current_sar = min(current_sar, df.iloc[i-2]["low"])
        else:  # 下降趋势
            # SAR 不能低于前两期的最高价
            current_sar = max(current_sar, df.iloc[i-1]["high"])
            if i > 1:
                current_sar = max(current_sar, df.iloc[i-2]["high"])
        
        # 3. 判断是否反转
        trend_changed = False
        
        if prev_trend == 1:  # 上升趋势，检查是否跌破 SAR
            if current_sar > df.iloc[i]["low"]:
                # 反转：上升转下降
                trend.iloc[i] = -1
                sar.iloc[i] = prev_ep  # SAR 设为前一个极值点
                ep.iloc[i] = df.iloc[i]["low"]
                af.iloc[i] = accel_init
                trend_changed = True
        else:  # 下降趋势，检查是否涨破 SAR
            if current_sar < df.iloc[i]["high"]:
                # 反转：下降转上升
                trend.iloc[i] = 1
                sar.iloc[i] = prev_ep  # SAR 设为前一个极值点
                ep.iloc[i] = df.iloc[i]["high"]
                af.iloc[i] = accel_init
                trend_changed = True
        
        # 4. 如果没有反转，更新极值点和加速因子
        if not trend_changed:
            trend.iloc[i] = prev_trend
            sar.iloc[i] = current_sar
            
            # 更新极值点
            if prev_trend == 1:  # 上升趋势
                if df.iloc[i]["high"] > prev_ep:
                    ep.iloc[i] = df.iloc[i]["high"]
                    af.iloc[i] = min(prev_af + accel_step, accel_max)
                else:
                    ep.iloc[i] = prev_ep
                    af.iloc[i] = prev_af
            else:  # 下降趋势
                if df.iloc[i]["low"] < prev_ep:
                    ep.iloc[i] = df.iloc[i]["low"]
                    af.iloc[i] = min(prev_af + accel_step, accel_max)
                else:
                    ep.iloc[i] = prev_ep
                    af.iloc[i] = prev_af
    
    # 5. 写入 DataFrame
    df["SAR"] = sar
    df["SAR_trend"] = trend
    
    return df


def get_all_indicators(time_frame: str, symbol: str, kline_limit: int = 60) -> pd.DataFrame:
    df = get_exchange_data(time_frame, symbol, kline_limit)
    df = calculate_macd(df)
    df = calculate_kdj(df)
    df = calculate_sar(df)
    return df


def build_display(symbol: str, df: pd.DataFrame) -> list:
    """最新足のシグナルを判定してリストで返す。"""
    latest = df.iloc[-1]

    sar  = "〇" if not math.isnan(latest["SAR_long"]) else "×"
    macd = "〇" if latest["MACD"] > latest["MACD_signal"] else "×"
    kdj  = "〇" if latest["K"] > latest["D"] else "×"

    if latest["K"] > 80 or latest["D"] > 80:
        kdj_over = "OverBuy"
    elif latest["K"] < 20 or latest["D"] < 20:
        kdj_over = "OverSell"
    else:
        kdj_over = "Normal"

    # 根据币种设置不同的小数位数
    price = latest['close']
    if 'DOGE' in symbol:
        price_str = f"{price:.5f}"  # DOGE保留5位小数
    elif 'SHIB' in symbol or 'PEPE' in symbol:
        price_str = f"{price:.8f}"  # SHIB和PEPE保留8位小数
    else:
        price_str = f"{price:.2f}"  # 其他币种保留2位小数

    return [symbol, str(df.index[-1]), price_str, sar, macd, kdj, kdj_over]


# ─────────────────────────────────────────────
# 1ターゲット分の処理（スレッドから呼び出す）
# ─────────────────────────────────────────────
def _process_target(time_frame: str, symbol: str) -> dict:
    """
    キャッシュ確認 → 必要なら get_all_indicators → キャッシュ更新。
    戻り値: {"key": cache_key, "data": display_list, "error": str | None}
    """
    cache_key = f"{time_frame}_{symbol}"
    now = time.time()

    with _cache_lock:
        if cache_key in _cache:
            cached_at, data = _cache[cache_key]
            if now - cached_at < settings.CACHE_DURATION:
                print(f"[signals] cache hit: {cache_key}")
                return {"key": cache_key, "data": data, "error": None}

    try:
        df = get_all_indicators(time_frame, symbol, settings.KLINE_LIMIT)
        display = build_display(symbol, df)
        with _cache_lock:
            _cache[cache_key] = (time.time(), display)
        return {"key": cache_key, "data": display, "error": None}
    except Exception as e:
        print(f"[signals] error {cache_key}: {e}")
        return {"key": cache_key, "data": None, "error": str(e)}

@api_view(["POST"])
def get_signals(request: Request) -> Response:
    """
    POST /signals
    Body: {"targets": [{"timeframe": "1h", "symbol": "BTC/USDT"}, ...]}

    フロントから dataSource を一括送信し、行単位でマルチスレッド処理して返す。

    レスポンス:
    {
      "results": {
        "1h_BTC/USDT": ["BTC/USDT", "2025-...", "94000.123", "〇", "〇", "×", "Normal"],
        "4h_BTC/USDT": ["BTC/USDT", "2025-...", "94000.123", "〇", "〇", "×", "Normal"],
        ...
      },
      "errors": {
        "4h_BNB/USDT": "timeout ..."   // 失敗した行のみ含まれる
      }
    }
    """
    targets: list = request.data.get("targets", [])

    if not targets or not isinstance(targets, list):
        return Response({"error": "targets (list) is required"}, status=400)

    # 入力バリデーション
    for t in targets:
        if not isinstance(t, dict) or not t.get("timeframe") or not t.get("symbol"):
            return Response(
                {"error": "Each target must have 'timeframe' and 'symbol'"},
                status=400,
            )

    results: dict = {}
    errors:  dict = {}

    # ターゲット数に合わせてスレッドを動的に確保
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
