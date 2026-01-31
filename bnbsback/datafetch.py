import ccxt
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import time
import pandas_ta as ta

KLINE_LIMIT = 100

def get_binance_hourly_btc_data():
    """
    从Binance获取BTC/USDT小时线数据
    """
    exchange = ccxt.binance({
        'rateLimit': 1200,
        'enableRateLimit': True,
        'options': {
            'defaultType': 'spot',
        }
    })
    
    # 获取BTC/USDT小时K线数据
    ohlcv = exchange.fetch_ohlcv(
        symbol = 'BTC/USDT',
        timeframe = '1h',
        limit = KLINE_LIMIT  # 获取的数据条数
    )
    
    # 转换为DataFrame
    df = pd.DataFrame(
        ohlcv,
        columns=['timestamp', 'open', 'high', 'low', 'close', 'volume']
    )
    
    # 转换时间戳为可读格式
    df['datetime'] = pd.to_datetime(df['timestamp'], unit='ms')
    df.set_index('datetime', inplace=True)
    
    return df

def calculate_macd(df, fast=12, slow=26, signal=9):
    """
    计算MACD指标
    """
    # 使用pandas-ta计算MACD
    macd = ta.macd(
        df['close'], 
        fast=fast, 
        slow=slow, 
        signal=signal
    )
    
    # 合并到DataFrame
    df['MACD'] = macd[f'MACD_{fast}_{slow}_{signal}']
    df['MACD_signal'] = macd[f'MACDs_{fast}_{slow}_{signal}']
    df['MACD_hist'] = macd[f'MACDh_{fast}_{slow}_{signal}']
    
    return df

def calculate_kdj(df):
    """
    计算KDJ指标
    KDJ = 随机指标
    """
    kdj = ta.kdj(
        high = df['high'],
        low = df['low'],
        close = df['close'],
        length = 9,           # KDJの期間（デフォルトは14）
        signal = 3,           # Dラインの期間（デフォルトは3）
        scalar = 100,
        offset = 0,
        append = True
    )
    df['K'] = kdj['K_9_3']
    df['D'] = kdj['D_9_3']
    df['J'] = kdj['J_9_3']
    return df

def calculate_sar(df, acceleration = 0.02, maximum = 0.2):
    """
    计算抛物线转向指标SAR
    """
    # 使用pandas-ta计算SAR
    sar = ta.psar(
        high=df['high'],
        low=df['low'],
        acceleration=acceleration,
        maximum=maximum
    )
    
    df['SAR_long'] = sar.iloc[:, 0]# 多头SAR（通常当价格上涨时显示）
    df['SAR_short'] = sar.iloc[:, 1]# 空头SAR（通常当价格下跌时显示）
    
    return df

def get_all_indicators():
    """
    获取数据并计算所有指标
    """
    print("正在从Binance获取BTC/USDT小时线数据...")
    
    # 1. 获取数据（最近1000小时）
    df = get_binance_hourly_btc_data()
    
    print(f"获取到 {len(df)} 条小时数据")
    print(f"时间范围: {df.index[0]} 到 {df.index[-1]}")
    
    # 2. 计算MACD
    print("计算MACD指标...")
    df = calculate_macd(df)
    
    # 3. 计算KDJ
    print("计算KDJ指标...")
    df = calculate_kdj(df)
    
    # 4. 计算SAR
    print("计算SAR指标...")
    df = calculate_sar(df)
    
    # 5. 清理数据（删除NaN值）
    # df_clean = df.dropna()
    
    return df

def display_results(df):
    """
    显示结果
    """
    pd.set_option('display.max_rows', 10)
    pd.set_option('display.width', 1000)
    
    print("df" + str(len(df)))
    print("\n" + "="*80)
    print("比特币小时线技术指标分析")
    print("="*80)
    
    # 显示最近10小时的完整数据
    print("\n最近10小时数据（完整指标）：")
    display_columns = ['open', 'high', 'low', 'close', 'volume', 
                      'MACD', 'MACD_signal', 'MACD_hist',
                      'K', 'D', 'J', 'SAR_long', 'SAR_short']
    
    print(df[display_columns].tail(10).round(2))
    
    # 显示最新的数据
    print(f"\n最新数据（时间: {df.index[-1]}）：")
    latest = df.iloc[-1]
    print(f"价格: {latest['close']:.2f} USDT")
    print(f"MACD: {latest['MACD']:.4f}, 信号线: {latest['MACD_signal']:.4f}, 柱状图: {latest['MACD_hist']:.4f}")
    print(f"KDJ - K: {latest['K']:.2f}, D: {latest['D']:.2f}, J: {latest['J']:.2f}")
    print(f"SAR_long: {latest['SAR_long']:.2f}")
    print(f"SAR_short: {latest['SAR_short']:.2f}")

    # MACD金叉死叉信号
    print("\nMACD信号分析:")
    if latest['MACD'] > latest['MACD_signal']:
        print("  MACD在信号线上方 - 看涨信号")
    else:
        print("  MACD在信号线下方 - 看跌信号")
    
    # KDJ超买超卖分析
    print("\nKDJ超买超卖分析:")
    if latest['K'] > 80 or latest['D'] > 80:
        print("  KDJ超买区域 - 注意回调风险")
    elif latest['K'] < 20 or latest['D'] < 20:
        print("  KDJ超卖区域 - 可能反弹机会")
    else:
        print("  KDJ正常区域")
    
    # 价格与SAR关系
    print("\nSAR信号分析:")
    # if latest['close'] > latest['SAR']:
    if latest['SAR_long']:
        print("  价格在SAR上方 - 上涨趋势")
    elif latest['SAR_short']:
        print("  价格在SAR下方 - 下跌趋势")

def main():
    """
    主函数
    """
    try:
        # 获取数据并计算指标
        df = get_all_indicators()
        
        # 保存数据
        df.to_csv('btc_hourly_indicators_fixed.csv')

        # 显示结果
        display_results(df)
        
        
        print("\n" + "="*80)
        print("数据获取和计算完成！")
        
    except Exception as e:
        print(f"发生错误: {str(e)}")
        print("可能的原因: 网络连接问题、Binance API限制或数据格式变化")

if __name__ == "__main__":
    main()