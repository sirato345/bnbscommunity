#!/usr/bin/env python
import os
import sys
from datetime import datetime
from typing import Dict, List, Optional

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

# 导入 trading_signals 中的函数（复用逻辑）
from common.common import (
    get_all_indicators_dict,
)


class TradingSignalJob:
    def __init__(self):
        self.signals_service_url = os.environ.get(
            'SIGNALS_SERVICE_URL',
            'https://bnbs-django-275599637949.asia-northeast1.run.app/signals'
        )
        self.db = firestore.Client(database='aitrading')
        self.collection_current = "CURRENT_TRADE"
        self.collection_history = "TRADE_HISTORY"
        self.japan_tz = pytz.timezone('Asia/Tokyo')
        self.payload = None
    
    # 发送POST请求获取交易信号
    def fetch_signals_data(self) -> Optional[Dict]:
        """调用独立的 signals 服务获取信号数据"""
        try:
            self.payload = {
                "targets": [
                    {'timeframe': '15m', 'symbol': 'BTC/USDT'},
                    {'timeframe': '1h', 'symbol': 'BTC/USDT'},
                    {'timeframe': '4h', 'symbol': 'BTC/USDT'},
                    {'timeframe': '15m', 'symbol': 'ETH/USDT'},
                    {'timeframe': '1h', 'symbol': 'ETH/USDT'},
                    {'timeframe': '4h', 'symbol': 'ETH/USDT'},
                    {'timeframe': '15m', 'symbol': 'BNB/USDT'},
                    {'timeframe': '1h', 'symbol': 'BNB/USDT'},
                    {'timeframe': '4h', 'symbol': 'BNB/USDT'},
                    {'timeframe': '15m', 'symbol': 'DOGE/USDT'},
                    {'timeframe': '1h', 'symbol': 'DOGE/USDT'},
                    {'timeframe': '4h', 'symbol': 'DOGE/USDT'},
                ]
            }
            
            print(f"调用 signals 服务: {self.signals_service_url}")
            
            response = requests.post(
                self.signals_service_url,
                json=self.payload,
                headers={'Content-Type': 'application/json'},
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
    
    # 解析信号数据
    def parse_signals_data(self, raw_data: Dict) -> Dict[str, List[str]]:
        """
        解析信号数据，返回完整指标格式
        
        返回格式:
        {
            "BTC": [币种, 价格, 买卖, 15m_SAR, 15m_MACD, 15m_KDJ, 1h_SAR, 1h_MACD, 1h_KDJ, 4h_SAR, 4h_MACD, 4h_KDJ],
            ...
        }
        """
        if not raw_data:
            return {}
        
        results = raw_data.get('results', {})
        if not results:
            print(f"没有找到信号数据")
            return {}
        
        print(f"解析原始数据，共 {len(results)} 条记录")
        
        # 使用 trading_signals 的辅助函数获取指标
        formatted = get_all_indicators_dict(raw_data, self.payload.get('targets'))
        
        # 按 payload 中的顺序排列（多线程，无顺序）
        currency_order = []
        if self.payload and 'targets' in self.payload:
            for target in self.payload['targets']:
                symbol = target['symbol'].split('/')[0]
                if symbol not in currency_order:
                    currency_order.append(symbol)
        
        ordered_formatted = {}
        for currency in currency_order:
            if currency in formatted:
                ordered_formatted[currency] = formatted[currency]
        
        return ordered_formatted
    
    # 检查冷却时间（从历史记录中获取最后一次平仓）
    def check_cooldown_from_history(self, symbol: str) -> tuple[bool, int, str]:
        """
        从历史记录检查冷却状态
        返回: (是否在冷却中, 剩余秒数, 上次平仓的symbol)
        规则: 盈利平仓无冷却，亏损平仓冷却30分钟
        """
        history_ref = self.db.collection(self.collection_history)
        latest_trade = history_ref.order_by("CLOSE_DATE", direction=firestore.Query.DESCENDING).limit(1).get()
        latest_trade_list = list(latest_trade)
        
        if not latest_trade_list:
            return False, 0, ""
        
        latest_doc = latest_trade_list[0]
        last_close_date_str = latest_doc.get('CLOSE_DATE')
        last_symbol = latest_doc.get('SYMBOL')
        last_profit = latest_doc.get('PROFIT_OR_LOSS_PERCENT', 0)
        
        if not last_close_date_str:
            return False, 0, last_symbol
        
        last_close_date = datetime.fromisoformat(last_close_date_str)
        current_time = datetime.now(self.japan_tz)
        time_diff_seconds = (current_time - last_close_date).total_seconds()
        
        # 盈利平仓无冷却
        if last_profit > 0:
            return False, 0, last_symbol
        
        # 亏损平仓：统一冷却30分钟（简化规则）
        required_seconds = 30 * 60
        
        if time_diff_seconds < required_seconds:
            remaining = int(required_seconds - time_diff_seconds)
            return True, remaining, last_symbol
        
        return False, 0, last_symbol
    
    # 平仓检查（完全由指标决定，删除持仓时间判断）
    def close_trade(self, parsed_data: Dict[str, List[str]]):
        """检查并执行平仓（完全由指标决定）"""
        try:
            for symbol, indicators in parsed_data.items():
                if len(indicators) >= 3 and indicators[2] == 'sell':
                    doc_ref = self.db.collection(self.collection_current).document(symbol)
                    doc = doc_ref.get()
                    
                    if doc.exists:
                        open_data = doc.to_dict()
                        
                        # 直接平仓，不检查持仓时间
                        self.save_to_history(symbol, indicators, open_data)
                        doc_ref.delete()
                        print(f"✅ 指标触发平仓: {symbol}")
                    else:
                        print(f"⚠️ Firestore中不存在持仓: {symbol}")
                        
        except Exception as e:
            print(f"❌ 平仓失败: {e}")
            import traceback
            traceback.print_exc()
    
    # 保存平仓记录到历史表
    def save_to_history(self, symbol: str, current_indicators: List[str], open_data: dict):
        """保存平仓记录到历史表"""
        try:
            open_price = float(open_data.get('OPEN_PRICE', 0))
            close_price = float(current_indicators[1]) if current_indicators[1] != '—' else 0
            
            open_date_str = open_data.get('OPEN_DATE', datetime.now(self.japan_tz).isoformat())
            close_date_str = datetime.now(self.japan_tz).isoformat()
            
            open_date = datetime.fromisoformat(open_date_str)
            close_date = datetime.fromisoformat(close_date_str)
            
            time_diff = close_date - open_date
            total_hours = int(time_diff.total_seconds() // 3600)
            total_minutes = int((time_diff.total_seconds() % 3600) // 60)
            hold_time = f"{total_hours}h{total_minutes}m"
            
            profit_or_loss = close_price - open_price
            profit_or_loss_percent = profit_or_loss / open_price if open_price != 0 else 0
            
            history_data = {
                "SYMBOL": symbol,
                "OPEN_DATE": open_date_str,
                "OPEN_PRICE": round(open_price, 5),
                "OPEN_15M_SAR": open_data.get('OPEN_15M_SAR', '—'),
                "OPEN_15M_MACD": open_data.get('OPEN_15M_MACD', '—'),
                "OPEN_15M_KDJ": open_data.get('OPEN_15M_KDJ', '—'),
                "OPEN_1H_SAR": open_data.get('OPEN_1H_SAR', '—'),
                "OPEN_1H_MACD": open_data.get('OPEN_1H_MACD', '—'),
                "OPEN_1H_KDJ": open_data.get('OPEN_1H_KDJ', '—'),
                "OPEN_4H_SAR": open_data.get('OPEN_4H_SAR', '—'),
                "OPEN_4H_MACD": open_data.get('OPEN_4H_MACD', '—'),
                "OPEN_4H_KDJ": open_data.get('OPEN_4H_KDJ', '—'),
                "CLOSE_DATE": close_date_str,
                "CLOSE_PRICE": round(close_price, 5),
                "CLOSE_15M_SAR": current_indicators[3] if len(current_indicators) > 3 else '—',
                "CLOSE_15M_MACD": current_indicators[4] if len(current_indicators) > 4 else '—',
                "CLOSE_15M_KDJ": current_indicators[5] if len(current_indicators) > 5 else '—',
                "CLOSE_1H_SAR": current_indicators[6] if len(current_indicators) > 6 else '—',
                "CLOSE_1H_MACD": current_indicators[7] if len(current_indicators) > 7 else '—',
                "CLOSE_1H_KDJ": current_indicators[8] if len(current_indicators) > 8 else '—',
                "CLOSE_4H_SAR": current_indicators[9] if len(current_indicators) > 9 else '—',
                "CLOSE_4H_MACD": current_indicators[10] if len(current_indicators) > 10 else '—',
                "CLOSE_4H_KDJ": current_indicators[11] if len(current_indicators) > 11 else '—',
                "PROFIT_OR_LOSS": round(profit_or_loss, 5),
                "PROFIT_OR_LOSS_PERCENT": round(profit_or_loss_percent, 5),
                "HOLD_TIME": hold_time
            }
            
            doc_id = datetime.now(self.japan_tz).isoformat()
            doc_ref = self.db.collection(self.collection_history).document(doc_id)
            doc_ref.set(history_data)
            
            print(f"✅ 保存平仓记录: {symbol}, 价格: {open_price} → {close_price}, 盈亏: {round(profit_or_loss, 5)} ({round(profit_or_loss_percent*100, 2)}%), 持仓: {hold_time}")
                
        except Exception as e:
            print(f"❌ 保存历史记录失败 {symbol}: {e}")
            import traceback
            traceback.print_exc()
    
    # 开仓检查
    def open_trade(self, parsed_data: Dict[str, List[str]]):
        """检查并执行开仓"""
        try:
            # 检查当前是否有持仓
            current_trades = list(self.db.collection(self.collection_current).get())
            
            if current_trades:
                print(f"⚠️ 当前有持仓，跳过开仓")
                return
            
            # 收集所有buy信号
            buy_symbols = []
            for symbol, indicators in parsed_data.items():
                if len(indicators) >= 3 and indicators[2] == 'buy':
                    buy_symbols.append((symbol, indicators))
            
            if not buy_symbols:
                print(f"⚠️ 没有找到buy信号")
                return
            
            print(f"📊 找到 {len(buy_symbols)} 个buy信号: {[s[0] for s in buy_symbols]}")
            
            # 遍历所有buy信号
            for buy_symbol, buy_indicators in buy_symbols:
                print(f"\n🔍 检查 {buy_symbol} 是否符合开仓条件...")
                
                in_cooldown, remaining, last_symbol = self.check_cooldown_from_history(buy_symbol)
                
                if in_cooldown:
                    print(f"   ❌ 冷却期未过: {buy_symbol}")
                    print(f"      上次平仓币种: {last_symbol}, 还需等待 {remaining//60} 分钟")
                    continue
                
                # 执行开仓
                price = float(buy_indicators[1]) if buy_indicators[1] != '—' else 0
                
                doc_data = {
                    "SYMBOL": buy_symbol,
                    "OPEN_DATE": datetime.now(self.japan_tz).isoformat(),
                    "OPEN_PRICE": price,
                    "OPEN_15M_SAR": buy_indicators[3] if len(buy_indicators) > 3 else '—',
                    "OPEN_15M_MACD": buy_indicators[4] if len(buy_indicators) > 4 else '—',
                    "OPEN_15M_KDJ": buy_indicators[5] if len(buy_indicators) > 5 else '—',
                    "OPEN_1H_SAR": buy_indicators[6] if len(buy_indicators) > 6 else '—',
                    "OPEN_1H_MACD": buy_indicators[7] if len(buy_indicators) > 7 else '—',
                    "OPEN_1H_KDJ": buy_indicators[8] if len(buy_indicators) > 8 else '—',
                    "OPEN_4H_SAR": buy_indicators[9] if len(buy_indicators) > 9 else '—',
                    "OPEN_4H_MACD": buy_indicators[10] if len(buy_indicators) > 10 else '—',
                    "OPEN_4H_KDJ": buy_indicators[11] if len(buy_indicators) > 11 else '—',
                }
                
                doc_ref = self.db.collection(self.collection_current).document(buy_symbol)
                doc_ref.set(doc_data)
                print(f"\n✅ 开仓成功: {buy_symbol}, 价格: {price}")
                return
            
            print(f"\n⚠️ 所有buy信号都不符合开仓条件")
            
        except Exception as e:
            print(f"❌ 开仓失败: {e}")
            import traceback
            traceback.print_exc()
    
    def run(self):
        """主执行函数"""
        print(f"🚀 交易信号Job开始执行: {datetime.now(self.japan_tz)}")
        print("=" * 50)
        
        # 1. 获取信号数据
        raw_data = self.fetch_signals_data()
        if not raw_data:
            print("❌ 无法获取信号数据，Job终止")
            return
        
        if 'errors' in raw_data and raw_data['errors']:
            print(f"⚠️ API返回错误: {raw_data['errors']}")
        
        # 2. 解析数据（使用统一逻辑）
        parsed_data = self.parse_signals_data(raw_data)
        print(f"\n📈 解析后的数据:")
        for symbol, indicators in parsed_data.items():
            print(f"  {symbol}: 信号={indicators[2]}, 价格={indicators[1]}")
        
        # 3. 平仓检查（完全由指标决定）
        self.close_trade(parsed_data)
        
        # 4. 开仓检查
        self.open_trade(parsed_data)
        
        print("\n" + "=" * 50)
        print(f"✅ Job执行完成: {datetime.now(self.japan_tz)}")


def main():
    job = TradingSignalJob()
    job.run()


if __name__ == "__main__":
    main()