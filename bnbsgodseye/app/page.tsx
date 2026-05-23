'use client';

import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import Image from 'next/image';
import CryptoExtractor from './CryptoExtractor';
import { MobileView } from "react-device-detect";
import "./globals.css";

// 定义数据类型
interface CryptoData {
  symbol: string;
  timestamp: string;
  price: string;
  sar: string;
  macd: string;
  kdj: string;
  kdjStatus: string;
  timeframe: string;
}

type DataSource = {
  timeframe: string;
  symbol: string;
};

// ─────────────────────────────────────────────
// 定数
// ─────────────────────────────────────────────
const API_BASE = 'https://bnbs-django-275599637949.asia-northeast1.run.app';
// const API_BASE = 'http://127.0.0.1:8000';

// ─── 统一的时间格式化函数 ─────────────────────
const formatTime = (date: Date): string => {
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  const seconds = date.getSeconds().toString().padStart(2, '0');
  return `${hours}:${minutes}:${seconds}`;
};

export default function CryptoScreenerPage() {
  const [data, setData] = useState<CryptoData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<string>('');
  const [dataSource, setDataSources] = useState<DataSource[]>([
    { timeframe: '1h', symbol: 'BTC/USDT' },
    { timeframe: '4h', symbol: 'BTC/USDT' },
    { timeframe: '1h', symbol: 'ETH/USDT' },
    { timeframe: '4h', symbol: 'ETH/USDT' },
    { timeframe: '1h', symbol: 'BNB/USDT' },
    { timeframe: '4h', symbol: 'BNB/USDT' },
    { timeframe: '1h', symbol: 'DOGE/USDT' },
    { timeframe: '4h', symbol: 'DOGE/USDT' },
  ]); 

  const [param] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    return new URLSearchParams(window.location.search).get('homepage');
  });

  // 定义回调函数，接收子组件数据
  const onAddCrypto = (crypto: DataSource[]) => {
    const newcrypto = crypto.filter(newItem => 
      !dataSource.some(existingItem => 
        existingItem.symbol === newItem.symbol && existingItem.timeframe === newItem.timeframe  
      )
    );
    if (newcrypto.length === 0) {
      return;
    }
    console.log('添加新的交易对:', newcrypto);
    // 更新数据源并获取新数据
    fetchAddedData(newcrypto);
    setDataSources(prev => [...prev, ...newcrypto]);
  };

  // 辅助函数：根据币种和价格值智能格式化
  function formatPrice(symbol: string, priceStr: string): string {
    const price = parseFloat(priceStr);
    const baseSymbol = symbol.split('/')[0]; // 获取币种名称
    
    // 根据币种设置小数位数
    if (baseSymbol === 'DOGE') {
      return price.toFixed(5);  // DOGE 保留5位
    } else if (baseSymbol === 'SHIB' || baseSymbol === 'PEPE') {
      return price.toFixed(8);  // 更小的币种保留8位
    } else {
      return price.toFixed(2);  // BTC, ETH, BNB 等保留2位
    }
  }

  // ─── 一括リモートデータ取得 ───────────────────
  /**
   * dataSource を1回の POST で送信し、バックエンドがマルチスレッドで処理した結果を受け取る。
   * レスポンス形式:
   *   { results: { "1h_BTC/USDT": [...], ... }, errors: { "4h_BNB/USDT": "msg" } }
   */
  async function getRemoteData(targets: DataSource[]): Promise<CryptoData[]> {
    const response = await axios.post(
      `${API_BASE}/signals`,
      { targets },
      { timeout: 30000 },
    );

    const { results, errors } = response.data as {
      results: Record<string, string[]>;
      errors:  Record<string, string>;
    };

    // 成功行をCryptoData[]に変換（targetsの順序を維持）
    const cryptoList: CryptoData[] = targets.map(source => {
      const key = `${source.timeframe}_${source.symbol}`;
      const raw = results[key];

      if (!raw) {
        // バックエンド側でエラーになった行はプレースホルダーを返す
        console.error(`[bulk] error for ${key}:`, errors[key] ?? 'unknown');
        return {
          symbol:    source.symbol,
          timestamp: new Date().toISOString(),
          price:     'Update',
          sar:       'Update',
          macd:      'Update',
          kdj:       'Update',
          kdjStatus: 'Update',
          timeframe: source.timeframe,
        } as CryptoData;
      }

      return {
        symbol:    raw[0],
        timestamp: raw[1],
        price:     formatPrice(source.symbol, raw[2]),
        sar:       raw[3],
        macd:      raw[4],
        kdj:       raw[5],
        kdjStatus: raw[6],
        timeframe: source.timeframe,
      } as CryptoData;
    });

    return cryptoList;
  }

  // 获取数据
  const fetchData = useCallback(async () => {
    console.log('Fetching data for sources:', dataSource);
    setLoading(true);
    setError('');
    
    try {
      const results = await getRemoteData(dataSource);
      setData(results);
      setLastUpdate(formatTime(new Date()));
    } catch (err) {
      setError('Failed to fetch data, please check your network connection or API status.');
      console.error('Failed to fetch data:', err);
    } finally {
      setLoading(false);
    }
  }, [dataSource]);

  // 获取追加数据
  const fetchAddedData = async (dataSource: DataSource[]) => {
    setLoading(true);
    setError('');
    
    try {
      const results = await getRemoteData(dataSource);
      setData(prevData => [...prevData, ...results]);  // ※基于最新状态更新  
      setLastUpdate(formatTime(new Date()));
    } catch (err) {
      setError('Failed to fetch data, please check your network connection or API status.');
      console.error('Failed to fetch data:', err);
    } finally {
      setLoading(false);
    }
  };

  // 计算两个时间框架的总看跌数量
  function calculateSignal(data1h: CryptoData, data4h: CryptoData): string {
    let signal;
    // 计算每个时间框架的看跌数量
    const bearishCount1h = [data1h.sar, data1h.macd, data1h.kdj].filter(v => v === '×').length;
    const bearishCount4h = [data4h.sar, data4h.macd, data4h.kdj].filter(v => v === '×').length;
    
    const total = bearishCount1h + bearishCount4h;
    if (total == 0 || total == 1) {
      signal = "Up";
    } else if (total == 2) {
      signal = "Warn";
    } else {
      signal = "Down";
    }
    return signal;
  }
  
  // 初始加载
  useEffect(() => {
    // 只在组件挂载时执行一次
    fetchData();
    // 设置定时刷新（每600秒 = 10分钟）
    const interval = setInterval(fetchData, 600000); // 600000ms = 10分钟
    // 组件卸载时清理定时器
    return () => clearInterval(interval);
  }, [fetchData]); // 空依赖数组

  // 按交易对分组显示
  const groupBySymbol = (data: CryptoData[]) => {
    const grouped: Record<string, CryptoData[]> = {};
    data.forEach(item => {
      if (!grouped[item.symbol]) {
        grouped[item.symbol] = [];
      }
      grouped[item.symbol].push(item);
    });
    return grouped;
  };

  const groupedData = groupBySymbol(data);

  // 指标样式映射
  const getIndicatorStyle = (value: string) => {
    switch (value) {
      case '〇':
        return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300';
      case '×':
        return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300';
      case 'OverBuy':
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300';
      case 'OverSell':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300';
    }
  };

  return (
    <>
      <div className="w-full md:w-3/5 lg:w-3/5 md:mx-auto bg-white p-4 md:p-4 min-h-screen">
        <div className="max-w-7xl mx-auto">
          {/* 标题区域 */}
          <div className="mb-5">
            {param === 'true' ? '' : 
            (<h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2 flex items-center ml-0.5">
              <Image src="BNBs.svg" alt="Logo" width={32} height={32} className="w-8 h-8 rounded-full object-cover"/>
              <span className='px-1 font-semibold text-[20px] text-black text-left'>BNBs AI Analyse</span>
            </h1>)}
            
            <div className="flex flex-wrap justify-end gap-4">
              <div className="flex items-center gap-4">
                {lastUpdate && (
                  <span className="text-sm text-gray-500 dark:text-gray-400">
                    Last Update: {lastUpdate}
                  </span>
                )}

                <button
                  onClick={() => fetchData()}
                  disabled={loading}
                  className="flex 
                              items-center 
                              justify-center 
                              min-w-[80px]
                              p-2.5 
                              ml-[2px] 
                              mt-[5px] 
                              mr-[5px] 
                              mb-[5px] 
                              bg-[#4CAF50] 
                              text-white 
                              text-sm
                              font-sans
                              font-medium
                              border-none 
                              rounded-r-md 
                              cursor-pointer 
                              w-[80px] 
                              h-9
                              hover:bg-[#4CAF50]
                              active:bg-[#4CAF50]
                              transition-colors
                              duration-200
                              disabled:opacity-50
                              disabled:cursor-not-allowed"
                >
                  {loading ? (
                  <span className="spinner"></span>
                  ) : (
                    "Update"
                  )}
                </button>
              </div>
            </div>
          </div>

          {/* 错误提示 */}
          {error && (
            <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg dark:bg-red-900/20 dark:border-red-800">
              <div className="flex items-center">
                <span className="text-red-600 dark:text-red-400">⚠️ {error}</span>
              </div>
            </div>
          )}

          {/* 数据表格 */}
          <div className="grid gap-4">
            {Object.entries(groupedData).map(([symbol, symbolData]) => (
              <div key={symbol} className="bg-white dark:bg-gray-800 rounded-xl shadow-lg overflow-hidden">
                {/* 交易对标题 */}
                <div className="px-2 py-0 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <h2 className="text-x0.5 font-semibold text-gray-900 dark:text-white">
                        {symbol}
                      </h2>
                    </div>
                    <div className="text-x0.5 font-semibold text-gray-900 dark:text-white">
                      Price: {symbolData[0]?.price || 'Update'}
                    </div>
                  </div>
                </div>

                {/* 表格 */}
                {/* 表格 - 移除横向滚动，添加纵向滚动控制 */}
                <div key={symbol} className="-mx-2 overflow-y-auto max-h-[400px]">
                  <table className="w-full divide-y divide-gray-200 dark:divide-gray-700 table-fixed">
                    <thead className="bg-gray-50 dark:bg-gray-900">
                      <tr>
                        <th className="w-[14%] sm:w-[14%] px-1 py-1 text-center text-[10px] sm:text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          TIME
                        </th>
                        <th className="w-[14%] sm:w-[14%] px-1 py-1 text-center text-[10px] sm:text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          SAR
                        </th>
                        <th className="w-[14%] sm:w-[14%] px-1 py-1 text-center text-[10px] sm:text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          MACD
                        </th>
                        <th className="w-[14%] sm:w-[14%] px-1 py-1 text-center text-[10px] sm:text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          KDJ
                        </th>
                        <th className="w-[20%] sm:w-[20%] px-1 py-1 text-center text-[10px] sm:text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          KDJ STATUS
                        </th>
                        <th className="w-[24%] sm:w-[24%] px-1 py-1 text-center text-[10px] sm:text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          SIGNAL
                        </th>
                      </tr>
                    </thead>
                    
                    <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                      {symbolData.length >= 1 && (
                        <tr className="bg-white dark:bg-gray-800">
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center whitespace-nowrap">
                            {/* 1.5倍放大：1h/4h标签 */}
                            <span className="inline-flex items-center justify-center px-2 py-1 sm:px-3 sm:py-1.5 rounded-full text-xs sm:text-sm font-medium bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300">
                              {symbolData[0].timeframe}
                            </span>
                          </td>
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center whitespace-nowrap">
                            {/* 1.5倍放大：SAR圆圈 */}
                            <span className={`inline-flex items-center justify-center w-7 h-7 sm:w-9 sm:h-9 rounded-full text-sm sm:text-base font-bold ${getIndicatorStyle(symbolData[0].sar)}`}>
                              {symbolData[0].sar}
                            </span>
                          </td>
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center whitespace-nowrap">
                            {/* 1.5倍放大：MACD圆圈 */}
                            <span className={`inline-flex items-center justify-center w-7 h-7 sm:w-9 sm:h-9 rounded-full text-sm sm:text-base font-bold ${getIndicatorStyle(symbolData[0].macd)}`}>
                              {symbolData[0].macd}
                            </span>
                          </td>
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center whitespace-nowrap">
                            {/* 1.5倍放大：KDJ圆圈 */}
                            <span className={`inline-flex items-center justify-center w-7 h-7 sm:w-9 sm:h-9 rounded-full text-sm sm:text-base font-bold ${getIndicatorStyle(symbolData[0].kdj)}`}>
                              {symbolData[0].kdj}
                            </span>
                          </td>
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center">
                            <span className={`px-2 py-1 sm:px-3 sm:py-1.5 rounded-full text-xs sm:text-sm font-medium inline-block text-center ${getIndicatorStyle(symbolData[0].kdjStatus)} whitespace-pre-wrap`}>
                              {symbolData[0].kdjStatus}
                            </span>
                          </td>
                          <td 
                            rowSpan={symbolData.length}
                            className={`px-1 py-2 sm:px-2 sm:py-3 text-center align-middle ${
                              calculateSignal(symbolData[0], symbolData[1]) === 'Warn' 
                                ? 'animate-cell-pulse-yellow' 
                                : calculateSignal(symbolData[0], symbolData[1]) === 'Down' 
                                ? 'animate-cell-pulse-red'
                                : calculateSignal(symbolData[0], symbolData[1]) === 'Up' 
                                ? 'animate-cell-pulse-green'
                                : ''
                            }`}
                          >
                            <div className="px-2 py-1 sm:px-4 sm:py-2 rounded-lg text-sm sm:text-base font-semibold text-center truncate">
                              {calculateSignal(symbolData[0], symbolData[1])}
                            </div>
                          </td>
                        </tr>
                      )}
                      
                      {/* 第二行：4h数据 */}
                      {symbolData.length >= 2 && (
                        <tr className="bg-gray-50 dark:bg-gray-900">
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center whitespace-nowrap">
                            <span className="inline-flex items-center justify-center px-2 py-1 sm:px-3 sm:py-1.5 rounded-full text-xs sm:text-sm font-medium bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300">
                              {symbolData[1].timeframe}
                            </span>
                          </td>
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center whitespace-nowrap">
                            <span className={`inline-flex items-center justify-center w-7 h-7 sm:w-9 sm:h-9 rounded-full text-sm sm:text-base font-bold ${getIndicatorStyle(symbolData[1].sar)}`}>
                              {symbolData[1].sar}
                            </span>
                          </td>
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center whitespace-nowrap">
                            <span className={`inline-flex items-center justify-center w-7 h-7 sm:w-9 sm:h-9 rounded-full text-sm sm:text-base font-bold ${getIndicatorStyle(symbolData[1].macd)}`}>
                              {symbolData[1].macd}
                            </span>
                          </td>
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center whitespace-nowrap">
                            <span className={`inline-flex items-center justify-center w-7 h-7 sm:w-9 sm:h-9 rounded-full text-sm sm:text-base font-bold ${getIndicatorStyle(symbolData[1].kdj)}`}>
                              {symbolData[1].kdj}
                            </span>
                          </td>
                          <td className="px-1 py-2 sm:px-2 sm:py-3 text-center">
                            <span className={`px-2 py-1 sm:px-3 sm:py-1.5 rounded-full text-xs sm:text-sm font-medium inline-block text-center ${getIndicatorStyle(symbolData[1].kdjStatus)} whitespace-pre-wrap`}>
                              {symbolData[1].kdjStatus}
                            </span>
                          </td>
                        </tr>
                      )}
                    </tbody>
                   </table>
                </div>
              </div>
            ))}
          </div>

          {/* 加载状态 */}
          {loading && data.length === 0 && (
            <div className="fixed inset-0 bg-white bg-opacity-90 z-50 flex items-center justify-center">
              <div className="text-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4 mx-auto"></div>
                <p className="text-gray-600">Loading...</p>
              </div>
            </div>
          )}
        </div>
      </div>
      <MobileView>
        <div className="h-[80px] bg-white"></div>
      </MobileView>
      {/* 内容 */}
      <CryptoExtractor onCallback={onAddCrypto} />
    </>
  );
}