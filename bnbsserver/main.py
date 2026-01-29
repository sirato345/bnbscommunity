from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import ccxt
import pandas as pd
import pandas_ta as ta
import math

app = FastAPI()

origins = [
    "http://127.0.0.1:8000",
    "http://localhost:3000",
    "http://server.bnbscommunity.com",
    "http://bnbchain.bnbscommunity.com",
    "https://127.0.0.1:8000",
    "https://localhost:3000",
    "https://server.bnbscommunity.com",
    "https://bnbchain.bnbscommunity.com",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization", "X-CSRF-Token", "Access-Control-Allow-Origin"]
)

TOTAL_COUNT = 21000000
KLINE_LIMIT = 100
TIME_FRAME_1H = '1h'
TIME_FRAME_4H = '4h'
SYMBOL_BTC = 'BTC/USDT'

@app.get("/")
def getSignals():
    timeFrame = TIME_FRAME_1H
    symbol = SYMBOL_BTC
    """
    主函数
    """
    if (timeFrame == None or timeFrame == ""):
        timeFrame = TIME_FRAME_1H
    if (symbol == None or symbol == ""):
        symbol = SYMBOL_BTC
    
    try:
        # 获取数据并计算指标
        df = get_all_indicators(timeFrame, symbol)
        
        display = getDisplay(symbol, df)

        print("数据获取和计算完成！")
        return display
        
    except Exception as e:
        print(f"发生错误: {str(e)}")

def get_all_indicators(timeFrame, symbol):
    """
    获取数据并计算所有指标
    """
    print("正在从Binance获取数据...")
    
    # 1. 获取数据
    df = get_binance_data(timeFrame, symbol)
    
    print(f"获取到 {symbol}  {timeFrame}  {len(df)} 条数据")
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
    
    return df

def get_binance_data(timeFrame, symbol):
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
        symbol = symbol,
        timeframe = timeFrame,
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

def calculate_sar(df):
    """
    计算抛物线转向指标SAR
    """
    # 使用pandas-ta计算SAR
    sar = ta.psar(
        high = df['high'],
        low = df['low'],
        acceleration = 0.02,
        maximum = 0.2
    )

    # sar = talib.SAR(df['high'], df['low'], acceleration = 0.02, maximum = 0.2)

    df['SAR_long'] = sar.iloc[:, 0]# 多头SAR（通常当价格上涨时显示）
    df['SAR_short'] = sar.iloc[:, 1]# 空头SAR（通常当价格下跌时显示）
    
    return df

def getDisplay(symbol, df):
    
    #最新一条数据
    latest = df.iloc[-1]

    SAR = ""
    MACD = ""
    KDJ = ""
    KDJ_OVER = ""

    # 价格与SAR关系
    print("\nSAR信号分析:")
    if math.isnan(latest['SAR_long']) == False:
        SAR = "〇"
    else:
        SAR = "×"

    # MACD金叉死叉信号
    print("\nMACD信号分析:")
    if latest['MACD'] > latest['MACD_signal']:
        MACD = "〇"
    else:
        MACD = "×"
    
    # KDJ超买超卖分析
    print("\nKDJ超买超卖分析:")
    if latest['K'] > latest['D']:
        KDJ = "〇"
    else:
        KDJ = "×"

    if latest['K'] > 80 or latest['D'] > 80:
        KDJ_OVER = "超买"
    elif latest['K'] < 20 or latest['D'] < 20:
        KDJ_OVER = "超卖"
    else:
        KDJ_OVER = "正常区间"

    print(f"快线: {latest['MACD']:.4f}, 慢线: {latest['MACD_signal']:.4f}")
    print(f"K: {latest['K']:.2f}, D: {latest['D']:.2f}")
    print(f"SAR_long: {latest['SAR_long']:.2f}")
    print(f"SAR_short: {latest['SAR_short']:.2f}")
    #时间，价格
    return [symbol, df.index[-1], f"{latest['close']:.2f}", SAR, MACD, KDJ, KDJ_OVER]

def is_number(value):
    """判断是否为数字的最简单方法"""
    try:
        float(value)  # 尝试转为浮点数
        return True
    except (ValueError, TypeError):
        return False