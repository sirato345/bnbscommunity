"""
apps/trading_signals.py
交易信号Web服务 - 获取每个币种的买卖信号
"""
from __future__ import annotations
from .signals import _process_target  # 同一アプリ内の場合

import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from typing import Dict, List, Optional, Tuple

import pytz
from django.conf import settings
from rest_framework.decorators import api_view
from rest_framework.request import Request
from rest_framework.response import Response

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

# ========== 新增：冷却管理（平仓记录）==========
_cooldown_records: dict[str, tuple[float, str, float]] = {}  # {symbol: (timestamp, reason, profit_pct)}
_cooldown_lock = threading.Lock()

# ========== 新增：15m指标连续弱计数 ==========
_weak_15m_counter: dict[str, int] = {}  # {symbol: consecutive_bad_count}
_weak_15m_lock = threading.Lock()


# ─────────────────────────────────────────────
# 信号获取与解析
# ─────────────────────────────────────────────
def fetch_signals(targets: List[Dict] = None) -> Optional[Dict]:
    """
    signals.py の _process_target を直接呼び出して交易信号を取得する。
    戻り値の形式は元の HTTP API レスポンスと同一。
    """
    if targets is None:
        targets = DEFAULT_TARGETS

    results: dict = {}
    errors:  dict = {}

    with ThreadPoolExecutor(max_workers=min(len(targets), 12)) as executor:
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


# ========== 修改：check_signal 函数（新开仓条件）==========
def check_signal(indicators: List[str]) -> bool:
    """
    检查买入信号（开仓条件）
    
    新规则：
    1. 15m_KDJ = 〇
    2. 15m_MACD = 〇
    3. 1h_SAR = 〇
    4. 1h_MACD = 〇
    5. 1h_KDJ = 〇
    6. 4h 指标中至少2个为〇（4h_SAR, 4h_MACD, 4h_KDJ）
    
    参数indicators格式: 
    [币种, 价格, 信号, 15m_KDJ, 15m_MACD, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
    """
    if len(indicators) < 11:
        return False
    
    # 15m指标（索引3=KDJ, 索引4=MACD）
    fifteen_ok = indicators[3] == '〇' and indicators[4] == '〇'
    
    # 1h指标（索引5=SAR, 索引6=MACD, 索引7=KDJ）
    one_hour_ok = indicators[5] == '〇' and indicators[6] == '〇' and indicators[7] == '〇'
    
    # 4h指标（索引8=SAR, 索引9=MACD, 索引10=KDJ）- 至少2个为〇
    four_hour_list = [indicators[8], indicators[9], indicators[10]]
    four_hour_count = sum(1 for ind in four_hour_list if ind == '〇')
    four_hour_ok = four_hour_count >= 2
    
    return fifteen_ok and one_hour_ok and four_hour_ok


# ========== 新增：获取所有指标的辅助函数 ==========
def get_all_indicators_dict(raw_data: Dict, targets: List[Dict] = None) -> Dict[str, List[str]]:
    """
    从原始数据中提取每个币种的所有指标
    
    返回格式:
    {
        "BTC": [币种, 价格, 信号, 15m_KDJ, 15m_MACD, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ],
        ...
    }
    """
    if not raw_data:
        return {}
    
    results = raw_data.get('results', {})
    if not results:
        return {}
    
    if targets is None:
        targets = DEFAULT_TARGETS
    
    # 初始化每个币种的指标数组
    formatted: Dict[str, List[str]] = {}
    
    for target in targets:
        symbol = target['symbol'].split('/')[0]  # 'BTC/USDT' -> 'BTC'
        if symbol not in formatted:
            # [币种, 价格, 信号, 15m_KDJ, 15m_MACD, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
            formatted[symbol] = [symbol, '0', '—', '—', '—', '—', '—', '—', '—', '—', '—']
    
    # 解析每个时间周期的数据
    for period_symbol, data in results.items():
        parts = period_symbol.split('_', 1)
        if len(parts) != 2:
            continue
        
        timeframe = parts[0]
        symbol_with_slash = parts[1]
        base_currency = symbol_with_slash.split('/')[0]
        
        if base_currency not in formatted:
            continue
        
        if len(data) >= 7:
            price = data[2]
            indicators = data[3:6]  # [SAR, MACD, KDJ]
            
            # 更新价格
            formatted[base_currency][1] = price
            
            if timeframe == '15m':
                formatted[base_currency][3] = indicators[2]  # 15m_KDJ
                formatted[base_currency][4] = indicators[1]  # 15m_MACD
            elif timeframe == '1h':
                formatted[base_currency][5] = indicators[0]  # 1h_SAR
                formatted[base_currency][6] = indicators[1]  # 1h_MACD
                formatted[base_currency][7] = indicators[2]  # 1h_KDJ
            elif timeframe == '4h':
                formatted[base_currency][8] = indicators[0]  # 4h_SAR
                formatted[base_currency][9] = indicators[1]  # 4h_MACD
                formatted[base_currency][10] = indicators[2]  # 4h_KDJ
    
    return formatted


def parse_signals(raw_data: Dict, targets: List[Dict] = None) -> Dict[str, str]:
    """
    解析信号数据，提取每个币种的买卖信号（开仓信号）
    
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
    
    formatted = get_all_indicators_dict(raw_data, targets)
    
    if targets is None:
        targets = DEFAULT_TARGETS
    
    currency_order = []
    for target in targets:
        symbol = target['symbol'].split('/')[0]
        if symbol not in currency_order:
            currency_order.append(symbol)
    
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


# ========== 新增：冷却管理函数 ==========
def update_cooldown(symbol: str, exit_reason: str, profit_pct: float):
    """更新冷却记录"""
    with _cooldown_lock:
        _cooldown_records[symbol] = (time.time(), exit_reason, profit_pct)


def check_cooldown(symbol: str, current_time: float) -> Tuple[bool, int]:
    """
    检查冷却状态
    返回: (是否在冷却中, 剩余秒数)
    规则：盈利平仓无冷却，亏损平仓同币种冷却1小时
    """
    with _cooldown_lock:
        if symbol not in _cooldown_records:
            return False, 0
        
        last_time, reason, profit_pct = _cooldown_records[symbol]
        elapsed = current_time - last_time
        
        # 盈利平仓无冷却
        if profit_pct > 0:
            return False, 0
        
        # 亏损平仓：同币种冷却1小时
        cooldown_seconds = 3600
        if elapsed < cooldown_seconds:
            remaining = int(cooldown_seconds - elapsed)
            return True, remaining
    
    return False, 0


def update_fifteen_counter(symbol: str, indicators: List[str]) -> int:
    """
    更新15m指标连续弱计数
    返回当前计数
    """
    if len(indicators) < 11:
        return 0
    
    fifteen_weak = not (indicators[3] == '〇' and indicators[4] == '〇')
    
    with _weak_15m_lock:
        if fifteen_weak:
            _weak_15m_counter[symbol] = _weak_15m_counter.get(symbol, 0) + 1
        else:
            _weak_15m_counter[symbol] = 0
        return _weak_15m_counter[symbol]


def reset_fifteen_counter(symbol: str):
    """重置15m指标计数"""
    with _weak_15m_lock:
        _weak_15m_counter[symbol] = 0


# ========== 新增：平仓条件检查函数 ==========
def check_exit_condition(
    indicators: List[str],
    entry_price: float,
    current_price: float,
    symbol: str,
    fifteen_confirm_count: int
) -> Tuple[bool, str]:
    """
    检查平仓条件
    
    条件（满足任意一条即平仓）：
    1. 亏损 ≥ -0.5%（硬止损）
    2. 1h_MACD 变为 ×
    3. 1h_KDJ 变为 ×
    4. 1h_SAR 变为 ×
    5. 15m_KDJ 或 15m_MACD 变为 ×（需要连续确认2次）
    
    参数indicators格式同 check_signal
    返回: (是否平仓, 平仓原因)
    """
    if len(indicators) < 11:
        return False, ""
    
    # 1. 硬止损
    loss_pct = (current_price - entry_price) / entry_price
    if loss_pct <= -0.005:
        return True, f"stop_loss_{loss_pct:.4f}"
    
    # 2. 1h_MACD 变 ×（索引6）
    if indicators[6] != '〇':
        return True, "1h_macd_weak"
    
    # 3. 1h_KDJ 变 ×（索引7）
    if indicators[7] != '〇':
        return True, "1h_kdj_weak"
    
    # 4. 1h_SAR 变 ×（索引5）
    if indicators[5] != '〇':
        return True, "1h_sar_break"
    
    # 5. 15m 指标转弱（需要连续确认2次）
    fifteen_weak = not (indicators[3] == '〇' and indicators[4] == '〇')
    
    if fifteen_weak:
        if fifteen_confirm_count >= 2:  # 需要连续2次（6分钟）
            return True, "15m_weak"
    
    return False, ""


def check_position_exit(
    symbol: str,
    entry_price: float,
    current_price: float,
    indicators: List[str]
) -> Tuple[bool, str, float]:
    """
    持仓平仓检查（需要传入持仓价格）
    
    返回:
        (是否平仓, 平仓原因, 盈亏百分比)
    """
    profit_pct = (current_price - entry_price) / entry_price
    
    # 更新15m弱计数
    fifteen_count = update_fifteen_counter(symbol, indicators)
    
    # 检查平仓条件
    should_close, close_reason = check_exit_condition(
        indicators, entry_price, current_price, symbol, fifteen_count
    )
    
    if should_close:
        # 更新冷却记录
        update_cooldown(symbol, close_reason, profit_pct)
        # 重置15m计数
        reset_fifteen_counter(symbol)
        return True, close_reason, profit_pct
    
    return False, "", profit_pct


# ========== 修改：cal_trading_signals 函数（增加开仓时机检查）==========
def is_entry_time() -> bool:
    """
    检查当前是否为开仓时机（15m K线收盘后）
    
    开仓窗口：收盘后5秒 ~ 3分20秒（200秒）
    - 最早：收盘后5秒（数据已稳定）
    - 最晚：收盘后5分钟（避免太迟入场）
    """
    now_jst = datetime.now(JAPAN_TZ)
    minute = now_jst.minute
    second = now_jst.second
    
    # 计算距离上次15m K线收盘的时间（秒）
    # 15m K线收盘时刻：00, 15, 30, 45 分
    current_close_minute = (minute // 15) * 15
    seconds_since_close = (minute - current_close_minute) * 60 + second
    
    # 收盘后5秒到200秒（3分20秒）之间可以开仓
    return 5 <= seconds_since_close <= 200


def cal_trading_signals(targets: List[Dict] = None, use_cache: bool = True) -> Dict[str, str]:
    """
    获取所有币种的买卖信号（带缓存）
    
    新增强制：只在15m K线收盘后的1分钟内才返回buy信号
    其他时间即使条件满足也返回sell
    
    Args:
        targets: 请求的目标列表
        use_cache: 是否使用缓存
        
    Returns:
        格式: {"BTC": "buy", "ETH": "sell", ...}
    """
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
    
    # 获取所有指标的完整数据
    formatted = get_all_indicators_dict(raw_data, targets)
    
    currency_order = []
    for target in targets:
        symbol = target['symbol'].split('/')[0]
        if symbol not in currency_order:
            currency_order.append(symbol)
    
    # 判断每个币种的买卖信号
    result: Dict[str, str] = {}
    
    # 检查是否在开仓时间窗口
    entry_time_ok = is_entry_time()
    
    for currency, indicators in formatted.items():
        # 检查冷却
        in_cooldown, _ = check_cooldown(currency, now)
        
        # 开仓条件：信号满足 + 开仓时间窗口 + 不在冷却中
        signal_ok = check_signal(indicators)
        
        if signal_ok and entry_time_ok and not in_cooldown:
            result[currency] = 'buy'
        else:
            result[currency] = 'sell'
    
    # 按原始顺序排列
    ordered_result = {}
    for currency in currency_order:
        if currency in result:
            ordered_result[currency] = result[currency]
    
    print(f"[trading_signals] 解析完成: {ordered_result} (entry_time={entry_time_ok})")
    
    # 更新缓存
    if use_cache and ordered_result:
        with _cache_lock:
            _cache[cache_key] = (now, ordered_result)
    
    return ordered_result


# ========== 新增：平仓检查的API ==========
@api_view(["POST"])
def check_exit(request: Request) -> Response:
    """
    检查是否需要平仓
    
    POST /trading/check-exit
    Body: {
        "symbol": "BTC",
        "entry_price": 94000.0,
        "current_price": 94100.0,
        "targets": [...]  // 可选，默认使用DEFAULT_TARGETS
    }
    
    返回:
    {
        "should_exit": true/false,
        "exit_reason": "stop_loss_-0.0052",
        "profit_percent": -0.0052
    }
    """
    symbol = request.data.get("symbol")
    entry_price = request.data.get("entry_price")
    current_price = request.data.get("current_price")
    targets = request.data.get("targets", DEFAULT_TARGETS)
    
    if not all([symbol, entry_price, current_price]):
        return Response({"error": "symbol, entry_price, current_price are required"}, status=400)
    
    try:
        entry_price = float(entry_price)
        current_price = float(current_price)
    except (TypeError, ValueError):
        return Response({"error": "entry_price and current_price must be numbers"}, status=400)
    
    # 获取当前信号数据
    raw_data = fetch_signals(targets)
    if not raw_data:
        return Response({"error": "Failed to fetch signals"}, status=500)
    
    # 获取该币种的指标
    formatted = get_all_indicators_dict(raw_data, targets)
    if symbol not in formatted:
        return Response({"error": f"Symbol {symbol} not found"}, status=404)
    
    indicators = formatted[symbol]
    
    # 检查平仓
    should_exit, exit_reason, profit_pct = check_position_exit(
        symbol, entry_price, current_price, indicators
    )
    
    return Response({
        "should_exit": should_exit,
        "exit_reason": exit_reason,
        "profit_percent": profit_pct
    })


@api_view(["POST"])
def record_exit(request: Request) -> Response:
    """
    记录平仓（用于冷却管理）
    
    POST /trading/record-exit
    Body: {
        "symbol": "BTC",
        "exit_reason": "stop_loss",
        "profit_percent": -0.005
    }
    """
    symbol = request.data.get("symbol")
    exit_reason = request.data.get("exit_reason", "")
    profit_percent = request.data.get("profit_percent", 0)
    
    if not symbol:
        return Response({"error": "symbol is required"}, status=400)
    
    try:
        profit_percent = float(profit_percent)
    except (TypeError, ValueError):
        profit_percent = 0
    
    update_cooldown(symbol, exit_reason, profit_percent)
    reset_fifteen_counter(symbol)
    
    return Response({"status": "ok", "symbol": symbol})


# ========== 原有的API（保持不变）==========
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