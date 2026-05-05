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
    
    # buy/sell信号检查函数（新逻辑：结合15m、1h和4h指标，使用15m MACD替代15m KDJ）
    def check_signal(self, indicators: List[str]) -> bool:
        """
        检查买入信号
        条件：
        1. 15m_MACD 为 '〇'（替换原来的15m_KDJ）
        2. 1h_MACD 为 '〇'
        3. 1h_KDJ 为 '〇'
        4. 4h指标中至少有2个为 '〇'（检查4h_SAR、4h_MACD、4h_KDJ）
        
        参数indicators格式: 
        [币种, 价格, 信号, 15m_KDJ, 15m_MACD, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
        """
        if len(indicators) < 11:
            return False
        
        # 获取需要的指标
        macd_15m = indicators[4]     # 15m MACD（新条件）
        macd_1h = indicators[6]      # 1h MACD
        kdj_1h = indicators[7]       # 1h KDJ
        
        # 条件1-3：三个指标都必须为'〇'（使用15m MACD替换15m KDJ）
        basic_conditions_ok = (macd_15m == '〇' and macd_1h == '〇' and kdj_1h == '〇')
                
        # 条件4：4h指标检查（索引8=SAR, 9=MACD, 10=KDJ）
        signal_4h_count = sum(1 for ind in indicators[8:11] if ind == '〇')
        signal_4h_ok = signal_4h_count >= 2
                
        return basic_conditions_ok and signal_4h_ok
    
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

    # 开仓函数：增加30分钟冷却时间检查（新增15m_MACD字段，保留15m_KDJ）
    def open_trade(self, parsed_data: Dict[str, List[str]]):
        """保存交易信号到Firestore（适配您的数据结构）
        条件：
        1. 当前交易collection里没有任何数据时
        2. 距离上次平仓时间超过30分钟
        """
        try:
            # 检查当前交易collection是否有数据
            current_trades = self.db.collection(self.collection_current).get()
            
            if len(list(current_trades)) > 0:
                print(f"⚠️ 当前交易collection不为空，跳过开仓操作")
                return
            
            # 检查历史记录中最后一次平仓时间
            history_ref = self.db.collection(self.collection_history)
            # 按CLOSE_DATE降序排序，获取最新的平仓记录
            latest_trade = history_ref.order_by("CLOSE_DATE", direction=firestore.Query.DESCENDING).limit(1).get()
            
            latest_trade_list = list(latest_trade)
            if latest_trade_list:
                latest_doc = latest_trade_list[0]
                latest_close_date_str = latest_doc.get('CLOSE_DATE')
                
                if latest_close_date_str:
                    latest_close_date = datetime.fromisoformat(latest_close_date_str)
                    current_time = datetime.now(self.japan_tz)
                    
                    # 计算距离上次平仓的时间差（秒）
                    time_diff_seconds = (current_time - latest_close_date).total_seconds()
                    required_seconds = 30 * 60  # 30分钟 = 1800秒
                    
                    if time_diff_seconds < required_seconds:
                        # 距离上次平仓不足30分钟，禁止开仓
                        remaining_minutes = int((required_seconds - time_diff_seconds) // 60) + 1
                        print(f"⚠️ 冷却期检查未通过: 距离上次平仓仅 {int(time_diff_seconds // 60)} 分钟，需等待 {remaining_minutes} 分钟后才能开仓")
                        print(f"   上次平仓时间: {latest_close_date_str}")
                        return
                    else:
                        print(f"✅ 冷却期检查通过: 距离上次平仓 {int(time_diff_seconds // 60)} 分钟，允许开仓")
                else:
                    print(f"⚠️ 历史记录缺少 CLOSE_DATE 字段，继续开仓检查")
            else:
                print(f"ℹ️ 没有历史平仓记录，这是首次开仓")
            
            # 查找第一个buy信号
            buy_symbol = None
            buy_indicators = None
            
            for symbol, indicators in parsed_data.items():
                # 检查信号是否为buy（索引2是信号）
                if len(indicators) >= 3 and indicators[2] == 'buy':
                    buy_symbol = symbol
                    buy_indicators = indicators
                    break  # 找到第一个就退出
            
            # 如果没有找到buy信号，则不操作
            if buy_symbol is None:
                print(f"⚠️ parsed_data中没有找到buy信号，跳过开仓操作")
                return
            
            # 获取价格（索引1是价格）
            price = float(buy_indicators[1]) if buy_indicators[1] != '—' else 0
            
            # 创建新文档（使用symbol作为文档ID，确保同一币种不会重复开仓）
            doc_ref = self.db.collection(self.collection_current).document(buy_symbol)
            
            # 按照您的数据结构保存（新增15m_MACD字段，保留15m_KDJ）
            doc_data = {
                "SYMBOL": buy_symbol,
                "OPEN_DATE": datetime.now(self.japan_tz).isoformat(),
                "OPEN_PRICE": price,
                "OPEN_15M_KDJ": buy_indicators[3] if len(buy_indicators) > 3 else '—',   # 15m KDJ（保留）
                "OPEN_15M_MACD": buy_indicators[4] if len(buy_indicators) > 4 else '—',  # 15m MACD（新增）
                "OPEN_1H_SAR": buy_indicators[5] if len(buy_indicators) > 5 else '—',   # 1h SAR
                "OPEN_1H_MACD": buy_indicators[6] if len(buy_indicators) > 6 else '—',  # 1h MACD
                "OPEN_1H_KDJ": buy_indicators[7] if len(buy_indicators) > 7 else '—',   # 1h KDJ
                "OPEN_4H_SAR": buy_indicators[8] if len(buy_indicators) > 8 else '—',   # 4h SAR
                "OPEN_4H_MACD": buy_indicators[9] if len(buy_indicators) > 9 else '—',  # 4h MACD
                "OPEN_4H_KDJ": buy_indicators[10] if len(buy_indicators) > 10 else '—', # 4h KDJ
            }
            
            doc_ref.set(doc_data)
            print(f"✅ 开仓成功: {buy_symbol}, 价格: {price}, 时间: {doc_data['OPEN_DATE']}")
            
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