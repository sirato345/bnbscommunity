"""
apps/common.py
共享工具函数
"""
from typing import Dict, List, Optional

def check_signal(indicators: List[str]) -> bool:
    """
    买入信号检查（入场条件）- 基于历史回测最佳规则

    indicators 格式:
    [币种, 价格, 买卖, 5m_SAR, 5m_MACD, 5m_KDJ, 15m_SAR, 15m_MACD, 15m_KDJ, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
    
    索引说明:
    [0]币种, [1]价格, [2]买卖, [3]5m_SAR, [4]5m_MACD, [5]5m_KDJ, 
    [6]15m_SAR, [7]15m_MACD, [8]15m_KDJ, [9]1h_SAR, [10]1h_MACD, [11]1h_KDJ,
    [12]4h_SAR, [13]4h_MACD, [14]4h_KDJ

    最佳规则（胜率76.9%，盈亏比2.5）:
    1. 4H级别: SAR・MACD・KDJ 全部为〇（3/3）
    2. 1H级别: SAR・MACD・KDJ 全部为〇（3/3）
    3. 15M级别: SAR必须为〇 + (MACD或KDJ至少一个为〇) → 至少2个金叉且强制SAR
    4. 5M级别: SAR必须为〇 + (MACD或KDJ至少一个为〇) → 至少2个金叉且强制SAR
    """
    if len(indicators) < 15:
        return False

    # ========== 1. 4H级别（必须全部为〇）==========
    # 索引12=4h_SAR, 13=4h_MACD, 14=4h_KDJ
    four_hour_ok = (indicators[12] == '〇' and
                    indicators[13] == '〇' and
                    indicators[14] == '〇')

    # ========== 2. 1H级别（必须全部为〇）==========
    # 索引9=1h_SAR, 10=1h_MACD, 11=1h_KDJ
    one_hour_ok = (indicators[9]  == '〇' and
                   indicators[10] == '〇' and
                   indicators[11] == '〇')

    # ========== 3. 15M级别（强制SAR + 至少一个其他）==========
    # 索引6=15m_SAR, 7=15m_MACD, 8=15m_KDJ
    fifteen_sar_ok = (indicators[6] == '〇')                                    # SAR必须〇
    fifteen_other_ok = (indicators[7] == '〇' or indicators[8] == '〇')         # MACD或KDJ至少一个〇
    fifteen_ok = fifteen_sar_ok and fifteen_other_ok

    # ========== 4. 5M级别（强制SAR + 至少一个其他）==========
    # 索引3=5m_SAR, 4=5m_MACD, 5=5m_KDJ
    five_sar_ok = (indicators[3] == '〇')                                      # SAR必须〇
    five_other_ok = (indicators[4] == '〇' or indicators[5] == '〇')           # MACD或KDJ至少一个〇
    five_ok = five_sar_ok and five_other_ok

    # ========== 最终判断 ==========
    return four_hour_ok and one_hour_ok and fifteen_ok and five_ok

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