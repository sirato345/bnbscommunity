"""
apps/common.py
共享工具函数
"""
from typing import Dict, List, Optional


def check_signal(indicators: List[str]) -> bool:
    """
    买入信号检查（入场条件）

    indicators 格式:
    [币种, 价格, 买卖, 5m_SAR, 5m_MACD, 5m_KDJ, 15m_SAR, 15m_MACD, 15m_KDJ, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]

    条件:
    1. 4時間: SAR・MACD・KDJ すべて〇
    2. 1時間: SAR・MACD・KDJ すべて〇
    3. 15分: 三つすべて〇、または二つ以上が〇かつ5mのSARが〇
    """
    if len(indicators) < 15:
        return False

    # 1. 4h指標（インデックス12=SAR, 13=MACD, 14=KDJ）- すべて〇
    four_hour_ok = (indicators[12] == '〇' and
                    indicators[13] == '〇' and
                    indicators[14] == '〇')

    # 2. 1h指標（インデックス9=SAR, 10=MACD, 11=KDJ）- すべて〇
    one_hour_ok = (indicators[9]  == '〇' and
                   indicators[10] == '〇' and
                   indicators[11] == '〇')

    # 3. 15m指標（インデックス6=SAR, 7=MACD, 8=KDJ）
    fifteen_indicators = [indicators[6], indicators[7], indicators[8]]
    fifteen_ok_count = sum(1 for ind in fifteen_indicators if ind == '〇')

    five_m_sar_ok = (indicators[3] == '〇')  # 5m_SAR（インデックス3）

    fifteen_ok = (
        fifteen_ok_count == 3  # 三つすべて〇
        or (fifteen_ok_count >= 2 and five_m_sar_ok)  # 二つ以上〇 かつ 5m_SARが〇
    )

    return four_hour_ok and one_hour_ok and fifteen_ok


def get_all_indicators_dict(
    raw_data: Dict,
    targets: List[Dict] = None,
) -> Dict[str, List[str]]:
    """
    原始数据から各通貨の全指標を取り出す

    Args:
        raw_data: 包含 'results' 键的原始数据
        targets: 目标列表，每个元素包含 'symbol' 和 'timeframe'

    返回値:
    {
        "BTC": [币种, 价格, 买卖, 5m_SAR, 5m_MACD, 5m_KDJ, 15m_SAR, 15m_MACD, 15m_KDJ, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ],
        ...
    }
    """
    if targets is None:
        targets = []

    results = raw_data.get('results', {})

    formatted: Dict[str, List[str]] = {}
    for target in targets:
        symbol = target['symbol'].split('/')[0]
        if symbol not in formatted:
            # [币种, 价格, 买卖, 5m_SAR, 5m_MACD, 5m_KDJ, 15m_SAR, 15m_MACD, 15m_KDJ, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
            formatted[symbol] = [symbol, '0', '—', '—', '—', '—', '—', '—', '—', '—', '—', '—', '—', '—', '—']

    for period_symbol, data in results.items():
        parts = period_symbol.split('_', 1)
        if len(parts) != 2:
            continue

        timeframe = parts[0]
        base_currency = parts[1].split('/')[0]

        if base_currency not in formatted:
            continue

        if len(data) >= 7:
            formatted[base_currency][1] = data[2]  # 価格
            ind = data[3:6]  # [SAR, MACD, KDJ]

            if timeframe == '5m':
                formatted[base_currency][3] = ind[0]  # 5m_SAR
                formatted[base_currency][4] = ind[1]  # 5m_MACD
                formatted[base_currency][5] = ind[2]  # 5m_KDJ
            elif timeframe == '15m':
                formatted[base_currency][6] = ind[0]  # 15m_SAR
                formatted[base_currency][7] = ind[1]  # 15m_MACD
                formatted[base_currency][8] = ind[2]  # 15m_KDJ
            elif timeframe == '1h':
                formatted[base_currency][9]  = ind[0]  # 1h_SAR
                formatted[base_currency][10] = ind[1]  # 1h_MACD
                formatted[base_currency][11] = ind[2]  # 1h_KDJ
            elif timeframe == '4h':
                formatted[base_currency][12] = ind[0]  # 4h_SAR
                formatted[base_currency][13] = ind[1]  # 4h_MACD
                formatted[base_currency][14] = ind[2]  # 4h_KDJ

    for currency, indicators in formatted.items():
        if check_signal(indicators):
            indicators[2] = 'buy'
        else:
            indicators[2] = 'sell'

    return formatted