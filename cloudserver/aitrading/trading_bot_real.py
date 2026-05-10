# trading_bot_real.py
#!/usr/bin/env python
import json
import os
import sys
import time
from datetime import datetime
from decimal import Decimal, ROUND_DOWN
from typing import Dict, List, Optional

import pytz
import requests
from binance.client import Client
from binance.exceptions import BinanceAPIException
from binance.enums import *

# 将项目根目录添加到 Python 路径
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

import django
django.setup()

from google.cloud import firestore
from django.conf import settings


class RealTradingBot:
    def __init__(self):
        self.api_url = "https://bnbs-django-275599637949.asia-northeast1.run.app/signals"
        self.db = firestore.Client(database='aitrading')
        self.collection_current = "REAL_CURRENT_TRADE"
        self.collection_history = "REAL_TRADE_HISTORY"
        self.collection_orders = "REAL_ORDER_HISTORY"
        self.japan_tz = pytz.timezone('Asia/Tokyo')
        
        # 初始化币安客户端
        api_key = settings.BINANCE_API_KEY
        api_secret = settings.BINANCE_API_SECRET
        testnet = settings.BINANCE_TESTNET
        
        # 检查 API 密钥是否存在
        if not api_key or not api_secret:
            raise ValueError(
                "Binance API credentials not found!\n"
                "Please ensure BINANCE_API_KEY and BINANCE_API_SECRET are set in Secret Manager."
            )
    
        try:
            if testnet:
                self.client = Client(api_key, api_secret, testnet=True)
                print("🔧 使用币安测试网 (Testnet)")
                print("   ⚠️  这是测试环境，不会使用真实资金")
            else:
                self.client = Client(api_key, api_secret)
                print("💰 使用币安主网 (Mainnet)")
                print("   ⚠️  这是真实交易环境，将会使用真实资金！")
            
            # 测试连接
            self.client.get_server_time()
            print("✅ Binance API 连接成功")
            
        except BinanceAPIException as e:
            print(f"❌ Binance API 连接失败: {e}")
            raise
        
        # 交易配置 - 全额交易模式
        self.trade_config = {
            'QUOTE_ASSET': 'USDT',           # 报价货币
            'ORDER_TYPE': ORDER_TYPE_MARKET, # 市价单
            'USE_FULL_BALANCE': True,        # 使用全部余额
            'MIN_NOTIONAL': 10,              # 最小下单金额 USDT
            'SLIPPAGE': 0.002,               # 滑点容忍度 0.2%
            'RESERVE_USDT': 0,               # 预留USDT金额（设为0表示全部使用）
        }
    
    def get_balance(self, asset: str = 'USDT') -> float:
        """获取指定资产的余额"""
        try:
            balance = self.client.get_asset_balance(asset=asset)
            return float(balance['free'])
        except BinanceAPIException as e:
            print(f"❌ 获取余额失败 {asset}: {e}")
            return 0.0
    
    def get_current_price(self, symbol: str) -> Optional[float]:
        """获取当前市场价格"""
        try:
            ticker = self.client.get_symbol_ticker(symbol=symbol)
            return float(ticker['price'])
        except BinanceAPIException as e:
            print(f"❌ 获取价格失败 {symbol}: {e}")
            return None
    
    def calculate_full_quantity(self, symbol: str, price: float) -> float:
        """
        使用全部USDT余额计算下单数量
        """
        # 获取USDT余额
        usdt_balance = self.get_balance('USDT')
        
        if usdt_balance <= 0:
            print(f"⚠️ USDT余额不足: {usdt_balance}")
            return 0.0
        
        # 扣除预留金额
        reserve = self.trade_config.get('RESERVE_USDT', 0)
        trade_amount = usdt_balance - reserve
        
        if trade_amount <= 0:
            print(f"⚠️ 扣除预留后无可用余额: 余额={usdt_balance}, 预留={reserve}")
            return 0.0
        
        # 检查最小交易金额
        min_notional = self.trade_config.get('MIN_NOTIONAL', 10)
        if trade_amount < min_notional:
            print(f"⚠️ 交易金额 {trade_amount:.2f} USDT 小于最小限制 {min_notional} USDT")
            return 0.0
        
        # 计算原始数量
        raw_quantity = trade_amount / price
        
        # 获取该交易对的数量精度
        try:
            info = self.client.get_symbol_info(symbol)
            step_size = None
            for filter_data in info['filters']:
                if filter_data['filterType'] == 'LOT_SIZE':
                    step_size = float(filter_data['stepSize'])
                    break
            
            if step_size:
                # 获取小数点位数
                precision = len(str(step_size).rstrip('0').split('.')[-1])
                # 向下取整到正确精度
                quantity = float(Decimal(str(raw_quantity)).quantize(
                    Decimal('1e-{}'.format(precision)), rounding=ROUND_DOWN
                ))
            else:
                quantity = round(raw_quantity, 6)
        except Exception as e:
            print(f"⚠️ 获取精度失败，使用默认: {e}")
            quantity = round(raw_quantity, 6)
        
        print(f"💰 使用全部余额交易: {trade_amount:.2f} USDT (总余额: {usdt_balance:.2f} USDT)")
        print(f"📊 计算数量: {quantity} {symbol.replace('USDT', '')}")
        
        return quantity
    
    def place_buy_order(self, symbol: str, quantity: float) -> Optional[Dict]:
        """执行市价买入订单（全额买入）"""
        try:
            print(f"📈 执行全额买入: {symbol}, 数量: {quantity}")
            
            order = self.client.order_market_buy(
                symbol=symbol,
                quantity=quantity
            )
            
            print(f"✅ 买入成功: {symbol}, 订单ID: {order['orderId']}")
            print(f"   成交数量: {order['executedQty']}, 成交金额: {order['cummulativeQuoteQty']}")
            
            return order
            
        except BinanceAPIException as e:
            print(f"❌ 买入失败 {symbol}: {e}")
            return None
    
    def place_sell_order(self, symbol: str, quantity: float) -> Optional[Dict]:
        """执行市价卖出订单（卖出全部持仓）"""
        try:
            print(f"📉 执行全额卖出: {symbol}, 数量: {quantity}")
            
            order = self.client.order_market_sell(
                symbol=symbol,
                quantity=quantity
            )
            
            print(f"✅ 卖出成功: {symbol}, 订单ID: {order['orderId']}")
            print(f"   成交数量: {order['executedQty']}, 成交金额: {order['cummulativeQuoteQty']}")
            
            return order
            
        except BinanceAPIException as e:
            print(f"❌ 卖出失败 {symbol}: {e}")
            return None
    
    def get_position(self, symbol: str) -> float:
        """获取指定交易对的持仓数量"""
        try:
            base_asset = symbol.replace('USDT', '')
            balance = self.client.get_asset_balance(asset=base_asset)
            return float(balance['free'])
        except Exception as e:
            print(f"❌ 获取持仓失败 {symbol}: {e}")
            return 0.0

    def get_precision_info(self, symbol: str) -> tuple:
        """获取交易对的数量和价格精度信息"""
        try:
            info = self.client.get_symbol_info(symbol)
            step_size = None
            tick_size = None
            
            for filter_data in info['filters']:
                if filter_data['filterType'] == 'LOT_SIZE':
                    step_size = float(filter_data['stepSize'])
                if filter_data['filterType'] == 'PRICE_FILTER':
                    tick_size = float(filter_data['tickSize'])
            
            # 计算精度位数
            quantity_precision = 6
            price_precision = 2
            
            if step_size:
                step_size_str = str(step_size).rstrip('0')
                if '.' in step_size_str:
                    quantity_precision = len(step_size_str.split('.')[-1])
                else:
                    quantity_precision = 0
            
            if tick_size:
                tick_size_str = str(tick_size).rstrip('0')
                if '.' in tick_size_str:
                    price_precision = len(tick_size_str.split('.')[-1])
                else:
                    price_precision = 0
            
            return quantity_precision, price_precision
        except Exception as e:
            print(f"⚠️ 获取精度信息失败: {e}")
            return 6, 2
        
    def fetch_signals(self) -> Optional[Dict]:
        """发送POST请求获取交易信号"""
        try:
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
            
            response = requests.post(
                self.api_url,
                json=self.payload,
                headers=headers,
                timeout=30
            )
            response.raise_for_status()
            return response.json()
            
        except requests.exceptions.RequestException as e:
            print(f"❌ API请求失败: {e}")
            return None
    
    def parse_signals(self, raw_data: Dict) -> Dict[str, List[str]]:
        """解析信号数据"""
        formatted = {}
        results = raw_data.get('results', {})
        
        currency_order = []
        if hasattr(self, 'payload') and 'targets' in self.payload:
            for target in self.payload['targets']:
                symbol = target['symbol'].split('/')[0]
                if symbol not in currency_order:
                    currency_order.append(symbol)
        
        for period_symbol, data in results.items():
            parts = period_symbol.split('_', 1)
            if len(parts) != 2:
                continue
            
            timeframe = parts[0]
            symbol_with_slash = parts[1]
            base_currency = symbol_with_slash.split('/')[0]
            
            if len(data) >= 7:
                price = data[2]
                indicators = data[3:6]
                
                if base_currency not in formatted:
                    formatted[base_currency] = [base_currency, price, '—', '—', '—', '—', '—', '—', '—', '—', '—']
                
                if timeframe == '15m':
                    formatted[base_currency][3] = indicators[2]
                    formatted[base_currency][4] = indicators[1]
                elif timeframe == '1h':
                    formatted[base_currency][5] = indicators[0]
                    formatted[base_currency][6] = indicators[1]
                    formatted[base_currency][7] = indicators[2]
                elif timeframe == '4h':
                    formatted[base_currency][8] = indicators[0]
                    formatted[base_currency][9] = indicators[1]
                    formatted[base_currency][10] = indicators[2]
        
        for currency, indicators in formatted.items():
            if self.check_signal(indicators):
                indicators[2] = 'buy'
            else:
                indicators[2] = 'sell'
        
        ordered_formatted = {}
        for currency in currency_order:
            if currency in formatted:
                ordered_formatted[currency] = formatted[currency]
        
        return ordered_formatted
    
    def check_signal(self, indicators: List[str]) -> bool:
        """检查买入信号"""
        if len(indicators) < 11:
            return False
        
        one_hour_indicators = indicators[5:8]
        four_hour_indicators = indicators[8:11]
        
        if all(ind == '〇' for ind in one_hour_indicators + four_hour_indicators):
            return True
        
        condition_a = indicators[6] == '〇' and indicators[7] == '〇'
        condition_b = sum(1 for ind in four_hour_indicators if ind == '〇') >= 2
        condition_c = indicators[4] == '〇'
        
        return condition_a and condition_b and condition_c
    
    def close_trade(self, parsed_data: Dict[str, List[str]]):
        """真实平仓：卖出全部持仓 - 不做精度检查，直接使用交易所实际数量"""
        try:
            for symbol, indicators in parsed_data.items():
                if len(indicators) >= 3 and indicators[2] == 'sell':
                    doc_ref = self.db.collection(self.collection_current).document(symbol)
                    doc = doc_ref.get()
                    
                    if doc.exists:
                        open_data = doc.to_dict()
                        open_date_str = open_data.get('OPEN_DATE')
                        
                        if open_date_str:
                            open_date = datetime.fromisoformat(open_date_str)
                            current_time = datetime.now(self.japan_tz)
                            time_diff_seconds = (current_time - open_date).total_seconds()
                            required_seconds = 9 * 60
                            
                            if time_diff_seconds >= required_seconds:
                                symbol_binance = f"{symbol}USDT"
                                
                                # 🔥 修改：直接从交易所获取实际持仓数量（可用余额）
                                actual_position = self.get_position(symbol_binance)
                                
                                print(f"🔍 卖出前检查 - {symbol}")
                                print(f"   交易所实际持仓: {actual_position}")
                                print(f"   数据库记录数量: {open_data.get('OPEN_QUANTITY', 0)}")
                                
                                # 🔥 关键修改：不做任何精度处理，直接使用交易所返回的数量
                                position_amount = actual_position
                                
                                if position_amount > 0:
                                    # 执行卖出（直接使用原始数量）
                                    print(f"📉 执行卖出: {symbol}, 数量={position_amount} (无精度处理)")
                                    
                                    try:
                                        order = self.client.order_market_sell(
                                            symbol=symbol_binance,
                                            quantity=position_amount
                                        )
                                        
                                        if order:
                                            close_price = float(order['cummulativeQuoteQty']) / float(order['executedQty'])
                                            
                                            self.save_real_order(symbol, order, 'SELL', open_data, close_price)
                                            self.save_to_history(symbol, indicators, open_data, close_price)
                                            doc_ref.delete()
                                            
                                            # 显示平仓后的余额
                                            new_balance = self.get_balance('USDT')
                                            print(f"✅ 真实平仓完成: {symbol}")
                                            print(f"   卖出数量: {position_amount}")
                                            print(f"   卖出均价: {close_price:.4f}")
                                            print(f"   收到金额: {float(order['cummulativeQuoteQty']):.2f} USDT")
                                            print(f"💰 平仓后USDT余额: {new_balance:.2f}")
                                        else:
                                            print(f"❌ 真实平仓失败: {symbol}")
                                            
                                    except BinanceAPIException as e:
                                        print(f"❌ 卖出失败 {symbol}: {e}")
                                        # 如果卖出失败，尝试使用数据库记录的数量
                                        print(f"🔄 降级尝试: 使用数据库记录数量 {open_data.get('OPEN_QUANTITY', 0)}")
                                        try:
                                            fallback_amount = float(open_data.get('OPEN_QUANTITY', 0))
                                            if fallback_amount > 0:
                                                order = self.client.order_market_sell(
                                                    symbol=symbol_binance,
                                                    quantity=fallback_amount
                                                )
                                                if order:
                                                    close_price = float(order['cummulativeQuoteQty']) / float(order['executedQty'])
                                                    self.save_real_order(symbol, order, 'SELL', open_data, close_price)
                                                    self.save_to_history(symbol, indicators, open_data, close_price)
                                                    doc_ref.delete()
                                                    print(f"✅ 降级方案平仓成功: {symbol}")
                                        except Exception as fallback_error:
                                            print(f"❌ 降级方案也失败: {fallback_error}")
                                else:
                                    print(f"⚠️ 交易所无持仓，跳过平仓: {symbol}")
                            else:
                                remaining_minutes = int((required_seconds - time_diff_seconds) // 60) + 1
                                print(f"⚠️ 持仓不足9分钟，跳过平仓: {symbol} (还需等待 {remaining_minutes} 分钟)")
                                    
        except Exception as e:
            print(f"❌ 平仓失败: {e}")
            import traceback
            traceback.print_exc()
    
    def open_trade(self, parsed_data: Dict[str, List[str]]):
        """真实开仓：使用全部余额买入"""
        try:
            # 检查是否已有持仓
            current_trades = self.db.collection(self.collection_current).get()
            if len(list(current_trades)) > 0:
                print(f"⚠️ 已有持仓，等待平仓后再开新仓")
                return
            
            # 检查USDT余额
            usdt_balance = self.get_balance('USDT')
            print(f"💰 当前USDT余额: {usdt_balance:.2f}")
            
            if usdt_balance <= 0:
                print("⚠️ USDT余额为0，无法开仓")
                return
            
            # 收集buy信号
            buy_symbols = []
            for symbol, indicators in parsed_data.items():
                if len(indicators) >= 3 and indicators[2] == 'buy':
                    buy_symbols.append((symbol, indicators))
            
            if not buy_symbols:
                print(f"⚠️ 没有buy信号")
                return
            
            # 检查冷却时间
            history_ref = self.db.collection(self.collection_history)
            latest_trade = history_ref.order_by("CLOSE_DATE", direction=firestore.Query.DESCENDING).limit(1).get()
            latest_trade_list = list(latest_trade)
            
            for buy_symbol, buy_indicators in buy_symbols:
                can_open = True
                
                if latest_trade_list:
                    latest_doc = latest_trade_list[0]
                    latest_close_date_str = latest_doc.get('CLOSE_DATE')
                    last_symbol = latest_doc.get('SYMBOL')
                    
                    if latest_close_date_str:
                        latest_close_date = datetime.fromisoformat(latest_close_date_str)
                        current_time = datetime.now(self.japan_tz)
                        time_diff_seconds = (current_time - latest_close_date).total_seconds()
                        
                        if last_symbol == buy_symbol:
                            required_seconds = 60 * 60
                            cooling_type = "相同币种60分钟"
                        else:
                            required_seconds = 30 * 60
                            cooling_type = "不同币种30分钟"
                        
                        if time_diff_seconds < required_seconds:
                            remaining_minutes = int((required_seconds - time_diff_seconds) // 60) + 1
                            print(f"⚠️ {buy_symbol} 冷却期未过 ({cooling_type}), 还需等待 {remaining_minutes} 分钟")
                            can_open = False
                        else:
                            print(f"✅ {buy_symbol} 冷却期检查通过 ({cooling_type})")
                
                if can_open:
                    symbol_binance = f"{buy_symbol}USDT"
                    current_price = self.get_current_price(symbol_binance)
                    
                    if not current_price:
                        print(f"❌ 无法获取 {symbol_binance} 价格")
                        continue
                    
                    # 价格偏差检查
                    signal_price = float(buy_indicators[1])
                    slippage = self.trade_config.get('SLIPPAGE', 0.002)
                    price_diff_pct = abs(current_price - signal_price) / signal_price
                    
                    if price_diff_pct > slippage:
                        print(f"⚠️ {buy_symbol} 价格偏差过大: {price_diff_pct*100:.2f}% > {slippage*100:.1f}%")
                        print(f"   信号价格: {signal_price}, 市价: {current_price}")
                        continue
                    
                    # 计算全额数量
                    quantity = self.calculate_full_quantity(symbol_binance, current_price)
                    
                    if quantity <= 0:
                        print(f"⚠️ {buy_symbol} 计算数量为0，跳过")
                        continue
                    
                    print(f"\n🎯 准备全额开仓: {buy_symbol}")
                    print(f"   信号价格: {signal_price}")
                    print(f"   实际价格: {current_price}")
                    print(f"   下单数量: {quantity}")
                    print(f"   预计金额: {quantity * current_price:.2f} USDT")
                    
                    # 执行全额买入
                    order = self.place_buy_order(symbol_binance, quantity)
                    
                    if order:
                        executed_qty = float(order['executedQty'])
                        executed_amount = float(order['cummulativeQuoteQty'])
                        actual_price = executed_amount / executed_qty if executed_qty > 0 else current_price
                        
                        print(f"\n✅ 全额开仓成功!")
                        print(f"   成交数量: {executed_qty}")
                        print(f"   成交金额: {executed_amount:.2f} USDT")
                        print(f"   成交均价: {actual_price:.4f}")
                        
                        # 显示开仓后剩余余额
                        remaining_balance = self.get_balance('USDT')
                        print(f"   剩余USDT: {remaining_balance:.2f}")
                        
                        # 记录开仓数据
                        doc_data = {
                            "SYMBOL": buy_symbol,
                            "OPEN_DATE": datetime.now(self.japan_tz).isoformat(),
                            "OPEN_PRICE": actual_price,
                            "OPEN_QUANTITY": executed_qty,
                            "OPEN_AMOUNT": executed_amount,
                            "BEFORE_BALANCE": usdt_balance,
                            "AFTER_BALANCE": remaining_balance,
                            "OPEN_15M_KDJ": buy_indicators[3] if len(buy_indicators) > 3 else '—',
                            "OPEN_15M_MACD": buy_indicators[4] if len(buy_indicators) > 4 else '—',
                            "OPEN_1H_SAR": buy_indicators[5] if len(buy_indicators) > 5 else '—',
                            "OPEN_1H_MACD": buy_indicators[6] if len(buy_indicators) > 6 else '—',
                            "OPEN_1H_KDJ": buy_indicators[7] if len(buy_indicators) > 7 else '—',
                            "OPEN_4H_SAR": buy_indicators[8] if len(buy_indicators) > 8 else '—',
                            "OPEN_4H_MACD": buy_indicators[9] if len(buy_indicators) > 9 else '—',
                            "OPEN_4H_KDJ": buy_indicators[10] if len(buy_indicators) > 10 else '—',
                            "ORDER_ID": order['orderId'],
                        }
                        
                        doc_ref = self.db.collection(self.collection_current).document(buy_symbol)
                        doc_ref.set(doc_data)
                        
                        self.save_real_order(buy_symbol, order, 'BUY', doc_data)
                        return  # 成功开仓后退出
                    else:
                        print(f"❌ 全额开仓失败: {buy_symbol}")
            
            print(f"⚠️ 没有符合条件的开仓信号")
            
        except Exception as e:
            print(f"❌ 开仓失败: {e}")
            import traceback
            traceback.print_exc()
    
    def save_real_order(self, symbol: str, order: Dict, order_type: str, trade_data: Dict, close_price: float = None):
        """保存真实订单到数据库"""
        try:
            # 获取交易后的实时余额
            current_usdt_balance = self.get_balance('USDT')
            
            # 根据订单类型计算正确的余额
            if order_type == 'BUY':
                # 买入：trade_data 包含开仓数据，有 BEFORE_BALANCE 和 AFTER_BALANCE
                before_balance = trade_data.get('BEFORE_BALANCE')
                after_balance = trade_data.get('AFTER_BALANCE')
            else:  # SELL
                # 卖出：从开仓数据获取卖出前余额（即开仓后的余额）
                before_balance = trade_data.get('AFTER_BALANCE') if trade_data else None
                
                # 如果无法从开仓数据获取，则计算（卖出后余额 - 卖出收到金额）
                if before_balance is None:
                    received_amount = float(order['cummulativeQuoteQty'])
                    before_balance = current_usdt_balance - received_amount
                
                after_balance = current_usdt_balance
            
            order_data = {
                "SYMBOL": symbol,
                "ORDER_TYPE": order_type,
                "ORDER_ID": order['orderId'],
                "QUANTITY": float(order['executedQty']),
                "PRICE": close_price if close_price else float(order['cummulativeQuoteQty']) / float(order['executedQty']),
                "TOTAL_AMOUNT": float(order['cummulativeQuoteQty']),
                "STATUS": order['status'],
                "TIMESTAMP": datetime.now(self.japan_tz).isoformat(),
                
                # 添加正确的余额信息
                "BEFORE_BALANCE": before_balance,
                "AFTER_BALANCE": after_balance,
                "BALANCE_CHANGE": after_balance - before_balance if before_balance else 0,
                
                # 保留原始交易数据用于调试
                "TRADE_DATA": trade_data
            }
            
            doc_id = f"{symbol}_{order_type}_{int(datetime.now(self.japan_tz).timestamp())}"
            doc_ref = self.db.collection(self.collection_orders).document(doc_id)
            doc_ref.set(order_data)
            
            print(f"✅ 订单记录保存成功: {symbol} {order_type}")
            print(f"   💰 余额变化: {before_balance:.2f} → {after_balance:.2f} (变化: {after_balance - before_balance:.2f})")
            
        except Exception as e:
            print(f"❌ 保存订单记录失败: {e}")
            import traceback
            traceback.print_exc()
    
    def save_to_history(self, symbol: str, current_indicators: List[str], open_data: dict, close_price: float):
        """保存平仓记录到历史表"""
        try:
            open_price = float(open_data.get('OPEN_PRICE', 0))
            open_quantity = float(open_data.get('OPEN_QUANTITY', 0))
            
            # 计算盈亏
            profit_or_loss = (close_price - open_price) * open_quantity
            profit_or_loss_percent = (close_price - open_price) / open_price if open_price != 0 else 0
            
            open_date_str = open_data.get('OPEN_DATE', datetime.now(self.japan_tz).isoformat())
            close_date_str = datetime.now(self.japan_tz).isoformat()
            
            from datetime import datetime as dt
            open_date = dt.fromisoformat(open_date_str)
            close_date = dt.fromisoformat(close_date_str)
            
            time_diff = close_date - open_date
            total_hours = int(time_diff.total_seconds() // 3600)
            total_minutes = int((time_diff.total_seconds() % 3600) // 60)
            hold_time = f"{total_hours}h{total_minutes}m"
            
            history_data = {
                "SYMBOL": symbol,
                "OPEN_DATE": open_date_str,
                "OPEN_PRICE": round(open_price, 5),
                "OPEN_QUANTITY": open_quantity,
                "OPEN_AMOUNT": open_data.get('OPEN_AMOUNT', 0),
                "OPEN_15M_KDJ": open_data.get('OPEN_15M_KDJ', '—'),
                "OPEN_15M_MACD": open_data.get('OPEN_15M_MACD', '—'),
                "OPEN_1H_SAR": open_data.get('OPEN_1H_SAR', '—'),
                "OPEN_1H_MACD": open_data.get('OPEN_1H_MACD', '—'),
                "OPEN_1H_KDJ": open_data.get('OPEN_1H_KDJ', '—'),
                "OPEN_4H_SAR": open_data.get('OPEN_4H_SAR', '—'),
                "OPEN_4H_MACD": open_data.get('OPEN_4H_MACD', '—'),
                "OPEN_4H_KDJ": open_data.get('OPEN_4H_KDJ', '—'),
                "CLOSE_DATE": close_date_str,
                "CLOSE_PRICE": round(close_price, 5),
                "CLOSE_15M_KDJ": current_indicators[3] if len(current_indicators) > 3 else '—',
                "CLOSE_15M_MACD": current_indicators[4] if len(current_indicators) > 4 else '—',
                "CLOSE_1H_SAR": current_indicators[5] if len(current_indicators) > 5 else '—',
                "CLOSE_1H_MACD": current_indicators[6] if len(current_indicators) > 6 else '—',
                "CLOSE_1H_KDJ": current_indicators[7] if len(current_indicators) > 7 else '—',
                "CLOSE_4H_SAR": current_indicators[8] if len(current_indicators) > 8 else '—',
                "CLOSE_4H_MACD": current_indicators[9] if len(current_indicators) > 9 else '—',
                "CLOSE_4H_KDJ": current_indicators[10] if len(current_indicators) > 10 else '—',
                "PROFIT_OR_LOSS": round(profit_or_loss, 5),
                "PROFIT_OR_LOSS_PERCENT": round(profit_or_loss_percent, 5),
                "HOLD_TIME": hold_time,
                "ORDER_TYPE": "REAL"
            }
            
            doc_id = datetime.now(self.japan_tz).isoformat()
            doc_ref = self.db.collection(self.collection_history).document(doc_id)
            doc_ref.set(history_data)
            
            print(f"✅ 保存真实交易历史: {symbol}")
            print(f"   盈亏: {round(profit_or_loss, 2)} USDT ({round(profit_or_loss_percent*100, 2)}%)")
                
        except Exception as e:
            print(f"❌ 保存历史记录失败: {e}")
    
    def print_account_summary(self):
        """打印账户摘要"""
        usdt_balance = self.get_balance('USDT')
        
        # 获取当前持仓
        current_trades = self.db.collection(self.collection_current).get()
        
        print("\n" + "=" * 50)
        print("📊 账户摘要")
        print("=" * 50)
        print(f"💰 USDT余额: {usdt_balance:.2f}")
        
        total_position_value = 0
        for trade in current_trades:
            data = trade.to_dict()
            symbol = data['SYMBOL']
            symbol_binance = f"{symbol}USDT"
            current_price = self.get_current_price(symbol_binance)
            if current_price:
                position_value = data.get('OPEN_QUANTITY', 0) * current_price
                total_position_value += position_value
                print(f"   📈 {symbol}: {position_value:.2f} USDT")
        
        print(f"💼 持仓总值: {total_position_value:.2f}")
        print(f"📊 总资产: {usdt_balance + total_position_value:.2f}")
        print("=" * 50)
    
    def run(self):
        """主执行函数"""
        print(f"🚀 真实交易Bot启动 (全额交易模式): {datetime.now(self.japan_tz)}")
        print("=" * 50)
        
        # 显示账户摘要
        self.print_account_summary()
        
        # 获取信号
        raw_data = self.fetch_signals()
        if not raw_data:
            print("❌ 无法获取信号数据")
            return
        
        # 解析数据
        parsed_data = self.parse_signals(raw_data)
        print(f"\n📈 解析后的信号:")
        for symbol, indicators in parsed_data.items():
            print(f"  {symbol}: 信号={indicators[2]}, 价格={indicators[1]}")
        
        # 平仓
        self.close_trade(parsed_data)
        
        # 开仓
        self.open_trade(parsed_data)
        
        # 显示最终账户摘要
        self.print_account_summary()
        
        print("\n" + "=" * 50)
        print(f"✅ Bot执行完成: {datetime.now(self.japan_tz)}")


def main():
    bot = RealTradingBot()
    bot.run()


if __name__ == "__main__":
    main()