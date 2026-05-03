import { useEffect, useState } from 'react';
import {
  collection,
  query,
  orderBy,
  limit,
  where,
  onSnapshot,
  QuerySnapshot,
  DocumentData,
  QueryConstraint,
} from 'firebase/firestore';
import { db } from '../../firebase';

export interface TradeHistoryData {
  id: string;
  SYMBOL: string;
  OPEN_DATE: string;
  OPEN_PRICE: number;
  OPEN_1H_SAR: string | number;
  OPEN_1H_MACD: string | number;
  OPEN_1H_KDJ: string | number;
  OPEN_4H_SAR: string | number;
  OPEN_4H_MACD: string | number;
  OPEN_4H_KDJ: string | number;
  CLOSE_DATE: string;
  CLOSE_PRICE: number;
  CLOSE_1H_SAR: string | number;
  CLOSE_1H_MACD: string | number;
  CLOSE_1H_KDJ: string | number;
  CLOSE_4H_SAR: string | number;
  CLOSE_4H_MACD: string | number;
  CLOSE_4H_KDJ: string | number;
  PROFIT_OR_LOSS: number;
  PROFIT_OR_LOSS_PERCENT: number;
  HOLD_TIME: string;
  created_at?: any;
}

interface UseTradeHistoryResult {
  history: TradeHistoryData[];
  loading: boolean;
  error: Error | null;
}

interface UseTradeHistoryOptions {
  maxRecords?: number;
  symbolFilter?: string;
}

export function useTradeHistory(options: UseTradeHistoryOptions = {}): UseTradeHistoryResult {
  const { maxRecords = 100, symbolFilter } = options;
  const [history, setHistory]   = useState<TradeHistoryData[]>([]);
  const [loading, setLoading]   = useState<boolean>(true);
  const [error, setError]       = useState<Error | null>(null);

  useEffect(() => {
    // ✅ 修正: symbolFilter の実装を正しい where() 句に変更
    const constraints: QueryConstraint[] = [
      orderBy('CLOSE_DATE', 'desc'),
      limit(maxRecords),
    ];
    if (symbolFilter) {
      constraints.push(where('SYMBOL', '==', symbolFilter));
    }

    const q = query(collection(db, 'TRADE_HISTORY'), ...constraints);

    const unsubscribe = onSnapshot(
      q,
      (snapshot: QuerySnapshot<DocumentData>) => {
        const data: TradeHistoryData[] = snapshot.docs.map(doc => {
          const d = doc.data();
          return {
            id:                   doc.id,
            SYMBOL:               d.SYMBOL,
            OPEN_DATE:            d.OPEN_DATE,
            OPEN_PRICE:           d.OPEN_PRICE,
            OPEN_1H_SAR:          d.OPEN_1H_SAR  ?? '—',
            OPEN_1H_MACD:         d.OPEN_1H_MACD ?? '—',
            OPEN_1H_KDJ:          d.OPEN_1H_KDJ  ?? '—',
            OPEN_4H_SAR:          d.OPEN_4H_SAR  ?? '—',
            OPEN_4H_MACD:         d.OPEN_4H_MACD ?? '—',
            OPEN_4H_KDJ:          d.OPEN_4H_KDJ  ?? '—',
            CLOSE_DATE:           d.CLOSE_DATE,
            CLOSE_PRICE:          d.CLOSE_PRICE,
            CLOSE_1H_SAR:         d.CLOSE_1H_SAR  ?? '—',
            CLOSE_1H_MACD:        d.CLOSE_1H_MACD ?? '—',
            CLOSE_1H_KDJ:         d.CLOSE_1H_KDJ  ?? '—',
            CLOSE_4H_SAR:         d.CLOSE_4H_SAR  ?? '—',
            CLOSE_4H_MACD:        d.CLOSE_4H_MACD ?? '—',
            CLOSE_4H_KDJ:         d.CLOSE_4H_KDJ  ?? '—',
            PROFIT_OR_LOSS:         d.PROFIT_OR_LOSS         ?? 0,
            PROFIT_OR_LOSS_PERCENT: d.PROFIT_OR_LOSS_PERCENT ?? 0,
            HOLD_TIME:            d.HOLD_TIME,
            created_at:           d.created_at,
          };
        });
        setHistory(data);
        setLoading(false);
      },
      (err: Error) => {
        console.error('取引履歴の取得に失敗:', err);
        setError(err);
        setLoading(false);
      }
    );

    return () => unsubscribe();
  }, [maxRecords, symbolFilter]);

  return { history, loading, error };
}