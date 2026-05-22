"""
apps/signals.py
技術指標の計算ロジック（services）+ DRF ビュー（views）を1ファイルに統合
TradingView 完全互換の指標計算
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
# 定数
# ─────────────────────────────────────────────
# TradingView 標準のK線数（十分な warm-up のため）
KLINE_LIMIT = 500

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

# スレッドセーフなインメモリキャッシュ
_cache: dict[str, tuple[float, list]] = {}
_cache_lock = threading.Lock()


# ─────────────────────────────────────────────
# データ取得
# ─────────────────────────────────────────────
def get_exchange_data(time_frame: str, symbol: str, kline_limit: int = KLINE_LIMIT) -> pd.DataFrame:
    """複数取引所を順番に試して OHLCV を取得する。"""
    last_error = None
    for name, exchange_provider in EXCHANGES:
        try:
            print(f"[signals] trying {name} {symbol} {time_frame}...")
            exchange = exchange_provider()
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
# TradingView 完全互換の指標計算
# ─────────────────────────────────────────────

def calculate_macd_tradingview(df: pd.DataFrame, fast: int = 12, slow: int = 26, signal: int = 9) -> pd.DataFrame:
    """
    TradingView 完全互換の MACD
    
    TradingView の MACD は:
    - MACD Line = EMA(close, fast) - EMA(close, slow)
    - Signal Line = EMA(MACD Line, signal)
    - Histogram = MACD Line - Signal Line
    
    全ての EMA は Wilder's Moving Average ではなく標準 EMA を使用
    """
    # 標準 EMA 計算（TradingView と同じ）
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
    TradingView 完全互換の KDJ (Stochastic RSI ベース)
    
    TradingView の標準 KDJ:
    - RSV = (close - lowest(low, length)) / (highest(high, length) - lowest(low, length)) * 100
    - K = SMA(RSV, smooth_k)
    - D = SMA(K, smooth_d)
    - J = 3 * K - 2 * D
    
    注: TradingView は通常 SMA を使用、EMA ではない
    """
    # 最高値と最安値の計算
    highest_high = df["high"].rolling(window=length).max()
    lowest_low = df["low"].rolling(window=length).min()
    
    # RSV (Raw Stochastic Value) の計算
    # ゼロ除算を防ぐ
    denominator = highest_high - lowest_low
    denominator = denominator.replace(0, np.nan)
    
    rsv = (df["close"] - lowest_low) / denominator * 100
    rsv = rsv.fillna(50)  # 初期値は50
    
    # K, D, J の計算（SMAを使用）
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
    TradingView 完全互換の PSAR (Parabolic SAR)
    
    TradingView の ta.sar() と完全に同じアルゴリズム:
    - 初期トレンドは最初の2本のK線で決定
    - AF (Acceleration Factor) は start から始まり、極値更新ごとに increment ずつ増加
    - AF は maximum を上限とする
    - SAR は極値を超えて移動しない（前の期間の範囲内に制限）
    
    Ref: Wilder, J. Welles (1978). New Concepts in Technical Trading Systems
    """
    df = df.copy()
    high = df["high"].values
    low = df["low"].values
    n = len(df)
    
    # 初期化
    sar = np.full(n, np.nan, dtype=float)
    trend = np.zeros(n, dtype=int)  # 1: uptrend, -1: downtrend
    ep = np.full(n, np.nan, dtype=float)  # Extreme Point
    af = np.full(n, np.nan, dtype=float)  # Acceleration Factor
    
    # 初期トレンドの決定（TradingView の方法: 最初の2本のK線を比較）
    # 上昇トレンド: 最初のK線の(high+low) < 2本目のK線の(high+low)
    if high[0] + low[0] < high[1] + low[1]:
        trend[0] = 1  # 上昇トレンド
        sar[0] = low[0]
        ep[0] = high[0]
    else:
        trend[0] = -1  # 下降トレンド
        sar[0] = high[0]
        ep[0] = low[0]
    af[0] = start
    
    # メインループ
    for i in range(1, n):
        prev_sar = sar[i-1]
        prev_ep = ep[i-1]
        prev_af = af[i-1]
        prev_trend = trend[i-1]
        
        # 1. 現在のSARを計算
        current_sar = prev_sar + prev_af * (prev_ep - prev_sar)
        
        # 2. SARを前の期間の範囲内に制限（TradingView の重要な特徴）
        if prev_trend == 1:  # 上昇トレンド
            # SAR は前の2期間の最安値を下回れない
            current_sar = min(current_sar, low[i-1])
            if i > 1:
                current_sar = min(current_sar, low[i-2])
        else:  # 下降トレンド
            # SAR は前の2期間の最高値を上回れない
            current_sar = max(current_sar, high[i-1])
            if i > 1:
                current_sar = max(current_sar, high[i-2])
        
        # 3. トレンド転換のチェック
        trend_changed = False
        
        if prev_trend == 1:  # 上昇トレンド中
            if current_sar > low[i]:
                # 下降への転換
                trend[i] = -1
                sar[i] = prev_ep
                ep[i] = low[i]
                af[i] = start
                trend_changed = True
        else:  # 下降トレンド中
            if current_sar < high[i]:
                # 上昇への転換
                trend[i] = 1
                sar[i] = prev_ep
                ep[i] = high[i]
                af[i] = start
                trend_changed = True
        
        # 4. トレンド継続の場合
        if not trend_changed:
            trend[i] = prev_trend
            sar[i] = current_sar
            
            # 極値点の更新と加速因子の調整
            if prev_trend == 1:  # 上昇トレンド
                if high[i] > prev_ep:
                    ep[i] = high[i]
                    af[i] = min(prev_af + increment, maximum)
                else:
                    ep[i] = prev_ep
                    af[i] = prev_af
            else:  # 下降トレンド
                if low[i] < prev_ep:
                    ep[i] = low[i]
                    af[i] = min(prev_af + increment, maximum)
                else:
                    ep[i] = prev_ep
                    af[i] = prev_af
    
    # DataFrame に結果を書き込み
    df["SAR"] = sar
    df["SAR_trend"] = trend
    df["SAR_long"] = pd.Series(sar).where(trend == 1, pd.NA)
    df["SAR_short"] = pd.Series(sar).where(trend == -1, pd.NA)
    
    return df


def get_all_indicators(time_frame: str, symbol: str, kline_limit: int = KLINE_LIMIT) -> pd.DataFrame:
    """すべてのTradingView互換インジケーターを取得"""
    df = get_exchange_data(time_frame, symbol, kline_limit)
    df = calculate_macd_tradingview(df)
    df = calculate_kdj_tradingview(df)
    df = calculate_sar_tradingview(df)
    return df


def build_display(symbol: str, df: pd.DataFrame) -> list:
    """最新のシグナルを判定してリストで返す（TradingView互換）"""
    latest = df.iloc[-1]
    
    # SAR: SAR_long が NaN でなければ上昇トレンド（〇）
    sar = "〇" if not pd.isna(latest["SAR_long"]) else "×"
    
    # MACD: MACDラインがシグナルラインを上回っていれば〇
    macd = "〇" if latest["MACD"] > latest["MACD_signal"] else "×"
    
    # KDJ: KがDを上回っていれば〇（TradingView標準）
    kdj = "〇" if latest["K"] > latest["D"] else "×"
    
    # KDJ オーバーソフト/オーバーハード
    if latest["K"] > 80 or latest["D"] > 80:
        kdj_over = "OverBuy"
    elif latest["K"] < 20 or latest["D"] < 20:
        kdj_over = "OverSell"
    else:
        kdj_over = "Normal"
    
    # 価格フォーマット（通貨ごとに小数点以下の桁数を変更）
    price = latest['close']
    if 'DOGE' in symbol:
        price_str = f"{price:.5f}"
    elif 'SHIB' in symbol or 'PEPE' in symbol:
        price_str = f"{price:.8f}"
    else:
        price_str = f"{price:.2f}"
    
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
    
    # settings からキャッシュ時間を取得（デフォルト15秒）
    cache_duration = getattr(settings, 'CACHE_DURATION', 15)

    with _cache_lock:
        if cache_key in _cache:
            cached_at, data = _cache[cache_key]
            if now - cached_at < cache_duration:
                print(f"[signals] cache hit: {cache_key}")
                return {"key": cache_key, "data": data, "error": None}

    try:
        df = get_all_indicators(time_frame, symbol, KLINE_LIMIT)
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
        "4h_BNB/USDT": "timeout ..."
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
    errors: dict = {}

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