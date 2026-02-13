from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import ccxt
import pandas as pd
import pandas_ta as ta
import math
import uvicorn
import time
from contextlib import asynccontextmanager
import csv
import shutil

# 应用启动时间
start_time = time.time()

# 生命周期管理
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时
    print("🚀 应用启动中...")
    yield
    # 关闭时
    print("🛑 应用关闭")

app = FastAPI(lifespan=lifespan, title="BNBS Trading Signal API")

origins = [
    "http://localhost:3000",
    "http://192.168.3.9:3000",
    "http://godseye.bnbscommunity.com",
    "http://www.godseye.bnbscommunity.com",
    "http://bnbchain.bnbscommunity.com",
    "https://localhost:3000",
    "https://192.168.3.9:3000",
    "https://godseye.bnbscommunity.com",
    "https://www.godseye.bnbscommunity.com",
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
# 简单的内存缓存
cache = {}
CACHE_DURATION = 60  # 缓存60秒

# ✅ 添加健康检查端点
@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "running",  # 只要服务在运行就返回健康
        "startup": True,       # 标记为启动阶段
        "timestamp": time.time()
    }

@app.get("/")
def getSignals(timeFrame: str,symbol: str):
    """
    主函数
    """
    if (timeFrame == None or timeFrame == ""):
        return
    if (symbol == None or symbol == ""):
        return
    
    # 创建缓存键
    cache_key = f"{timeFrame}_{symbol}"
    
    # 检查缓存
    if cache_key in cache:
        cached_time, cached_data = cache[cache_key]
        if time.time() - cached_time < CACHE_DURATION:
            print("返回缓存数据")
            return cached_data
        
    try:
        # 获取数据并计算指标
        df = get_all_indicators(timeFrame, symbol)
        
        display = getDisplay(symbol, df)

        # 存入缓存
        cache[cache_key] = (time.time(), display)

        print("数据获取和计算完成！")
        return display
        
    except Exception as e:
        print(f"发生错误: {str(e)}")
        return None

def get_all_indicators(timeFrame, symbol):
    """
    获取数据并计算所有指标
    """
    # 1. 获取数据
    df = get_exchange_data(timeFrame, symbol)
    
    # 2. 计算MACD
    df = calculate_macd(df)
    
    # 3. 计算KDJ
    df = calculate_kdj(df)
    
    # 4. 计算SAR
    df = calculate_sar(df)
    
    return df

def get_exchange_data(timeFrame, symbol):
    exchanges = [
        ('okx', ccxt.okx),
        ('bybit', ccxt.bybit),
        ('gateio', ccxt.gateio),
        ('kucoin', ccxt.kucoin),
        ('binance', ccxt.binance),
        ('huobi', ccxt.huobi)
    ]
    
    last_error = None
    for exchange_name, exchange_class in exchanges:
        try:
            print(f"尝试从 {exchange_name} 获取数据...")
            exchange = exchange_class({
                'enableRateLimit': True,
                'timeout': 10000
            })
            
            ohlcv = exchange.fetch_ohlcv(
                symbol=symbol,
                timeframe=timeFrame,
                limit=KLINE_LIMIT
            )
            
            print(f"成功从 {exchange_name} 获取数据")
            # 转换为DataFrame
            df = pd.DataFrame(ohlcv, columns=['timestamp', 'open', 'high', 'low', 'close', 'volume'])
            df['datetime'] = pd.to_datetime(df['timestamp'], unit='ms')
            df.set_index('datetime', inplace=True)
            return df
            
        except Exception as e:
            print(f"{exchange_name} 失败: {str(e)}")
            last_error = e
            continue
    
    # 所有交易所都失败
    raise Exception(f"所有交易所都失败: {last_error}")

# def get_exchange_data(timeFrame, symbol):
#     try:
#         """
#         获取BTC/USDT小时线数据
#         """
#         exchange = ccxt.okx({
#             'rateLimit': 1200,
#             'enableRateLimit': True,
#             'timeout': 30000,
#             'options': {
#                 'defaultType': 'spot',
#             }
#         })

#         # EXCHANGES = {
#         #     'kucoin': {
#         #         'class': ccxt.kucoin,
#         #         'config': {
#         #             'rateLimit': 1000,
#         #             'enableRateLimit': True,
#         #             'timeout': 10000
#         #         },
#         #         'supported_pairs': ['BTC/USDT', 'ETH/USDT', 'BNB/USDT', 'SOL/USDT']
#         #     },
#         #     'bybit': {
#         #         'class': ccxt.bybit,
#         #         'config': {
#         #             'rateLimit': 1000,
#         #             'enableRateLimit': True,
#         #             'timeout': 10000
#         #         }
#         #     },
#         #     'gateio': {
#         #         'class': ccxt.gateio,
#         #         'config': {
#         #             'rateLimit': 1000,
#         #             'enableRateLimit': True,
#         #             'timeout': 10000
#         #         }
#         #     },
#         #     'okx': {
#         #         'class': ccxt.okx,
#         #         'config': {
#         #             'rateLimit': 1000,
#         #             'enableRateLimit': True,
#         #             'timeout': 10000
#         #         }
#         #     },
#         #     'huobi': {
#         #         'class': ccxt.huobi,
#         #         'config': {
#         #             'rateLimit': 1000,
#         #             'enableRateLimit': True,
#         #             'timeout': 10000
#         #         }
#         #     },
#         #     'coinbase': {
#         #         'class': ccxt.coinbase,
#         #         'config': {
#         #             'rateLimit': 1000,
#         #             'enableRateLimit': True,
#         #             'timeout': 10000,
#         #             'apiKey': '',  # Coinbase 可能需要 API Key
#         #             'secret': '',
#         #         }
#         #     }
#         # }
        
#         # 获取BTC/USDT小时K线数据
#         ohlcv = exchange.fetch_ohlcv(
#             symbol = symbol,
#             timeframe = timeFrame,
#             limit = KLINE_LIMIT  # 获取的数据条数
#         )

#         if not ohlcv or len(ohlcv) == 0:
#             raise Exception("没有获取到数据")
        
#         # 转换为DataFrame
#         df = pd.DataFrame(
#             ohlcv,
#             columns=['timestamp', 'open', 'high', 'low', 'close', 'volume']
#         )
        
#         # 转换时间戳为可读格式
#         df['datetime'] = pd.to_datetime(df['timestamp'], unit='ms')
#         df.set_index('datetime', inplace=True)
        
#         return df
#     except Exception as e:
#         print(f"获取交易所数据失败: {str(e)}")
#         raise  # 重新抛出异常让上层处理

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
    if math.isnan(latest['SAR_long']) == False:
        SAR = "〇"
    else:
        SAR = "×"

    # MACD金叉死叉信号
    if latest['MACD'] > latest['MACD_signal']:
        MACD = "〇"
    else:
        MACD = "×"
    
    # KDJ超买超卖分析
    if latest['K'] > latest['D']:
        KDJ = "〇"
    else:
        KDJ = "×"

    if latest['K'] > 80 or latest['D'] > 80:
        KDJ_OVER = "Overbought"
    elif latest['K'] < 20 or latest['D'] < 20:
        KDJ_OVER = "Oversell"
    else:
        KDJ_OVER = "Normal"

    #时间，价格
    return [symbol, df.index[-1], f"{latest['close']:.3f}", SAR, MACD, KDJ, KDJ_OVER]

def is_number(value):
    """判断是否为数字的最简单方法"""
    try:
        float(value)  # 尝试转为浮点数
        return True
    except (ValueError, TypeError):
        return False
    

#CSVデータ取得
@app.get("/csv")
def getCsv():
    datalist = []
    with open("export-tokenholders-for-contract-0xC07ef1C7af6112C34A110809C6c8Efb343e63A64.csv") as csvfile:
        reader = csv.reader(csvfile)

        i = 0
        for line in reader:
            if (i == 0):
                i = i + 1
                continue
            
            newline = []
            newline.append(i)
            newline.append(line[0])

            count = int(float(line[1].replace(",", "")))
            newline.append(count)

            percent = (count / TOTAL_COUNT) * 100
            newline.append(f"{percent:.5f}%")

            datalist.append(newline)
            i = i + 1

    return datalist

@app.post("/upload")
def upload(file: UploadFile = File(...)):        
    # 保存文件
    with open("export-tokenholders-for-contract-0xC07ef1C7af6112C34A110809C6c8Efb343e63A64.csv", "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
    
if __name__ == '__main__':
    # 关键：监听 0.0.0.0（所有网络接口）
    uvicorn.run(app, host='0.0.0.0', port=8080)