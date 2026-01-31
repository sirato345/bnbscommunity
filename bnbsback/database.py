import sqlite3

DATABASE_URL = "app.db"

# ChainId
# Bitcoin 20000000000001
# Solana 1151111081099710
# Sui 9270000000000000
OTHER_CHAIN = ["20000000000001", "1151111081099710", "9270000000000000"]
REWARD_STANDARD = 50

class TradeHistory:
    fromAddress: str
    toAddress: str
    fromAmountUSD: float
    toAmountUSD: float
    fromChainId: str
    toChainId: str
    gasUSD: float
    feeUSD: float

# 数据库初始化
def init_db():
    """初始化数据库和表"""
    conn = sqlite3.connect(DATABASE_URL)
    cursor = conn.cursor()
    
    # 创建交易历史表
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS TRADE_HISTORY (
        ID INTEGER PRIMARY KEY AUTOINCREMENT,
        FROM_ADDRESS VARCHAR(255) NOT NULL,
        TO_ADDRESS VARCHAR(255) NOT NULL,
        FROM_AMOUNT_USD DECIMAL(12,2) NOT NULL DEFAULT 0,
        TO_AMOUNT_USD DECIMAL(12,2) NOT NULL DEFAULT 0,
        FROM_CHAIN_ID VARCHAR(50) NOT NULL,
        TO_CHAIN_ID VARCHAR(50) NOT NULL,
        GAS_USD DECIMAL(12,2) NOT NULL DEFAULT 0,
        FEE_USD DECIMAL(12,2) NOT NULL DEFAULT 0,
        CREATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # 创建积分表
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS REWARD_HISTORY (
        ID INTEGER PRIMARY KEY AUTOINCREMENT,
        FROM_ADDRESS VARCHAR(255) NOT NULL UNIQUE,
        RECEIVE_ADDRESS VARCHAR(255) NOT NULL,
        REWARD_BNBS DECIMAL(8,0) NOT NULL DEFAULT 0,
        RECEIVED_BNBS DECIMAL(8,0) NOT NULL DEFAULT 0,
        TOTAL_TRADE_AMOUNT_USD DECIMAL(12,2) NOT NULL DEFAULT 0,
        CREATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    
    # 创建其他表...
    
    conn.commit()
    conn.close()

# 登录交易记录，计算积分
def insert_trade(tradeHistory: TradeHistory):
    """初始化数据库和表"""
    conn = sqlite3.connect(DATABASE_URL)
    cursor = conn.cursor()
    
    # 插入交易历史表
    cursor.execute("""
    INSERT INTO TRADE_HISTORY (
        FROM_ADDRESS,
        TO_ADDRESS,
        FROM_AMOUNT_USD,
        TO_AMOUNT_USD,
        FROM_CHAIN_ID,
        TO_CHAIN_ID,
        GAS_USD,
        FEE_USD) VALUES (
                 """ + tradeHistory.fromAddress + """,
                 """ + tradeHistory.toAddress + """,
                 """ + tradeHistory.fromAmountUSD + """,
                 """ + tradeHistory.toAmountUSD + """,
                 """ + tradeHistory.fromChainId + """,
                 """ + tradeHistory.toChainId + """,
                 """ + tradeHistory.gasUSD + """,
                 """ + tradeHistory.feeUSD + """
                 )
    )
    """)

    cursor.execute("""
        SELECT * FROM REWARD_HISTORY WHERE FROM_ADDRESS = """ + tradeHistory.fromAddress + """""")
    row = cursor.fetchone()
    if row:
        print(f"找到数据: {row}")
        # 增加金额
        increaseAmount = (totalTradeAmountUSD % REWARD_STANDARD) + tradeHistory.fromAmountUSD
        # 增加奖励（向下取整）
        increaseBNBs = round(increaseAmount // REWARD_STANDARD)
        rewardBNBs = row[3] + increaseBNBs
        totalTradeAmountUSD = row[5] + tradeHistory.fromAmountUSD

        # 更新积分历史表
        cursor.execute("""
        UPDATE REWARD_HISTORY SET
            REWARD_BNBS = """ + rewardBNBs + """,
            TOTAL_TRADE_AMOUNT_USD = """ + totalTradeAmountUSD + """
        WHERE FROM_ADDRESS = """ + tradeHistory.fromAddress + """
        """)
    else:
        print("未找到数据") 
        
        # evm chain，设定发送地址为接收地址
        if tradeHistory.fromChainId not in OTHER_CHAIN:
            tradeHistory.toAddress = tradeHistory.fromAddress

        # 插入积分历史表
        cursor.execute("""
        INSERT INTO REWARD_HISTORY (
            FROM_ADDRESS,
            RECEIVE_ADDRESS,
            REWARD_BNBS,
            RECEIVED_BNBS,
            TOTAL_TRADE_AMOUNT_USD) VALUES (
                    """ + tradeHistory.fromAddress + """,
                    """ + tradeHistory.toAddress + """,
                    0,
                    0，
                    """ + tradeHistory.fromAmountUSD + """
                    )
        )
        """)

    conn.commit()
    conn.close()
