import { useEffect, useState } from 'react';
import { collection, onSnapshot, QuerySnapshot, DocumentData } from 'firebase/firestore';
import { db } from '../../firebase';

// 定义当前持仓的数据结构
export interface CurrentTradeData {
  id: string;
  SYMBOL: string;
  OPEN_DATE: string;
  OPEN_PRICE: number;
  OPEN_15M_KDJ: string;    // 新增
  OPEN_1H_SAR: string | number;
  OPEN_1H_MACD: string | number;
  OPEN_1H_KDJ: string | number;
  OPEN_4H_SAR: string | number;
  OPEN_4H_MACD: string | number;
  OPEN_4H_KDJ: string | number;
  updated_at?: any; // Firebase Timestamp
}

// 定义 Hook 的返回类型
interface UseCurrentTradesResult {
  currentTrades: CurrentTradeData[];
  loading: boolean;
  error: Error | null;
}

export function useCurrentTrades(): UseCurrentTradesResult {
  const [currentTrades, setCurrentTrades] = useState<CurrentTradeData[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    // 注意：请根据实际数据结构调整集合路径
    // 如果 CURRENT_TRADE 是一个集合：
    const tradesRef = collection(db, 'CURRENT_TRADE');
    
    // 如果 CURRENT_TRADE 在子集合中，使用：
    // const tradesRef = collection(db, 'trades', 'current', 'positions');
    
    const unsubscribe = onSnapshot(
      tradesRef,
      (snapshot: QuerySnapshot<DocumentData>) => {
        const tradesData: CurrentTradeData[] = [];
        snapshot.forEach((doc) => {
          const data = doc.data();
          tradesData.push({
            id: doc.id,
            SYMBOL: data.SYMBOL,
            OPEN_DATE: data.OPEN_DATE,
            OPEN_PRICE: data.OPEN_PRICE,
            OPEN_15M_KDJ: data.OPEN_15M_KDJ ?? '—',
            OPEN_1H_SAR: data.OPEN_1H_SAR ?? '—',
            OPEN_1H_MACD: data.OPEN_1H_MACD ?? '—',
            OPEN_1H_KDJ: data.OPEN_1H_KDJ ?? '—',
            OPEN_4H_SAR: data.OPEN_4H_SAR ?? '—',
            OPEN_4H_MACD: data.OPEN_4H_MACD ?? '—',
            OPEN_4H_KDJ: data.OPEN_4H_KDJ ?? '—',
            updated_at: data.updated_at,
          });
        });
        setCurrentTrades(tradesData);
        setLoading(false);
      },
      (err: Error) => {
        console.error('读取当前持仓失败:', err);
        setError(err);
        setLoading(false);
      }
    );

    // 清理函数：组件卸载时取消监听
    return () => unsubscribe();
  }, []); // 空依赖数组，仅在组件挂载时执行一次

  return { currentTrades, loading, error };
}