import { NextRequest, NextResponse } from 'next/server';
import { ChatOpenAI } from "@langchain/openai";
import { HumanMessage } from "@langchain/core/messages";

// 预定义的加密货币列表（简化版）
const CRYPTO_LIST = [
    // 前20名（主要加密货币）
    { symbol: "BTC", name: "Bitcoin", chinese: "比特币" },
    { symbol: "ETH", name: "Ethereum", chinese: "以太坊" },
    { symbol: "USDT", name: "Tether", chinese: "泰达币" },
    { symbol: "BNB", name: "Binance Coin", chinese: "币安币" },
    { symbol: "SOL", name: "Solana", chinese: "索拉纳" },
    { symbol: "USDC", name: "USD Coin", chinese: "美元币" },
    { symbol: "XRP", name: "Ripple", chinese: "瑞波币" },
    { symbol: "TON", name: "Toncoin", chinese: "Ton币" },
    { symbol: "DOGE", name: "Dogecoin", chinese: "狗狗币" },
    { symbol: "ADA", name: "Cardano", chinese: "艾达币" },
    { symbol: "TRX", name: "TRON", chinese: "波场币" },
    { symbol: "AVAX", name: "Avalanche", chinese: "雪崩币" },
    { symbol: "SHIB", name: "Shiba Inu", chinese: "柴犬币" },
    { symbol: "DOT", name: "Polkadot", chinese: "波卡币" },
    { symbol: "LINK", name: "Chainlink", chinese: "链链接" },
    { symbol: "MATIC", name: "Polygon", chinese: "马蹄" },
    { symbol: "NEAR", name: "NEAR Protocol", chinese: "NEAR协议" },
    { symbol: "UNI", name: "Uniswap", chinese: "去中心化交易所" },
    { symbol: "BCH", name: "Bitcoin Cash", chinese: "比特币现金" },
    { symbol: "LTC", name: "Litecoin", chinese: "莱特币" },
    
    // 21-50名
    { symbol: "ICP", name: "Internet Computer", chinese: "互联网计算机" },
    { symbol: "APT", name: "Aptos", chinese: "阿普托斯" },
    { symbol: "ATOM", name: "Cosmos", chinese: "宇宙币" },
    { symbol: "FIL", name: "Filecoin", chinese: "文件币" },
    { symbol: "LEO", name: "LEO Token", chinese: "LEO代币" },
    { symbol: "ETC", name: "Ethereum Classic", chinese: "以太经典" },
    { symbol: "STX", name: "Stacks", chinese: "堆栈币" },
    { symbol: "XLM", name: "Stellar", chinese: "恒星币" },
    { symbol: "OKB", name: "OKB", chinese: "欧易币" },
    { symbol: "TAO", name: "Bittensor", chinese: "比特张量" },
    { symbol: "HBAR", name: "Hedera", chinese: "海德拉" },
    { symbol: "INJ", name: "Injective", chinese: "注射币" },
    { symbol: "KAS", name: "Kaspa", chinese: "卡斯帕" },
    { symbol: "VET", name: "VeChain", chinese: "唯链" },
    { symbol: "MKR", name: "Maker", chinese: "创客币" },
    { symbol: "OP", name: "Optimism", chinese: "乐观币" },
    { symbol: "RNDR", name: "Render", chinese: "渲染币" },
    { symbol: "IMX", name: "Immutable X", chinese: "不可变X" },
    { symbol: "GRT", name: "The Graph", chinese: "图表币" },
    { symbol: "ARB", name: "Arbitrum", chinese: "仲裁币" },
    { symbol: "MNT", name: "Mantle", chinese: "曼特尔" },
    { symbol: "FDUSD", name: "First Digital USD", chinese: "第一数字美元" },
    { symbol: "THETA", name: "Theta Network", chinese: "西塔网络" },
    { symbol: "XTZ", name: "Tezos", chinese: "特所思" },
    { symbol: "TIA", name: "Celestia", chinese: "天体币" },
    { symbol: "LDO", name: "Lido DAO", chinese: "利多道" },
    { symbol: "ALGO", name: "Algorand", chinese: "阿尔戈兰德" },
    { symbol: "FLOKI", name: "Floki", chinese: "弗洛基" },
    { symbol: "BSV", name: "Bitcoin SV", chinese: "比特币SV" },
    { symbol: "RUNE", name: "THORChain", chinese: "雷神链" },
    
    // 51-80名
    { symbol: "ORDI", name: "Ordinals", chinese: "序号币" },
    { symbol: "BONK", name: "Bonk", chinese: "邦克币" },
    { symbol: "QNT", name: "Quant", chinese: "数量币" },
    { symbol: "FTM", name: "Fantom", chinese: "幻影币" },
    { symbol: "SEI", name: "Sei", chinese: "塞伊币" },
    { symbol: "AAVE", name: "Aave", chinese: "Aave借贷" },
    { symbol: "EGLD", name: "MultiversX", chinese: "多元宇宙X" },
    { symbol: "SUI", name: "Sui", chinese: "隋币" },
    { symbol: "FLOW", name: "Flow", chinese: "流币" },
    { symbol: "SNX", name: "Synthetix", chinese: "合成资产" },
    { symbol: "HNT", name: "Helium", chinese: "氦气币" },
    { symbol: "AXS", name: "Axie Infinity", chinese: "阿蟹无限" },
    { symbol: "SAND", name: "The Sandbox", chinese: "沙盒" },
    { symbol: "MINA", name: "Mina", chinese: "米娜币" },
    { symbol: "KAVA", name: "Kava", chinese: "卡瓦币" },
    { symbol: "EOS", name: "EOS", chinese: "柚子币" },
    { symbol: "CFX", name: "Conflux", chinese: "树图" },
    { symbol: "STRK", name: "Starknet", chinese: "星网币" },
    { symbol: "GALA", name: "Gala", chinese: "嘉拉币" },
    { symbol: "NEO", name: "NEO", chinese: "小蚁币" },
    { symbol: "PEPE", name: "Pepe", chinese: "佩佩蛙币" },
    { symbol: "DYM", name: "Dymension", chinese: "维度币" },
    { symbol: "BTT", name: "BitTorrent", chinese: "比特流" },
    { symbol: "XEC", name: "eCash", chinese: "电子现金" },
    { symbol: "USDD", name: "USDD", chinese: "去中心化美元" },
    { symbol: "BEAM", name: "Beam", chinese: "光束币" },
    { symbol: "ENS", name: "Ethereum Name Service", chinese: "以太坊域名服务" },
    { symbol: "OSMO", name: "Osmosis", chinese: "渗透币" },
    { symbol: "GT", name: "GateToken", chinese: "门罗币" },
    { symbol: "WEMIX", name: "WEMIX", chinese: "威米克斯" },
    
    // 81-100名
    { symbol: "WOO", name: "WOO Network", chinese: "WOO网络" },
    { symbol: "PYTH", name: "Pyth Network", chinese: "Pyth网络" },
    { symbol: "ASTR", name: "Astar", chinese: "阿斯塔" },
    { symbol: "COMP", name: "Compound", chinese: "复合币" },
    { symbol: "MANA", name: "Decentraland", chinese: "分散之地" },
    { symbol: "CAKE", name: "PancakeSwap", chinese: "薄饼交换" },
    { symbol: "KLAY", name: "Klaytn", chinese: "克莱顿" },
    { symbol: "AGIX", name: "SingularityNET", chinese: "奇点网络" },
    { symbol: "CHZ", name: "Chiliz", chinese: "奇力兹" },
    { symbol: "RON", name: "Ronin", chinese: "浪人币" },
    { symbol: "DYDX", name: "dYdX", chinese: "dYdX去中心化交易所" },
    { symbol: "FLR", name: "Flare", chinese: "闪光币" },
    { symbol: "FET", name: "Fetch.ai", chinese: "Fetch.ai" },
    { symbol: "CRV", name: "Curve DAO", chinese: "曲线道" },
    { symbol: "GNO", name: "Gnosis", chinese: "诺西斯" },
    { symbol: "OM", name: "MANTRA", chinese: "曼陀罗" },
    { symbol: "ZIL", name: "Zilliqa", chinese: "齐利卡" },
    { symbol: "FTT", name: "FTX Token", chinese: "FTX代币" },
    { symbol: "BGB", name: "Bitget Token", chinese: "比特儿代币" },
    { symbol: "JUP", name: "Jupiter", chinese: "木星币" }
];

export async function POST(request: NextRequest) {
    try {
        const { message } = await request.json();

        // 免费的 OpenAI 模型
        const llm = new ChatOpenAI({
            model: "openai/gpt-3.5-turbo",  // ✅ 通常有免费额度
            apiKey: process.env.OPENROUTER_API_KEY,
            configuration: {
                baseURL: "https://openrouter.ai/api/v1",
                defaultHeaders: {
                    "HTTP-Referer": "http://localhost:3000",
                    "X-Title": "Crypto Bot",
                },
            },
            temperature: 0.3,
            maxTokens: 100,
        });

        // 构建系统提示词 - 核心逻辑
        const systemPrompt = `你是一个加密货币代码提取器。请从用户输入中提取加密货币的代码。

            规则：
            1. 只提取加密货币的符号代码（如BTC、ETH）
            2. 忽略所有其他文本
            3. 如果有多个币种，用逗号分隔
            4. 如果找不到明确的币种，返回"未识别到加密货币"
            5. 支持中文，英语，日语输入

            已知加密货币列表：
            ${CRYPTO_LIST.map(c => `${c.symbol} - ${c.name} - ${c.chinese}`).join('\n')}

            示例：
            用户："我想关注比特币" -> 提取结果："BTC"
            用户："添加BTC和以太坊" -> 提取结果："BTC,ETH"
            用户："今天天气不错" -> 提取结果："未识别到加密货币"

            现在请处理以下输入：`;

        // 调用OpenAI
        const response = await llm.invoke([
            new HumanMessage(systemPrompt + `\n用户输入："${message}"\n提取结果：`)
        ]);

        const extracted = response.content.toString().trim();

        // 验证提取结果是否有效
        const isValidCrypto = (symbol: string) => {
            return CRYPTO_LIST.some(crypto =>
                crypto.symbol.toUpperCase() === symbol.toUpperCase().trim()
            );
        };

        // 处理多个币种
        const symbols = extracted.split(/[,，\s]+/).filter(Boolean);
        console.log(symbols);
        const validSymbols = symbols.filter(symbol => {
            // 如果是"未识别到加密货币"则跳过
            if (symbol.includes("未识别")) return false;
            return isValidCrypto(symbol);
        });

        if (validSymbols.length === 0) {
            return NextResponse.json({
                success: false,
                message: "Unable to find any crypto.",
                symbols: []
            });
        } else {
            return NextResponse.json({
                success: true,
                message: `Found ${validSymbols} crypto.`,
                symbols: validSymbols
            });
        }

    } catch (error) {
        console.error("API Error:", error);
        return NextResponse.json(
            {
                success: false,
                message: "Request processing failed. Please verify your network connection.",
                symbols: []
            },
            { status: 500 }
        );
    }
}

// 获取支持的加密货币列表
export async function GET() {
    return NextResponse.json({
        supportedCryptos: CRYPTO_LIST,
        count: CRYPTO_LIST.length,
        lastUpdated: new Date().toISOString()
    });
}