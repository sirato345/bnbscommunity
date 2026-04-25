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


def calculate_sar(df: pd.DataFrame) -> pd.DataFrame:
    sar = ta.psar(high=df["high"], low=df["low"], acceleration=0.02, maximum=0.2)
    df["SAR_long"]  = sar.iloc[:, 0]
    df["SAR_short"] = sar.iloc[:, 1]
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

    return [symbol, str(df.index[-1]), f"{latest['close']:.3f}", sar, macd, kdj, kdj_over]


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


# ─────────────────────────────────────────────
# DRF ビュー
# ─────────────────────────────────────────────
# @api_view(["GET"])
# def get_signals_bulk(request: Request) -> Response:
#     """
#     GET /?timeFrame=1h&symbol=BTC/USDT
#     単一シンボル・タイムフレームのシグナルを返す（後方互換用）。
#     """
#     time_frame = request.query_params.get("timeFrame", "").strip()
#     symbol     = request.query_params.get("symbol", "").strip()

#     if not time_frame or not symbol:
#         return Response({"error": "timeFrame and symbol are required"}, status=400)

#     result = _process_target(time_frame, symbol)
#     if result["error"]:
#         return Response({"error": result["error"]}, status=502)
#     return Response(result["data"])


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
        "4h_BTC/USDT": [...],
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
