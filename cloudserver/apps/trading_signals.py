"""
apps/trading_signals.py
交易信号Web服务 - 获取每个币种的买卖信号
"""
from __future__ import annotations

import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from typing import Dict, List, Optional

import pytz
import requests
from django.conf import settings
from rest_framework.decorators import api_view
from rest_framework.request import Request
from rest_framework.response import Response

# ─────────────────────────────────────────────
# 定数
# ─────────────────────────────────────────────
# 信号API的URL（可以配置在settings中）
SIGNAL_API_URL = "https://bnbs-django-275599637949.asia-northeast1.run.app/signals"

# 缓存持续时间（秒）
CACHE_DURATION = 15

# 默认交易对列表
DEFAULT_TARGETS = [
    {'timeframe': '15m', 'symbol': 'ETH/USDT'},
    {'timeframe': '1h', 'symbol': 'ETH/USDT'},
    {'timeframe': '4h', 'symbol': 'ETH/USDT'},
    {'timeframe': '15m', 'symbol': 'BTC/USDT'},
    {'timeframe': '1h', 'symbol': 'BTC/USDT'},
    {'timeframe': '4h', 'symbol': 'BTC/USDT'},
    {'timeframe': '15m', 'symbol': 'BNB/USDT'},
    {'timeframe': '1h', 'symbol': 'BNB/USDT'},
    {'timeframe': '4h', 'symbol': 'BNB/USDT'},
    {'timeframe': '15m', 'symbol': 'DOGE/USDT'},
    {'timeframe': '1h', 'symbol': 'DOGE/USDT'},
    {'timeframe': '4h', 'symbol': 'DOGE/USDT'},
]

# 日本时区
JAPAN_TZ = pytz.timezone('Asia/Tokyo')

# 线程安全的缓存
_cache: dict[str, tuple[float, dict]] = {}
_cache_lock = threading.Lock()


# ─────────────────────────────────────────────
# 信号获取与解析
# ─────────────────────────────────────────────
def fetch_signals(targets: List[Dict] = None) -> Optional[Dict]:
    """
    发送POST请求获取交易信号
    
    Args:
        targets: 请求的目标列表，如果不提供则使用默认值
        
    Returns:
        原始API响应数据，失败时返回None
    """
    if targets is None:
        targets = DEFAULT_TARGETS
    
    try:
        payload = {"targets": targets}
        headers = {'Content-Type': 'application/json'}
        
        print(f"[trading_signals] 发送请求到: {SIGNAL_API_URL}")
        print(f"[trading_signals] 请求目标数量: {len(targets)}")
        
        response = requests.post(
            SIGNAL_API_URL,
            json=payload,
            headers=headers,
            timeout=30
        )
        response.raise_for_status()
        
        print(f"[trading_signals] ✅ API响应成功: {response.status_code}")
        return response.json()
        
    except requests.exceptions.RequestException as e:
        print(f"[trading_signals] ❌ API请求失败: {e}")
        if hasattr(e, 'response') and e.response is not None:
            print(f"[trading_signals] 响应状态码: {e.response.status_code}")
            print(f"[trading_signals] 响应内容: {e.response.text}")
        return None


# buy/sell信号检查函数（新逻辑）
def check_signal(indicators: List[str]) -> bool:
    """
    检查买入信号
    条件：
    1. 如果1h和4h的六个指标全部为 '〇'，返回 True
    2. 否则，如果同时满足以下三个条件，返回 True：
    a. 1h_MACD 和 1h_KDJ 均为 '〇'
    b. 4h_KDJ、4h_MACD、4h_SAR 中至少有两个为 '〇'
    c. 15m_MACD 为 '〇'
    
    参数indicators格式: 
    [币种, 价格, 信号, 15m_KDJ, 15m_MACD, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
    """
    if len(indicators) < 11:
        return False
    
    # 1h指标（索引5-7: 1h_SAR, 1h_MACD, 1h_KDJ）
    one_hour_indicators = indicators[5:8]  # [1h_SAR, 1h_MACD, 1h_KDJ]
    # 4h指标（索引8-10: 4h_SAR, 4h_MACD, 4h_KDJ）
    four_hour_indicators = indicators[8:11]  # [4h_SAR, 4h_MACD, 4h_KDJ]
    
    # 规则1：1h和4h的全部六个指标都为 '〇'，15m_MACD也必须为 '〇'，此时不限制15m_KDJ的状态
    if (all(ind == '〇' for ind in one_hour_indicators + four_hour_indicators) and indicators[4] == '〇'):
        return True
    
    # 规则2：
    # 条件a：1h_MACD 和 1h_KDJ 都为 '〇'（索引6和7）
    condition_a = indicators[6] == '〇' and indicators[7] == '〇'
    # 条件b：4h至少有两个为 '〇'
    condition_b = sum(1 for ind in four_hour_indicators if ind == '〇') >= 2
    # 条件c：15m_KDJ 为 '〇'（索引3）
    condition_c = indicators[3] == '〇'
    
    return condition_a and condition_b and condition_c

def parse_signals(raw_data: Dict, targets: List[Dict] = None) -> Dict[str, str]:
    """
    解析信号数据，提取每个币种的买卖信号
    
    Args:
        raw_data: API返回的原始数据
        targets: 请求的目标列表（用于保持顺序）
        
    Returns:
        格式: {"BTC": "buy", "ETH": "sell", ...}
    """
    if not raw_data:
        return {}
    
    results = raw_data.get('results', {})
    if not results:
        print(f"[trading_signals] 没有找到信号数据")
        return {}
    
    print(f"[trading_signals] 解析原始数据，共 {len(results)} 条记录")
    
    # 存储每个币种的指标数据
    formatted: Dict[str, List[str]] = {}
    
    # 从targets动态获取币种顺序
    if targets is None:
        targets = DEFAULT_TARGETS
    
    currency_order = []
    for target in targets:
        symbol = target['symbol'].split('/')[0]  # 'BTC/USDT' -> 'BTC'
        if symbol not in currency_order:
            currency_order.append(symbol)
    
    # 解析每个时间周期的数据
    for period_symbol, data in results.items():
        # period_symbol格式如 "1h_BTC/USDT"
        parts = period_symbol.split('_', 1)
        if len(parts) != 2:
            continue
        
        timeframe = parts[0]  # '15m', '1h' 或 '4h'
        symbol_with_slash = parts[1]  # 'BTC/USDT'
        base_currency = symbol_with_slash.split('/')[0]  # 'BTC'
        
        if len(data) >= 7:
            price = data[2]
            indicators = data[3:6]  # [SAR, MACD, KDJ]
            
            # 初始化或更新币种数据
            if base_currency not in formatted:
                # 格式: [币种, 价格, 买卖信号, 15m_KDJ, 15m_MACD, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
                formatted[base_currency] = [base_currency, price, '—', '—', '—', '—', '—', '—', '—', '—', '—']
            
            if timeframe == '15m':
                # 15m指标：索引3=KDJ, 索引4=MACD
                formatted[base_currency][3] = indicators[2]  # KDJ指标
                formatted[base_currency][4] = indicators[1]  # MACD指标
            elif timeframe == '1h':
                # 1h指标：索引5=SAR, 索引6=MACD, 索引7=KDJ
                formatted[base_currency][5] = indicators[0]  # SAR
                formatted[base_currency][6] = indicators[1]  # MACD
                formatted[base_currency][7] = indicators[2]  # KDJ
            elif timeframe == '4h':
                # 4h指标：索引8=SAR, 索引9=MACD, 索引10=KDJ
                formatted[base_currency][8] = indicators[0]  # SAR
                formatted[base_currency][9] = indicators[1]  # MACD
                formatted[base_currency][10] = indicators[2]  # KDJ
    
    # 判断每个币种的买卖信号
    result: Dict[str, str] = {}
    for currency, indicators in formatted.items():
        if check_signal(indicators):
            result[currency] = 'buy'
        else:
            result[currency] = 'sell'
    
    # 按原始顺序排列
    ordered_result = {}
    for currency in currency_order:
        if currency in result:
            ordered_result[currency] = result[currency]
    
    print(f"[trading_signals] 解析完成: {ordered_result}")
    return ordered_result


def cal_trading_signals(targets: List[Dict] = None, use_cache: bool = True) -> Dict[str, str]:
    """
    获取所有币种的买卖信号（带缓存）
    
    Args:
        targets: 请求的目标列表
        use_cache: 是否使用缓存
        
    Returns:
        格式: {"BTC": "buy", "ETH": "sell", ...}
    """
    # 生成缓存键（基于targets）
    if targets is None:
        targets = DEFAULT_TARGETS
    cache_key = json.dumps(targets, sort_keys=True)
    
    now = time.time()
    
    # 检查缓存
    if use_cache:
        with _cache_lock:
            if cache_key in _cache:
                cached_at, data = _cache[cache_key]
                if now - cached_at < CACHE_DURATION:
                    print(f"[trading_signals] 缓存命中: {cache_key}")
                    return data
    
    # 获取信号数据
    raw_data = fetch_signals(targets)
    if not raw_data:
        print(f"[trading_signals] 无法获取信号数据")
        return {}
    
    # 检查API错误
    if 'errors' in raw_data and raw_data['errors']:
        print(f"[trading_signals] ⚠️ API返回错误: {raw_data['errors']}")
    
    # 解析信号
    signals = parse_signals(raw_data, targets)
    
    # 更新缓存
    if use_cache and signals:
        with _cache_lock:
            _cache[cache_key] = (now, signals)
    
    return signals

@api_view(["GET"])
def get_trading_signals(request: Request) -> Response:
    """
    简化版API - 只返回买卖信号列表
    
    响应格式:
    {
        "BTC": "buy",
        "ETH": "sell",
        "BNB": "buy",
        "DOGE": "sell"
    }
    """
    try:
        signals = cal_trading_signals()
        return Response(signals)
    except Exception as e:
        print(f"[trading_signals] 处理请求时出错: {e}")
        return Response(
            {"error": str(e)},
            status=500
        )
