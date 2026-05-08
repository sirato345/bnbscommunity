#!/usr/bin/env python
import json
import os
import sys
from datetime import datetime
from typing import Dict, List, Optional
from datetime import datetime
import pytz

import requests

# 将项目根目录添加到 Python 路径
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

# 设置 Django 环境
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

import django

django.setup()

from google.cloud import firestore


class TradingSignalJob:
    def __init__(self):
        self.api_url = "https://bnbs-django-275599637949.asia-northeast1.run.app/signals"
        self.db = firestore.Client(database='aitrading')
        self.collection_current = "CURRENT_TRADE"
        self.collection_history = "TRADE_HISTORY"# 固定文档ID，方便更新和删除
        self.japan_tz = pytz.timezone('Asia/Tokyo')
    
    # 发送POST请求获取交易信号
    def fetch_signals(self) -> Optional[Dict]:
        """发送POST请求获取交易信号（使用正确的请求格式）"""
        try:
            # 按照前端代码的格式准备请求体
            self.payload = {
                "targets": [
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
            }
            headers = {'Content-Type': 'application/json'}
            
            print(f"发送请求到: {self.api_url}")
            print(f"请求体: {json.dumps(self.payload, indent=2)}")
            
            response = requests.post(
                self.api_url,
                json=self.payload,
                headers=headers,
                timeout=30
            )
            response.raise_for_status()
            
            print(f"✅ API响应成功: {response.status_code}")
            return response.json()
            
        except requests.exceptions.RequestException as e:
            print(f"❌ API请求失败: {e}")
            if hasattr(e, 'response') and e.response is not None:
                print(f"响应状态码: {e.response.status_code}")
                print(f"响应内容: {e.response.text}")
            return None
    
    # 解析信号数据，格式化为需要的结构
    # 新格式: [币种, 价格, 买卖信号, 15m_KDJ, 15m_MACD, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
    def parse_signals(self, raw_data: Dict) -> Dict[str, List[str]]:
        """解析信号数据，格式化为需要的结构"""
        formatted = {}
        results = raw_data.get('results', {})
        
        print(f"解析原始数据，共 {len(results)} 条记录")

        # 从 payload 动态获取币种顺序（排序用）
        currency_order = []
        if hasattr(self, 'payload') and 'targets' in self.payload:
            for target in self.payload['targets']:
                symbol = target['symbol'].split('/')[0]  # 'BTC/USDT' -> 'BTC'
                if symbol not in currency_order:
                    currency_order.append(symbol)
        
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
                    # 新格式: [币种, 价格, 买卖信号, 15m_KDJ, 15m_MACD, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
                    # 共11个元素 (索引0-10)
                    formatted[base_currency] = [base_currency, price, '—', '—', '—', '—', '—', '—', '—', '—', '—']
                
                if timeframe == '15m':
                    # 15m指标：索引3=KDJ, 索引4=MACD
                    formatted[base_currency][3] = indicators[2]  # KDJ指标
                    formatted[base_currency][4] = indicators[1]  # MACD指标（新增）
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
        
        # 所有数据填充完成后，检查每个币种的买卖信号
        for currency, indicators in formatted.items():
            # 调用check_signal判断
            if self.check_signal(indicators):
                indicators[2] = 'buy'   # 在币种和price后面(索引2)设置buy
            else:
                indicators[2] = 'sell'  # 否则设置sell

        # 按照动态获取的顺序重新排列(排序用)
        ordered_formatted = {}
        for currency in currency_order:
            if currency in formatted:
                ordered_formatted[currency] = formatted[currency]
        
        return ordered_formatted
    
    # buy/sell信号检查函数（新逻辑）
    def check_signal(self, indicators: List[str]) -> bool:
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
        
        # 规则1：1h和4h的全部六个指标都为 '〇'
        if all(ind == '〇' for ind in one_hour_indicators + four_hour_indicators):
            return True
        
        # 规则2：
        # 条件a：1h_MACD 和 1h_KDJ 都为 '〇'（索引6和7）
        condition_a = indicators[6] == '〇' and indicators[7] == '〇'
        # 条件b：4h至少有两个为 '〇'
        condition_b = sum(1 for ind in four_hour_indicators if ind == '〇') >= 2
        # 条件c：15m_MACD 为 '〇'（索引4）
        condition_c = indicators[4] == '〇'
        
        return condition_a and condition_b and condition_c
    
    # 根据指标判断是否卖出，增加9分钟持仓时间检查
    def close_trade(self, parsed_data: Dict[str, List[str]]):
        """从Firestore删除交易信号（适配您的数据结构）
        条件：必须持仓超过9分钟才能平仓
        """
        try:
            for symbol, indicators in parsed_data.items():
                # 检查indicators的长度是否足够，并且第3个元素（索引2）是否为'sell'
                if len(indicators) >= 3 and indicators[2] == 'sell':
                    # 检查document是否存在
                    doc_ref = self.db.collection(self.collection_current).document(symbol)
                    doc = doc_ref.get()
                    
                    if doc.exists:
                        # 获取开仓数据
                        open_data = doc.to_dict()
                        open_date_str = open_data.get('OPEN_DATE')
                        
                        if open_date_str:
                            # 解析开仓时间
                            open_date = datetime.fromisoformat(open_date_str)
                            current_time = datetime.now(self.japan_tz)
                            
                            # 计算持仓时间差（秒）
                            time_diff_seconds = (current_time - open_date).total_seconds()
                            required_seconds = 9 * 60  # 9分钟 = 540秒
                            
                            if time_diff_seconds >= required_seconds:
                                # 持仓超过9分钟，允许平仓
                                print(f"✅ 持仓时间检查通过: {symbol} 持仓 {int(time_diff_seconds // 60)} 分钟，允许平仓")
                                
                                # 保存到历史记录（平仓操作）
                                self.save_to_history(symbol, indicators, open_data)
                                
                                # 然后删除
                                doc_ref.delete()
                                print(f"✅ 从Firestore删除并保存到历史: {symbol}")
                            else:
                                # 持仓不足9分钟
                                remaining_minutes = int((required_seconds - time_diff_seconds) // 60) + 1
                                print(f"⚠️ 持仓不足9分钟，跳过平仓: {symbol} 已持仓 {int(time_diff_seconds // 60)} 分钟，需等待 {remaining_minutes} 分钟后平仓")
                        else:
                            print(f"⚠️ {symbol} 缺少 OPEN_DATE 字段，跳过平仓")
                    else:
                        print(f"⚠️ 无需删除，Firestore中不存在: {symbol}")
                else:
                    print(f"⏭️ 跳过删除 {symbol}: 信号为 {indicators[2] if len(indicators) >= 3 else 'unknown'}")
                    
        except Exception as e:
            print(f"❌ Firestore批量删除失败: {e}")
            import traceback
            traceback.print_exc()

    # 保存平仓记录到历史表（新增15m_MACD字段，保留15m_KDJ）
    def save_to_history(self, symbol: str, current_indicators: List[str], open_data: dict):
        """保存平仓记录到历史表"""
        try:
            # 获取开仓价格（从即将删除的数据中获取）
            open_price = float(open_data.get('OPEN_PRICE', 0))
            
            # 获取当前平仓价格（从当前indicators中获取，索引1是价格）
            close_price = float(current_indicators[1]) if current_indicators[1] != '—' else 0
            
            # 解析开仓时间和平仓时间
            open_date_str = open_data.get('OPEN_DATE', datetime.now(self.japan_tz).isoformat())
            close_date_str = datetime.now(self.japan_tz).isoformat()
            
            # 转换时间为datetime对象
            from datetime import datetime as dt
            open_date = dt.fromisoformat(open_date_str)
            close_date = dt.fromisoformat(close_date_str)
            
            # 计算持仓时间差
            time_diff = close_date - open_date
            total_hours = int(time_diff.total_seconds() // 3600)
            total_minutes = int((time_diff.total_seconds() % 3600) // 60)
            hold_time = f"{total_hours}h{total_minutes}m"
            
            # 计算盈亏
            profit_or_loss = close_price - open_price
            profit_or_loss_percent = profit_or_loss / open_price if open_price != 0 else 0
            
            # 构建历史记录文档（新增15m_MACD字段，保留15m_KDJ）
            history_data = {
                "SYMBOL": symbol,
                "OPEN_DATE": open_date_str,
                "OPEN_PRICE": round(open_price, 5),
                "OPEN_15M_KDJ": open_data.get('OPEN_15M_KDJ', '—'),
                "OPEN_15M_MACD": open_data.get('OPEN_15M_MACD', '—'),      # 新增
                "OPEN_1H_SAR": open_data.get('OPEN_1H_SAR', '—'),
                "OPEN_1H_MACD": open_data.get('OPEN_1H_MACD', '—'),
                "OPEN_1H_KDJ": open_data.get('OPEN_1H_KDJ', '—'),
                "OPEN_4H_SAR": open_data.get('OPEN_4H_SAR', '—'),
                "OPEN_4H_MACD": open_data.get('OPEN_4H_MACD', '—'),
                "OPEN_4H_KDJ": open_data.get('OPEN_4H_KDJ', '—'),
                "CLOSE_DATE": close_date_str,
                "CLOSE_PRICE": round(close_price, 5),
                "CLOSE_15M_KDJ": current_indicators[3] if len(current_indicators) > 3 else '—',
                "CLOSE_15M_MACD": current_indicators[4] if len(current_indicators) > 4 else '—',  # 新增
                "CLOSE_1H_SAR": current_indicators[5] if len(current_indicators) > 5 else '—',
                "CLOSE_1H_MACD": current_indicators[6] if len(current_indicators) > 6 else '—',
                "CLOSE_1H_KDJ": current_indicators[7] if len(current_indicators) > 7 else '—',
                "CLOSE_4H_SAR": current_indicators[8] if len(current_indicators) > 8 else '—',
                "CLOSE_4H_MACD": current_indicators[9] if len(current_indicators) > 9 else '—',
                "CLOSE_4H_KDJ": current_indicators[10] if len(current_indicators) > 10 else '—',
                "PROFIT_OR_LOSS": round(profit_or_loss, 5),
                "PROFIT_OR_LOSS_PERCENT": round(profit_or_loss_percent, 5),
                "HOLD_TIME": hold_time
            }
            
            # 使用时间戳作为文档ID
            doc_id = datetime.now(self.japan_tz).isoformat()
            doc_ref = self.db.collection(self.collection_history).document(doc_id)
            doc_ref.set(history_data)
            
            print(f"✅ 保存平仓记录到历史: {symbol}, 开仓价: {open_price}, 平仓价: {close_price}, 盈亏: {round(profit_or_loss, 5)}, 持仓时间: {hold_time}")
                
        except Exception as e:
            print(f"❌ 保存历史记录失败 {symbol}: {e}")
            import traceback
            traceback.print_exc()

    # 开仓函数：遍历所有Symbol，找出第一个符合开仓条件的
    def open_trade(self, parsed_data: Dict[str, List[str]]):
        """保存交易信号到Firestore（适配您的数据结构）
        条件：
        1. 当前交易collection里没有任何数据时
        2. 距离上次平仓时间需要超过冷却时间：
        - 如果当前交易和上一次交易的SYMBOL一样：需要超过60分钟
        - 如果SYMBOL不同或首次开仓：需要超过30分钟
        3. 遍历所有有buy信号的Symbol，选择第一个符合条件的开仓
        """
        try:
            # 检查当前交易collection是否有数据
            current_trades = self.db.collection(self.collection_current).get()
            
            if len(list(current_trades)) > 0:
                print(f"⚠️ 当前交易collection不为空，跳过开仓操作")
                return
            
            # 收集所有有buy信号的Symbol
            buy_symbols = []
            for symbol, indicators in parsed_data.items():
                # 检查信号是否为buy（索引2是信号）
                if len(indicators) >= 3 and indicators[2] == 'buy':
                    buy_symbols.append((symbol, indicators))
            
            if not buy_symbols:
                print(f"⚠️ parsed_data中没有找到buy信号，跳过开仓操作")
                return
            
            print(f"📊 找到 {len(buy_symbols)} 个buy信号: {[s[0] for s in buy_symbols]}")
            
            # 获取历史记录中最后一次平仓记录
            history_ref = self.db.collection(self.collection_history)
            latest_trade = history_ref.order_by("CLOSE_DATE", direction=firestore.Query.DESCENDING).limit(1).get()
            latest_trade_list = list(latest_trade)
            
            # 遍历所有buy信号，找到第一个符合条件的
            for buy_symbol, buy_indicators in buy_symbols:
                print(f"\n🔍 检查 {buy_symbol} 是否符合开仓条件...")
                
                # 默认允许开仓的标志
                can_open = True
                skip_reason = ""
                
                # 如果有历史平仓记录，进行冷却时间检查
                if latest_trade_list:
                    latest_doc = latest_trade_list[0]
                    latest_close_date_str = latest_doc.get('CLOSE_DATE')
                    last_symbol = latest_doc.get('SYMBOL')
                    
                    if latest_close_date_str:
                        latest_close_date = datetime.fromisoformat(latest_close_date_str)
                        current_time = datetime.now(self.japan_tz)
                        
                        # 计算距离上次平仓的时间差（秒）
                        time_diff_seconds = (current_time - latest_close_date).total_seconds()
                        
                        # 根据SYMBOL是否相同，决定冷却时间
                        if last_symbol == buy_symbol:
                            # SYMBOL相同：需要60分钟冷却
                            required_seconds = 60 * 60  # 60分钟 = 3600秒
                            cooling_type = "60分钟（相同SYMBOL）"
                        else:
                            # SYMBOL不同：需要30分钟冷却
                            required_seconds = 30 * 60  # 30分钟 = 1800秒
                            cooling_type = "30分钟（不同SYMBOL）"
                        
                        if time_diff_seconds < required_seconds:
                            # 冷却时间不足，禁止开仓
                            remaining_minutes = int((required_seconds - time_diff_seconds) // 60) + 1
                            print(f"   ❌ 冷却期检查未通过: {cooling_type}")
                            print(f"      当前SYMBOL: {buy_symbol}, 上一次SYMBOL: {last_symbol}")
                            print(f"      距离上次平仓仅 {int(time_diff_seconds // 60)} 分钟，需等待 {remaining_minutes} 分钟后才能开仓")
                            print(f"      上次平仓时间: {latest_close_date_str}")
                            can_open = False
                            skip_reason = f"冷却期不足（需要{cooling_type}）"
                        else:
                            print(f"   ✅ 冷却期检查通过: {cooling_type}")
                            print(f"      当前SYMBOL: {buy_symbol}, 上一次SYMBOL: {last_symbol}")
                            print(f"      距离上次平仓 {int(time_diff_seconds // 60)} 分钟 >= {required_seconds // 60} 分钟")
                    else:
                        print(f"   ⚠️ 历史记录缺少 CLOSE_DATE 字段，跳过冷却检查")
                else:
                    print(f"   ℹ️ 没有历史平仓记录，这是首次开仓，允许开仓")
                
                # 如果符合条件，执行开仓
                if can_open:
                    # 获取价格（索引1是价格）
                    price = float(buy_indicators[1]) if buy_indicators[1] != '—' else 0
                    
                    # 创建新文档（使用symbol作为文档ID，确保同一币种不会重复开仓）
                    doc_ref = self.db.collection(self.collection_current).document(buy_symbol)
                    
                    # 按照您的数据结构保存
                    doc_data = {
                        "SYMBOL": buy_symbol,
                        "OPEN_DATE": datetime.now(self.japan_tz).isoformat(),
                        "OPEN_PRICE": price,
                        "OPEN_15M_KDJ": buy_indicators[3] if len(buy_indicators) > 3 else '—',
                        "OPEN_15M_MACD": buy_indicators[4] if len(buy_indicators) > 4 else '—',
                        "OPEN_1H_SAR": buy_indicators[5] if len(buy_indicators) > 5 else '—',
                        "OPEN_1H_MACD": buy_indicators[6] if len(buy_indicators) > 6 else '—',
                        "OPEN_1H_KDJ": buy_indicators[7] if len(buy_indicators) > 7 else '—',
                        "OPEN_4H_SAR": buy_indicators[8] if len(buy_indicators) > 8 else '—',
                        "OPEN_4H_MACD": buy_indicators[9] if len(buy_indicators) > 9 else '—',
                        "OPEN_4H_KDJ": buy_indicators[10] if len(buy_indicators) > 10 else '—',
                    }
                    
                    doc_ref.set(doc_data)
                    print(f"\n✅ 开仓成功: {buy_symbol}, 价格: {price}, 时间: {doc_data['OPEN_DATE']}")
                    return  # 成功开仓后退出函数
                else:
                    print(f"   ⚠️ {buy_symbol} 不符合开仓条件: {skip_reason}")
            
            # 遍历完所有Symbol都没有符合条件的
            print(f"\n⚠️ 所有 {len(buy_symbols)} 个buy信号都不符合开仓条件，本次不开仓")
            
        except Exception as e:
            print(f"❌ 开仓失败: {e}")
            import traceback
            traceback.print_exc()
    
    def run(self):
        """主执行函数"""
        print(f"🚀 交易信号Job开始执行: {datetime.now(self.japan_tz)}")
        print("=" * 50)
        
        # 1. 获取信号数据
        raw_data = self.fetch_signals()
        if not raw_data:
            print("❌ 无法获取信号数据，Job终止")
            return
        
        # 检查是否有错误
        if 'errors' in raw_data and raw_data['errors']:
            print(f"⚠️ API返回错误: {raw_data['errors']}")
        
        # 2. 解析数据
        parsed_data = self.parse_signals(raw_data)
        print(f"\n📈 解析后的数据:")
        for symbol, indicators in parsed_data.items():
            print(f"  {symbol}: {indicators}")
        
        # 3.平仓检查并保存历史记录 
        self.close_trade(parsed_data)

        # 4. 检查买入信号并保存
        self.open_trade(parsed_data)
        
        print("\n" + "=" * 50)
        print(f"✅ Job执行完成: {datetime.now(self.japan_tz)}")

def main():
    job = TradingSignalJob()
    job.run()

if __name__ == "__main__":
    main()