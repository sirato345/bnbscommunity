"""
apps/trading_signals.py
交易信号Web服务 - 获取每个币种的买卖信号
"""
from __future__ import annotations
from .signals import _process_target
from common.common import get_all_indicators_dict

from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, List, Optional

from rest_framework.decorators import api_view
from rest_framework.request import Request
from rest_framework.response import Response

# 默认交易对列表
DEFAULT_TARGETS = [
    {'timeframe': '5m',  'symbol': 'BTC/USDT'},
    {'timeframe': '15m', 'symbol': 'BTC/USDT'},
    {'timeframe': '1h',  'symbol': 'BTC/USDT'},
    {'timeframe': '4h',  'symbol': 'BTC/USDT'},
    {'timeframe': '5m',  'symbol': 'ETH/USDT'},
    {'timeframe': '15m', 'symbol': 'ETH/USDT'},
    {'timeframe': '1h',  'symbol': 'ETH/USDT'},
    {'timeframe': '4h',  'symbol': 'ETH/USDT'},
    {'timeframe': '5m',  'symbol': 'BNB/USDT'},
    {'timeframe': '15m', 'symbol': 'BNB/USDT'},
    {'timeframe': '1h',  'symbol': 'BNB/USDT'},
    {'timeframe': '4h',  'symbol': 'BNB/USDT'},
    {'timeframe': '5m',  'symbol': 'DOGE/USDT'},
    {'timeframe': '15m', 'symbol': 'DOGE/USDT'},
    {'timeframe': '1h',  'symbol': 'DOGE/USDT'},
    {'timeframe': '4h',  'symbol': 'DOGE/USDT'},
]


# ─────────────────────────────────────────────────────────
# 信号取得
# ─────────────────────────────────────────────────────────
def fetch_signals(targets: List[Dict] = None) -> Optional[Dict]:
    """_process_target を直接呼び出して交易信号を取得する"""
    if targets is None:
        targets = DEFAULT_TARGETS

    results: dict = {}
    errors: dict = {}

    with ThreadPoolExecutor(max_workers=min(len(targets), 16)) as executor:
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

    return {"results": results, "errors": errors}


# ─────────────────────────────────────────────────────────
# 売買シグナル取得
# ─────────────────────────────────────────────────────────
def cal_trading_signals(targets: List[Dict] = None) -> Dict[str, str]:
    """
    各通貨の売買シグナルを返す（キャッシュなし）

    戻り値: {"BTC": "buy", "ETH": "sell", ...}
    """
    if targets is None:
        targets = DEFAULT_TARGETS

    raw_data = fetch_signals(targets)
    if not raw_data:
        print("[trading_signals] 无法获取信号数据")
        return {}

    if raw_data.get('errors'):
        print(f"[trading_signals] ⚠️ API返回错误: {raw_data['errors']}")

    formatted = get_all_indicators_dict(raw_data, targets)

    # 按原始顺序返回结果
    currency_order = []
    for target in targets:
        symbol = target['symbol'].split('/')[0]
        if symbol not in currency_order:
            currency_order.append(symbol)

    result: Dict[str, str] = {}
    for currency in currency_order:
        if currency in formatted:
            result[currency] = formatted[currency][2]
        else:
            result[currency] = 'sell'

    print(f"[trading_signals] 解析完成: {result}")
    return result


# ─────────────────────────────────────────────────────────
# API ビュー
# ─────────────────────────────────────────────────────────
@api_view(["GET"])
def get_trading_signals(request: Request) -> Response:
    """
    GET /trading/signals
    戻り値: {"BTC": "buy", "ETH": "sell", ...}
    """
    try:
        return Response(cal_trading_signals())
    except Exception as e:
        print(f"[trading_signals] 处理请求时出错: {e}")
        return Response({"error": str(e)}, status=500)