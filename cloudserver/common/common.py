"""
apps/common.py
共享工具函数
"""
from typing import Dict, List, Optional

def check_signal(indicators: List[str]) -> bool:
    """
    买入信号检查（入场条件）- 前一根 KDJ 从末尾获取

    indicators 格式（原有索引不变，前一根 KDJ 追加在末尾）:
    索引: 
    0: 币种
    1: 价格
    2: 买卖
    3: 5m_SAR
    4: 5m_MACD
    5: 5m_KDJ
    6: 15m_SAR
    7: 15m_MACD
    8: 15m_KDJ
    9: 1h_SAR
    10: 1h_MACD
    11: 1h_KDJ
    12: 4h_SAR
    13: 4h_MACD
    14: 4h_KDJ
    15: 5m_PREV_KDJ   ← 追加
    16: 15m_PREV_KDJ  ← 追加
    17: 1h_PREV_KDJ   ← 追加
    18: 4h_PREV_KDJ   ← 追加

    条件（全部必须满足）:
    1. 4H级别: SAR・MACD・KDJ すべて〇
    2. 1H级别: SAR・MACD・KDJ すべて〇
    3. 15M级别: SAR・MACD・KDJ すべて〇
    4. 5M级别: SAR == 〇, 且当前 KDJ 和前一根 KDJ 都为〇
    """
    if len(indicators) < 15:
        return False

    # 1. 4H指标（索引12=SAR, 13=MACD, 14=KDJ）- 必须全〇
    four_hour_ok = (indicators[12] == '〇' and
                    indicators[13] == '〇' and
                    indicators[14] == '〇')

    # 2. 1H指标（索引9=SAR, 10=MACD, 11=KDJ）- 必须全〇
    one_hour_ok = (indicators[9]  == '〇' and
                   indicators[10] == '〇' and
                   indicators[11] == '〇')

    # 3. 15M指标（索引6=SAR, 7=MACD, 8=KDJ）- 必须全〇
    fifteen_ok = (indicators[6] == '〇' and
                  indicators[7] == '〇' and
                  indicators[8] == '〇')

    # 4. 5M指标 - SAR必须〇，且 KDJ 不是双重死叉
    #    双重死叉 = 前一根是 × 且 当前也是 ×
    # if len(indicators) >= 16:
    #     current_kdj = indicators[5]      # 当前 5m_KDJ
    #     prev_kdj = indicators[15]        # 前一根 5m_KDJ
        
    #     # 如果是双重死叉（前一根和当前都是×），则条件不满足
    #     is_double_death_cross = (prev_kdj == '×' and current_kdj == '×')
        
    #     five_m_ok = (indicators[3] == '〇' and indicators[4] == '〇' and not is_double_death_cross)
    # else:

    # 兼容旧版（无前一根判断）
    five_m_ok = sum([indicators[3] == '〇', indicators[4] == '〇', indicators[5] == '〇']) >= 2

    return four_hour_ok and one_hour_ok and fifteen_ok and five_m_ok



def get_all_indicators_dict(
    raw_data: Dict,
    targets: List[Dict] = None,
) -> Dict[str, List[str]]:
    """
    原始数据から各通貨の全指標を取り出す（前一根KDJ追加在末尾）

    返回値格式（19个元素）:
    索引:
    0: 币种
    1: 价格
    2: 买卖
    3: 5m_SAR
    4: 5m_MACD
    5: 5m_KDJ
    6: 15m_SAR
    7: 15m_MACD
    8: 15m_KDJ
    9: 1h_SAR
    10: 1h_MACD
    11: 1h_KDJ
    12: 4h_SAR
    13: 4h_MACD
    14: 4h_KDJ
    15: 5m_PREV_KDJ
    16: 15m_PREV_KDJ
    17: 1h_PREV_KDJ
    18: 4h_PREV_KDJ
    """
    if targets is None:
        targets = []

    results = raw_data.get('results', {})

    # 初始化数据结构（先创建15个元素的列表）
    formatted: Dict[str, List[str]] = {}
    for target in targets:
        symbol = target['symbol'].split('/')[0]
        if symbol not in formatted:
            # 15个元素的列表（索引0-14）
            formatted[symbol] = [
                symbol,      # 0: 币种
                '0',         # 1: 价格
                '—',         # 2: 买卖
                '—', '—', '—',  # 3:5m_SAR, 4:5m_MACD, 5:5m_KDJ
                '—', '—', '—',  # 6:15m_SAR, 7:15m_MACD, 8:15m_KDJ
                '—', '—', '—',  # 9:1h_SAR, 10:1h_MACD, 11:1h_KDJ
                '—', '—', '—'   # 12:4h_SAR, 13:4h_MACD, 14:4h_KDJ
            ]

    # 存储每个周期的前一根KDJ（临时）
    prev_kdj_store: Dict[str, Dict[str, str]] = {}

    for period_symbol, data in results.items():
        parts = period_symbol.split('_', 1)
        if len(parts) != 2:
            continue

        timeframe = parts[0]
        base_currency = parts[1].split('/')[0]

        if base_currency not in formatted:
            continue

        # data 格式: [symbol, datetime, price, sar, macd, kdj, kdj_over, prev_kdj]
        if len(data) >= 7:
            formatted[base_currency][1] = data[2]  # 价格
            
            sar_val = data[3]   # SAR
            macd_val = data[4]  # MACD
            kdj_val = data[5]   # KDJ
            prev_kdj_val = data[7] if len(data) > 7 else '—'  # 前一根 KDJ（第8个元素）

            # 存储到对应的索引（原有索引不变）
            if timeframe == '5m':
                formatted[base_currency][3] = sar_val   # 5m_SAR
                formatted[base_currency][4] = macd_val  # 5m_MACD
                formatted[base_currency][5] = kdj_val   # 5m_KDJ
                # 保存前一根KDJ稍后追加
                prev_kdj_store.setdefault(base_currency, {})['5m'] = prev_kdj_val
            elif timeframe == '15m':
                formatted[base_currency][6] = sar_val   # 15m_SAR
                formatted[base_currency][7] = macd_val  # 15m_MACD
                formatted[base_currency][8] = kdj_val   # 15m_KDJ
                prev_kdj_store.setdefault(base_currency, {})['15m'] = prev_kdj_val
            elif timeframe == '1h':
                formatted[base_currency][9] = sar_val   # 1h_SAR
                formatted[base_currency][10] = macd_val # 1h_MACD
                formatted[base_currency][11] = kdj_val  # 1h_KDJ
                prev_kdj_store.setdefault(base_currency, {})['1h'] = prev_kdj_val
            elif timeframe == '4h':
                formatted[base_currency][12] = sar_val  # 4h_SAR
                formatted[base_currency][13] = macd_val # 4h_MACD
                formatted[base_currency][14] = kdj_val  # 4h_KDJ
                prev_kdj_store.setdefault(base_currency, {})['4h'] = prev_kdj_val

    # 追加前一根KDJ到列表末尾（索引15-18）
    for currency, prev_kdjs in prev_kdj_store.items():
        formatted[currency].append(prev_kdjs.get('5m', '—'))   # 索引15: 5m_PREV_KDJ
        formatted[currency].append(prev_kdjs.get('15m', '—'))  # 索引16: 15m_PREV_KDJ
        formatted[currency].append(prev_kdjs.get('1h', '—'))   # 索引17: 1h_PREV_KDJ
        formatted[currency].append(prev_kdjs.get('4h', '—'))   # 索引18: 4h_PREV_KDJ

    # 判断买卖信号
    for currency, indicators in formatted.items():
        if check_signal(indicators):
            indicators[2] = 'buy'
        else:
            indicators[2] = 'sell'

    return formatted