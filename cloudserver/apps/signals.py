"""
apps/signals.py
技術指標の計算ロジック（services）+ DRF ビュー（views）を1ファイルに統合
"""
from __future__ import annotations

import math
import time

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
EXCHANGES = [
    ("okx",     ccxt.okx),
    ("bybit",   ccxt.bybit),
    ("gateio",  ccxt.gateio),
    ("kucoin",  ccxt.kucoin),
    ("binance", ccxt.binance),
    ("huobi",   ccxt.huobi),
]

# ─── インメモリキャッシュ ───────────────────
_cache: dict[str, tuple[float, list]] = {}


# ─────────────────────────────────────────────
# データ取得
# ─────────────────────────────────────────────
def get_exchange_data(time_frame: str, symbol: str, kline_limit: int = 100) -> pd.DataFrame:
    """複数取引所を順番に試して OHLCV を取得する。"""
    last_error = None
    for name, cls in EXCHANGES:
        try:
            print(f"[signals] trying {name}...")
            exchange = cls({"enableRateLimit": True, "timeout": 10000})
            ohlcv = exchange.fetch_ohlcv(symbol=symbol, timeframe=time_frame, limit=kline_limit)
            df = pd.DataFrame(ohlcv, columns=["timestamp", "open", "high", "low", "close", "volume"])
            df["datetime"] = pd.to_datetime(df["timestamp"], unit="ms")
            df.set_index("datetime", inplace=True)
            print(f"[signals] success: {name}")
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


def calculate_sar(df: pd.DataFrame) -> pd.DataFrame:
    sar = ta.psar(high=df["high"], low=df["low"], acceleration=0.02, maximum=0.2)
    df["SAR_long"]  = sar.iloc[:, 0]
    df["SAR_short"] = sar.iloc[:, 1]
    return df


def get_all_indicators(time_frame: str, symbol: str, kline_limit: int = 100) -> pd.DataFrame:
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

    return [symbol, str(df.index[-1]), f"{latest['close']:.3f}", sar, macd, kdj, kdj_over]


@api_view(["GET"])
def get_signals(request: Request) -> Response:
    """
    GET /?timeFrame=1h&symbol=BTC/USDT
    FastAPI の getSignals() に相当。
    """
    time_frame = request.query_params.get("timeFrame", "").strip()
    symbol     = request.query_params.get("symbol", "").strip()

    if not time_frame or not symbol:
        return Response({"error": "timeFrame and symbol are required"}, status=400)

    cache_key = f"{time_frame}_{symbol}"
    now       = time.time()

    # キャッシュヒット
    if cache_key in _cache:
        cached_at, data = _cache[cache_key]
        if now - cached_at < settings.CACHE_DURATION:
            print("[signals] cache hit")
            return Response(data)

    try:
        df      = get_all_indicators(time_frame, symbol, settings.KLINE_LIMIT)
        display = build_display(symbol, df)
        _cache[cache_key] = (now, display)
        return Response(display)
    except Exception as e:
        print(f"[signals] error: {e}")
        return Response({"error": str(e)}, status=502)
