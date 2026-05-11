'use client';

import { useState, useRef, useCallback, useEffect } from 'react';
import { useCurrentTrades, CurrentTradeData } from './useCurrentTrades';
import { useTradeHistory, TradeHistoryData } from './useTradeHistory';
import './AITrading.css';

// ── Utilities ──────────────────────────────────────────────────
const toPct     = (v: number) => v * 100;
const fmtPct    = (v: number) => (v >= 0 ? '+' : '') + v.toFixed(3) + '%';
const cls       = (v: number) => v >= 0 ? 'positive' : 'negative';

// KPI 汇总显示（保留2位小数）
const fmtDollarKpi = (v: number): string => {
  return (v >= 0 ? '+' : '') + v.toFixed(2);
};

// 🔥 根据币种格式化价格
const formatPrice = (symbol: string, price: number): string => {
  const baseSymbol = symbol.toUpperCase();
  
  switch (baseSymbol) {
    case 'DOGE':
      return price.toFixed(5);  // 狗狗币保留5位
    case 'SHIB':
    case 'PEPE':
      return price.toFixed(8);  // SHIB、PEPE保留8位
    default:
      return price.toFixed(2);  // BTC、ETH、BNB等保留2位
  }
};

// 🔥 根据币种格式化盈亏金额（P&L）
const fmtDollar = (symbol: string, v: number): string => {
  const prefix = v >= 0 ? '+' : '';
  const baseSymbol = symbol.toUpperCase();
  
  switch (baseSymbol) {
    case 'DOGE':
      return prefix + v.toFixed(5);
    case 'SHIB':
    case 'PEPE':
      return prefix + v.toFixed(8);
    default:
      return prefix + v.toFixed(2);
  }
};

// 用于显示在表格中的价格（带$符号）
const fmtPrice = (symbol: string, price: number): string => {
  return `$${formatPrice(symbol, price)}`;
};

const fmtDate = (raw: string): string => {
  const d = new Date(raw);
  const yyyy = d.getFullYear();
  const mm   = d.getMonth() + 1;
  const dd   = d.getDate();
  const hh   = String(d.getHours()).padStart(2, '0');
  const min  = String(d.getMinutes()).padStart(2, '0');
  const ss   = String(d.getSeconds()).padStart(2, '0');
  return `${yyyy}/${mm}/${dd}  ${hh}:${min}:${ss}`;
};

const fmtHoldTime = (raw: string): string =>
  raw ? raw.replace(/h(\d)/g, 'h $1') : raw;

// ── Current Position Card ──────────────────────────────────────
function TradeCard({ trade }: { trade: CurrentTradeData }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="trade-card">
      <div className="trade-card-header">
        <span className="trade-card-symbol">{trade.SYMBOL}</span>
        <span className="trade-card-dot" title="Open position" />
      </div>
      <div className="trade-card-row">
        <span className="tc-label">Open Price</span>
        <span className="tc-value">{fmtPrice(trade.SYMBOL, trade.OPEN_PRICE)}</span>
      </div>
      <div className="trade-card-row">
        <span className="tc-label">Open Date</span>
        <span className="tc-value">{fmtDate(trade.OPEN_DATE)}</span>
      </div>

      <button className="indicators-toggle" onClick={() => setOpen(v => !v)}>
        {open ? '▲ Hide Indicators' : '▼ Show Indicators'}
      </button>

      {open && (
        <div className="indicators-panel">
          {[
            { label: '15M', macd: trade.OPEN_15M_MACD, kdj: trade.OPEN_15M_KDJ},
            { label: '1H', sar: trade.OPEN_1H_SAR, macd: trade.OPEN_1H_MACD, kdj: trade.OPEN_1H_KDJ},
            { label: '4H', sar: trade.OPEN_4H_SAR, macd: trade.OPEN_4H_MACD, kdj: trade.OPEN_4H_KDJ },
          ].map(g => (
            <div key={g.label} className="ind-group">
              <div className="ind-group-title">{g.label}</div>
              {g.kdj !== undefined && (
                <div className="ind-row"><span>KDJ</span><span>{g.kdj}</span></div>
              )}
              {g.macd !== undefined && (
                <div className="ind-row"><span>MACD</span><span>{g.macd}</span></div>
              )}
              {g.sar !== undefined && (
                <div className="ind-row"><span>SAR</span><span>{g.sar}</span></div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Simplified Trading Signal Component ────────────────────────
function TradingSignals() {
  const [signals, setSignals] = useState<Record<string, string>>({});
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

  const fetchSignals = useCallback(async () => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000);

    try {
      const response = await fetch(
        'https://bnbs-django-275599637949.asia-northeast1.run.app/trading_signals',
        { signal: controller.signal, headers: { 'Content-Type': 'application/json' } }
      );
      clearTimeout(timeoutId);

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();

      if (!mountedRef.current) return;

      const parsed = data.signals || data;
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        setSignals(parsed);
        setError(null);
        setIsInitialized(true);
      } else {
        throw new Error('Unexpected response format');
      }
    } catch (err) {
      clearTimeout(timeoutId);
      if (!mountedRef.current) return;

      const msg = err instanceof Error ? err.message : '請求失敗';
      console.error('Failed to fetch signals:', msg);
      setError(msg);
    } finally {
      if (mountedRef.current) setLastUpdate(new Date());
    }
  }, []);

  useEffect(() => {
    fetchSignals();
    const interval = setInterval(fetchSignals, 30000);
    return () => clearInterval(interval);
  }, [fetchSignals]);

  useEffect(() => {
    if (isInitialized) return;
    const retryInterval = setInterval(() => {
      if (!isInitialized) fetchSignals();
    }, 5000);
    return () => clearInterval(retryInterval);
  }, [isInitialized, fetchSignals]);

  const coinOrder = ['BTC', 'ETH', 'BNB', 'DOGE'];

  const formatTime = (date: Date) => {
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    const ss = String(date.getSeconds()).padStart(2, '0');
    return `${hh}:${mm}:${ss}`;
  };

  const getSignalDisplay = (coin: string) => {
    if (!isInitialized) return '--';
    return signals[coin] || '--';
  };

  const getSignalType = (coin: string): 'buy' | 'sell' | 'none' => {
    if (!isInitialized) return 'none';
    const signal = signals[coin]?.toLowerCase();
    if (signal === 'buy') return 'buy';
    if (signal === 'sell') return 'sell';
    return 'none';
  };

  return (
    <div className="signal-row">
      {coinOrder.map(coin => {
        const signalType = getSignalType(coin);
        const displayValue = getSignalDisplay(coin);
        const isBuy  = signalType === 'buy';
        const isSell = signalType === 'sell';

        return (
          <div
            key={coin}
            className={`signal-item ${isBuy ? 'signal-buy' : ''} ${isSell ? 'signal-sell' : ''}`}
          >
            <span className="signal-coin">{coin}</span>
            {isBuy  && <span className="signal-arrow-up">▲</span>}
            {isSell && <span className="signal-arrow-down">▼</span>}
            {!isBuy && !isSell && <span className="signal-unknown">{displayValue}</span>}
          </div>
        );
      })}
      <div className="signal-update-time">
        {!isInitialized
          ? <span className="signal-connecting">Connecting...</span>
          : `Last Update: ${lastUpdate ? formatTime(lastUpdate) : '--:--:--'}`
        }
        {error && isInitialized && (
          <span className="signal-error-indicator" title={error}> ⚠️</span>
        )}
      </div>
    </div>
  );
}

// ── Main Page ──────────────────────────────────────────────────
export default function AiTradingPage() {
  const { currentTrades, loading: cl, error: ce } = useCurrentTrades();
  const { history,       loading: hl, error: he } = useTradeHistory({ maxRecords: 50 });

  const tableScrollRef  = useRef<HTMLDivElement>(null);
  const pageBodyRef     = useRef<HTMLDivElement>(null);
  const tableTouchLastY = useRef(0);

  const handleTableWheel = useCallback((e: React.WheelEvent<HTMLDivElement>) => {
    const el   = tableScrollRef.current;
    const page = pageBodyRef.current;
    if (!el || !page) return;
    const atTop    = el.scrollTop === 0;
    const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1;
    if ((atTop && e.deltaY < 0) || (atBottom && e.deltaY > 0)) {
      e.preventDefault();
      page.scrollBy({ top: e.deltaY, behavior: 'auto' });
    }
  }, []);

  const handleTableTouchStart = useCallback((e: React.TouchEvent<HTMLDivElement>) => {
    tableTouchLastY.current = e.touches[0].clientY;
  }, []);

  const handleTableTouchMove = useCallback((e: React.TouchEvent<HTMLDivElement>) => {
    const el   = tableScrollRef.current;
    const page = pageBodyRef.current;
    if (!el || !page) return;

    const currentY = e.touches[0].clientY;
    const deltaY = tableTouchLastY.current - currentY;
    tableTouchLastY.current = currentY;

    const atTop    = el.scrollTop <= 0;
    const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1;

    if ((atTop && deltaY < 0) || (atBottom && deltaY > 0)) {
      page.scrollBy({ top: deltaY, behavior: 'auto' });
    }
  }, []);

  if (cl || hl) {
    return (
      <div className="fixed inset-0 z-40 bg-white flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4 mx-auto"></div>
          <p className="text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }
  if (ce || he) {
    return (
      <div className="error-screen">
        <p>Error: {ce?.message || he?.message}</p>
      </div>
    );
  }

  const pctValues  = history.map(t => toPct(t.PROFIT_OR_LOSS_PERCENT || 0));
  const totalPct   = pctValues.reduce((s, v) => s + v, 0);
  const winTrades  = pctValues.filter(v => v > 0).length;
  const winRate    = history.length > 0 ? (winTrades / history.length * 100) : 0;
  const maxWinPct  = pctValues.length > 0 ? Math.max(...pctValues) : 0;
  const maxLossPct = pctValues.length > 0 ? Math.min(...pctValues) : 0;

  return (
    <div className="trade-dashboard-wrapper">
      <div
        className="trade-dashboard-body"
        ref={pageBodyRef}
      >
        <div className="trade-dashboard">

          <div className="page-header">
            <h1>AI Trading</h1>
            <span className="badge">LIVE</span>
          </div>

          <TradingSignals />

          <div className="kpi-strip">
            <div className="kpi-card kpi-card--pnl">
              <div className="kpi-label">Total P&amp;L</div>
              <div className={`kpi-value ${cls(totalPct)}`}>{fmtDollarKpi(totalPct)}%</div>
              <div className="kpi-sub"></div>
            </div>
            <div className="kpi-card kpi-card--winrate">
              <div className="kpi-label">Win Rate</div>
              <div className="kpi-value neutral">{winRate.toFixed(1)}%</div>
              <div className="kpi-sub"></div>
            </div>
            <div className="kpi-card kpi-card--trades">
              <div className="kpi-label">Total Trades</div>
              <div className="kpi-value neutral">{history.length}</div>
              <div className="kpi-sub"></div>
            </div>
            <div className="kpi-card kpi-card--maxwin">
              <div className="kpi-label">Best Trade</div>
              <div className="kpi-value positive">{fmtPct(maxWinPct)}</div>
              <div className="kpi-sub"></div>
            </div>
            <div className="kpi-card kpi-card--maxloss">
              <div className="kpi-label">Worst Trade</div>
              <div className="kpi-value negative">{fmtPct(maxLossPct)}</div>
              <div className="kpi-sub"></div>
            </div>
          </div>

          <section className="section">
            <div className="section-header">
              <h2>Current Positions</h2>
              <span className="count-tag">{currentTrades.length}</span>
              <div className="section-line" />
            </div>
            {currentTrades.length === 0 ? (
              <div className="empty-state">Waiting for trade signals...</div>
            ) : (
              <div className="current-grid">
                {currentTrades.map((t: CurrentTradeData) => (
                  <TradeCard key={t.id} trade={t} />
                ))}
              </div>
            )}
          </section>

          <section className="section">
            <div className="section-header">
              <h2>Trade History</h2>
              <span className="count-tag">{history.length}</span>
              <div className="section-line" />
            </div>
            {history.length === 0 ? (
              <div className="empty-state">No trade history</div>
            ) : (
              <div className="table-wrapper">
                <div
                  className="table-scroll"
                  ref={tableScrollRef}
                  onWheel={handleTableWheel}
                  onTouchStart={handleTableTouchStart}
                  onTouchMove={handleTableTouchMove}
                >
                  <table className="history-table">
                    <thead>
                      <tr>
                        <th>Symbol</th>
                        <th>Open Price</th>
                        <th>Close Price</th>
                        <th>P&amp;L ($)</th>
                        <th>P&amp;L (%)</th>
                        <th>Hold Time</th>
                        <th>Open Date</th>
                        <th>Close Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.map((t: TradeHistoryData) => {
                        const pl    = t.PROFIT_OR_LOSS || 0;
                        const plPct = toPct(t.PROFIT_OR_LOSS_PERCENT || 0);
                        return (
                          <tr key={t.id} className={pl >= 0 ? 'profit-row' : 'loss-row'}>
                            <td className="td-symbol">{t.SYMBOL}</td>
                            <td>{fmtPrice(t.SYMBOL, t.OPEN_PRICE)}</td>
                            <td>{fmtPrice(t.SYMBOL, t.CLOSE_PRICE)}</td>
                            <td>
                              <span className={`pl-chip ${cls(pl)}`}>{fmtDollar(t.SYMBOL, pl)}</span>
                            </td>
                            <td>
                              <span className={`pl-chip ${cls(plPct)}`}>{fmtPct(plPct)}</span>
                            </td>
                            <td className="td-date">{fmtHoldTime(t.HOLD_TIME)}</td>
                            <td className="td-date">{fmtDate(t.OPEN_DATE)}</td>
                            <td className="td-date">{fmtDate(t.CLOSE_DATE)}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </section>

        </div>
      </div>
    </div>
  );
}