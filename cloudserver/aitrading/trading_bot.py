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
                    {'timeframe': '1h', 'symbol': 'ETH/USDT'},
                    {'timeframe': '4h', 'symbol': 'ETH/USDT'},
                    {'timeframe': '1h', 'symbol': 'BTC/USDT'},
                    {'timeframe': '4h', 'symbol': 'BTC/USDT'},
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
    # {
    # 'BTC': ['BTC', '50000', 'buy', '〇', '〇', '〇', '〇', '—', '〇'],
    # 'ETH': ['ETH', '3000', 'sell', '〇', '—', '〇', '—', '—', '〇']
    # }
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
            
            timeframe = parts[0]  # '1h' 或 '4h'
            symbol_with_slash = parts[1]  # 'BTC/USDT'
            base_currency = symbol_with_slash.split('/')[0]  # 'BTC'
            
            if len(data) >= 7:
                price = data[2]
                indicators = data[3:6]  # [SAR, MACD, KDJ]
                
                # 初始化或更新币种数据
                if base_currency not in formatted:
                    # 格式: [币种, 价格, 买卖信号, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ]
                    # 先临时用 '—' 占位信号，稍后会更新
                    formatted[base_currency] = [base_currency, price, '—', '—', '—', '—', '—', '—', '—']
                
                if timeframe == '1h':
                    # 索引调整：索引0是币种，索引1是价格，索引2是信号位，指标从索引3开始
                    formatted[base_currency][3:6] = indicators
                elif timeframe == '4h':
                    # 4h指标从索引6开始
                    formatted[base_currency][6:9] = indicators
        
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
    
    # buy/sell信号检查函数
    def check_signal(self, indicators: List[str]) -> bool:
        """
        检查买入信号
        条件：1h的3个指标全部为"〇"，4h至少有2个为"〇"
        参数indicators格式: [币种, 价格, 信号, 1h_sar, 1h_macd, 1h_kdj, 4h_sar, 4h_macd, 4h_kdj]
        """
        if len(indicators) < 9:
            return False
        
        # 1h指标检查（索引3,4,5）
        signal_1h_ok = all(ind == '〇' for ind in indicators[3:6])
        
        # 4h指标检查（索引6,7,8）
        signal_4h_count = sum(1 for ind in indicators[6:9] if ind == '〇')
        signal_4h_ok = signal_4h_count >= 2
        
        return signal_1h_ok and signal_4h_ok
    
    # 根据指标判断是否卖出
    def close_trade(self, parsed_data: Dict[str, List[str]]):
        """从Firestore删除交易信号（适配您的数据结构）"""
        try:
            for symbol, indicators in parsed_data.items():
                # 检查indicators的长度是否足够，并且第3个元素（索引2）是否为'sell'
                if len(indicators) >= 3 and indicators[2] == 'sell':
                    # 检查document是否存在
                    doc_ref = self.db.collection(self.collection_current).document(symbol)
                    doc = doc_ref.get()
                    
                    if doc.exists:
                        # 获取即将删除的开仓数据
                        open_data = doc.to_dict()
                        
                        # 保存到历史记录（平仓操作）
                        self.save_to_history(symbol, indicators, open_data)
                        
                        # 然后删除
                        doc_ref.delete()
                        print(f"✅ 从Firestore删除并保存到历史: {symbol}")
                    else:
                        print(f"⚠️ 无需删除，Firestore中不存在: {symbol}")
                else:
                    print(f"⏭️ 跳过删除 {symbol}: 信号为 {indicators[2] if len(indicators) >= 3 else 'unknown'}")
                    
        except Exception as e:
            print(f"❌ Firestore批量删除失败: {e}")
            import traceback
            traceback.print_exc()

    # 保存平仓记录到历史表
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
            
            # 构建历史记录文档
            history_data = {
                "SYMBOL": symbol,
                "OPEN_DATE": open_date_str,
                "OPEN_PRICE": round(open_price, 5),
                "OPEN_1H_SAR": open_data.get('OPEN_1H_SAR', '—'),
                "OPEN_1H_MACD": open_data.get('OPEN_1H_MACD', '—'),
                "OPEN_1H_KDJ": open_data.get('OPEN_1H_KDJ', '—'),
                "OPEN_4H_SAR": open_data.get('OPEN_4H_SAR', '—'),
                "OPEN_4H_MACD": open_data.get('OPEN_4H_MACD', '—'),
                "OPEN_4H_KDJ": open_data.get('OPEN_4H_KDJ', '—'),
                "CLOSE_DATE": close_date_str,
                "CLOSE_PRICE": round(close_price, 5),
                "CLOSE_1H_SAR": current_indicators[3] if len(current_indicators) > 3 else '—',
                "CLOSE_1H_MACD": current_indicators[4] if len(current_indicators) > 4 else '—',
                "CLOSE_1H_KDJ": current_indicators[5] if len(current_indicators) > 5 else '—',
                "CLOSE_4H_SAR": current_indicators[6] if len(current_indicators) > 6 else '—',
                "CLOSE_4H_MACD": current_indicators[7] if len(current_indicators) > 7 else '—',
                "CLOSE_4H_KDJ": current_indicators[8] if len(current_indicators) > 8 else '—',
                "PROFIT_OR_LOSS": round(profit_or_loss, 5),
                "PROFIT_OR_LOSS_PERCENT": round(profit_or_loss_percent, 5),
                "HOLD_TIME": hold_time  # 新增持仓时间字段
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

    # 开仓函数：根据parsed_data中的第一个buy信号进行开仓，并保存到Firestore（适配您的数据结构）
    def open_trade(self, parsed_data: Dict[str, List[str]]):
        """保存交易信号到Firestore（适配您的数据结构）
        条件：当前交易collection里没有任何数据时，才根据parsed_data中第一个buy信号进行开仓
        """
        try:
            # 检查当前交易collection是否有数据
            current_trades = self.db.collection(self.collection_current).get()
            
            if len(list(current_trades)) > 0:
                print(f"⚠️ 当前交易collection不为空，跳过开仓操作")
                return
            
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
            
            # 按照您的数据结构保存
            doc_data = {
                "SYMBOL": buy_symbol,
                "OPEN_DATE": datetime.now(self.japan_tz).isoformat(),
                "OPEN_PRICE": price,
                "OPEN_1H_SAR": buy_indicators[3] if len(buy_indicators) > 3 else '—',   # 1h SAR
                "OPEN_1H_MACD": buy_indicators[4] if len(buy_indicators) > 4 else '—',  # 1h MACD
                "OPEN_1H_KDJ": buy_indicators[5] if len(buy_indicators) > 5 else '—',   # 1h KDJ
                "OPEN_4H_SAR": buy_indicators[6] if len(buy_indicators) > 6 else '—',   # 4h SAR
                "OPEN_4H_MACD": buy_indicators[7] if len(buy_indicators) > 7 else '—',  # 4h MACD
                "OPEN_4H_KDJ": buy_indicators[8] if len(buy_indicators) > 8 else '—',   # 4h KDJ
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