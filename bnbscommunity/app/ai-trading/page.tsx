'use client';

import { useState, useRef, useCallback } from 'react';
import { useCurrentTrades, CurrentTradeData } from './useCurrentTrades';
import { useTradeHistory, TradeHistoryData } from './useTradeHistory';
import './AITrading.css';

// ── Utilities ──────────────────────────────────────────────────
const toPct     = (v: number) => v * 100;
const fmtPct    = (v: number) => (v >= 0 ? '+' : '') + v.toFixed(3) + '%';
const fmtDollar = (v: number) => (v >= 0 ? '+' : '') + v.toFixed(2);
const cls       = (v: number) => v >= 0 ? 'positive' : 'negative';

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
        <span className="tc-value">${Number(trade.OPEN_PRICE).toFixed(4)}</span>
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
            { label: '1H', sar: trade.OPEN_1H_SAR, macd: trade.OPEN_1H_MACD, kdj: trade.OPEN_1H_KDJ },
            { label: '4H', sar: trade.OPEN_4H_SAR, macd: trade.OPEN_4H_MACD, kdj: trade.OPEN_4H_KDJ },
          ].map(g => (
            <div key={g.label} className="ind-group">
              <div className="ind-group-title">{g.label}</div>
              <div className="ind-row"><span>SAR</span><span>{g.sar}</span></div>
              <div className="ind-row"><span>MACD</span><span>{g.macd}</span></div>
              <div className="ind-row"><span>KDJ</span><span>{g.kdj}</span></div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Main Page ──────────────────────────────────────────────────
export default function AiTradingPage() {
  const { currentTrades, loading: cl, error: ce } = useCurrentTrades();
  const { history,       loading: hl, error: he } = useTradeHistory({ maxRecords: 50 });

  // Scroll refs
  const tableScrollRef  = useRef<HTMLDivElement>(null);
  const pageBodyRef     = useRef<HTMLDivElement>(null);
  const tableTouchLastY = useRef(0);

  // ── Wheel hand-off (desktop): table → page body ─────────────
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

  // ── Touch hand-off (mobile): table → page body ──────────────
  const handleTableTouchStart = useCallback((e: React.TouchEvent<HTMLDivElement>) => {
    tableTouchLastY.current = e.touches[0].clientY;
  }, []);

  const handleTableTouchMove = useCallback((e: React.TouchEvent<HTMLDivElement>) => {
    const el   = tableScrollRef.current;
    const page = pageBodyRef.current;
    if (!el || !page) return;

    const currentY = e.touches[0].clientY;
    // deltaY > 0: finger moved down = scrolling up; < 0: finger moved up = scrolling down
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
      <div className="loading-screen">
        <div className="spinner" />
        <p>Loading data...</p>
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

  // ── Aggregation ─────────────────────────────────────────────
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

          {/* ── Page Header ── */}
          <div className="page-header">
            <h1>AI Trading</h1>
            <span className="badge">LIVE</span>
          </div>

          {/* ── KPI Cards ── */}
          <div className="kpi-strip">
            <div className="kpi-card kpi-card--pnl">
              <div className="kpi-label">Total P&amp;L</div>
              <div className={`kpi-value ${cls(totalPct)}`}>{fmtPct(totalPct)}</div>
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

          {/* ── Current Positions ── */}
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

          {/* ── Trade History ── */}
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
                            <td>${t.OPEN_PRICE.toFixed(4)}</td>
                            <td>${t.CLOSE_PRICE.toFixed(4)}</td>
                            <td>
                              <span className={`pl-chip ${cls(pl)}`}>{fmtDollar(pl)}</span>
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