import { useEffect, useState } from 'react';
import { collection, onSnapshot, QuerySnapshot, DocumentData } from 'firebase/firestore';
import { db } from '../../firebase';

export interface CurrentTradeData {
  id: string;
  SYMBOL: string;
  OPEN_DATE: string;
  OPEN_PRICE: number;
  OPEN_5M_SAR: string | number;
  OPEN_5M_MACD: string | number;
  OPEN_5M_KDJ: string | number;
  OPEN_15M_SAR: string | number;
  OPEN_15M_MACD: string | number;
  OPEN_15M_KDJ: string | number;
  OPEN_1H_SAR: string | number;
  OPEN_1H_MACD: string | number;
  OPEN_1H_KDJ: string | number;
  OPEN_4H_SAR: string | number;
  OPEN_4H_MACD: string | number;
  OPEN_4H_KDJ: string | number;
  updated_at?: any;
}

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
    const tradesRef = collection(db, 'CURRENT_TRADE');

    const unsubscribe = onSnapshot(
      tradesRef,
      (snapshot: QuerySnapshot<DocumentData>) => {
        const tradesData: CurrentTradeData[] = [];
        snapshot.forEach((doc) => {
          const data = doc.data();
          tradesData.push({
            id:            doc.id,
            SYMBOL:        data.SYMBOL,
            OPEN_DATE:     data.OPEN_DATE,
            OPEN_PRICE:    data.OPEN_PRICE,
            OPEN_5M_SAR:   data.OPEN_5M_SAR   ?? '—',
            OPEN_5M_MACD:  data.OPEN_5M_MACD  ?? '—',
            OPEN_5M_KDJ:   data.OPEN_5M_KDJ   ?? '—',
            OPEN_15M_SAR:  data.OPEN_15M_SAR  ?? '—',
            OPEN_15M_MACD: data.OPEN_15M_MACD ?? '—',
            OPEN_15M_KDJ:  data.OPEN_15M_KDJ  ?? '—',
            OPEN_1H_SAR:   data.OPEN_1H_SAR   ?? '—',
            OPEN_1H_MACD:  data.OPEN_1H_MACD  ?? '—',
            OPEN_1H_KDJ:   data.OPEN_1H_KDJ   ?? '—',
            OPEN_4H_SAR:   data.OPEN_4H_SAR   ?? '—',
            OPEN_4H_MACD:  data.OPEN_4H_MACD  ?? '—',
            OPEN_4H_KDJ:   data.OPEN_4H_KDJ   ?? '—',
            updated_at:    data.updated_at,
          });
        });
        setCurrentTrades(tradesData);
        setLoading(false);
      },
      (err: Error) => {
        console.error('読取当前持仓失敗:', err);
        setError(err);
        setLoading(false);
      }
    );

    return () => unsubscribe();
  }, []);

  return { currentTrades, loading, error };
}