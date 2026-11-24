#!/usr/bin/env python3
"""Read-only Hyperliquid spot backtest for the finance trend-pullback hypothesis."""

import argparse
import bisect
import json
import math
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from typing import Optional, Sequence

from finance_indicators import Candle, kdj, macd, moving_average, sar
from finance_indicators import ema as ema


API_URL = "https://api.hyperliquid.xyz/info"
MARKET = "@151"
INTERVALS = {"5m": 300_000, "15m": 900_000, "1h": 3_600_000, "4h": 14_400_000}
WARMUP = 200
HORIZONS = (4, 8, 24)


def api_request(payload: dict) -> object:
    request = urllib.request.Request(
        API_URL, data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except (urllib.error.URLError, TimeoutError, ValueError) as exc:
        raise RuntimeError(f"Hyperliquid API request failed: {exc}") from exc


def verify_market() -> None:
    meta = api_request({"type": "spotMeta"})
    if not isinstance(meta, dict):
        raise ValueError("Unexpected spotMeta response")
    try:
        tokens = {token["index"]: token["name"] for token in meta["tokens"]}
        markets = [market for market in meta["universe"] if market["name"] == MARKET]
        if len(markets) != 1 or [tokens[i] for i in markets[0]["tokens"]] != ["UETH", "USDC"]:
            raise ValueError(f"{MARKET} is not the expected UETH/USDC market")
    except (KeyError, TypeError) as exc:
        raise ValueError("Invalid spotMeta response") from exc


def fetch_candles(interval: str, start: int, end: int) -> list[Candle]:
    """Fetch complete, consecutive candles for [start, end), in UTC milliseconds."""
    duration = INTERVALS[interval]
    start = start // duration * duration
    end = end // duration * duration
    candles: list[Candle] = []
    cursor = start
    while cursor < end:
        chunk_end = min(cursor + 500 * duration, end)
        raw = api_request({
            "type": "candleSnapshot",
            "req": {"coin": MARKET, "interval": interval, "startTime": cursor,
                    "endTime": chunk_end - 1},
        })
        if not isinstance(raw, list):
            raise ValueError(f"Unexpected {interval} candle response")
        for item in raw:
            try:
                candle = Candle(int(item["t"]), float(item["o"]), float(item["h"]),
                                float(item["l"]), float(item["c"]))
                if item["s"] != MARKET or item["i"] != interval:
                    raise ValueError("market or interval mismatch")
                if int(item["T"]) != candle.start + duration - 1:
                    raise ValueError("unexpected candle end time")
                prices = (candle.open, candle.high, candle.low, candle.close)
                if (not all(math.isfinite(price) and price > 0 for price in prices)
                        or candle.low > min(candle.open, candle.close)
                        or candle.high < max(candle.open, candle.close)
                        or candle.low > candle.high):
                    raise ValueError("invalid OHLC values")
                if not cursor <= candle.start < chunk_end:
                    raise ValueError("candle outside requested range")
                candles.append(candle)
            except (KeyError, TypeError, ValueError) as exc:
                raise ValueError(f"Invalid {interval} candle: {exc}") from exc
        cursor = chunk_end

    candles.sort(key=lambda candle: candle.start)
    expected_count = (end - start) // duration
    if len(candles) != expected_count or any(
        candle.start != start + index * duration for index, candle in enumerate(candles)
    ):
        raise ValueError(
            f"{interval} candles are missing or duplicated: expected {expected_count}, "
            f"received {len(candles)}. The API may not retain the requested history "
            "or the market may have gaps; shorten the range or use a complete data source."
        )
    return candles


def last_closed(starts: Sequence[int], duration: int, decision_time: int) -> int:
    return bisect.bisect_right(starts, decision_time - duration) - 1


def crossed_up(a_before: Optional[float], b_before: Optional[float],
               a: Optional[float], b: Optional[float]) -> bool:
    return (a_before is not None and b_before is not None and a is not None
            and b is not None and a_before <= b_before and a > b)


def backtest(series: dict[str, list[Candle]], start: int) -> tuple[list[tuple[int, float]], dict[int, tuple[int, int, int]], dict[str, int]]:
    five, fifteen, hour, four = (series[key] for key in ("5m", "15m", "1h", "4h"))
    starts = {key: [c.start for c in candles] for key, candles in series.items()}
    _, _, histogram = macd(five)
    hour_dif, hour_signal, _ = macd(hour)
    k, d = kdj(fifteen)
    four_sar = sar(four)
    signals: list[tuple[int, float]] = []
    outcomes = {hours: [0, 0, 0] for hours in HORIZONS}
    stages = {"evaluated": 0, "4h trend": 0, "1h MACD": 0, "15m KDJ": 0, "5m MACD": 0}

    for i in range(1, len(five) - 1):
        decision = five[i].start + INTERVALS["5m"]
        if decision < start:
            continue
        j = last_closed(starts["15m"], INTERVALS["15m"], decision)
        h = last_closed(starts["1h"], INTERVALS["1h"], decision)
        f = last_closed(starts["4h"], INTERVALS["4h"], decision)
        if j < 1 or h < 0 or f < 49:
            raise ValueError("Insufficient indicator warmup data")
        if (four_sar[f] is None or hour_dif[h] is None or hour_signal[h] is None
                or histogram[i - 1] is None or histogram[i] is None):
            raise ValueError("Indicators are unavailable after warmup")
        stages["evaluated"] += 1
        if not (four[f].close > moving_average(four, f, 20)
            and four[f].close > moving_average(four, f, 50)
            and four_sar[f] < four[f].close):
            continue
        stages["4h trend"] += 1
        if hour_dif[h] <= hour_signal[h]:
            continue
        stages["1h MACD"] += 1
        if not crossed_up(k[j - 1], d[j - 1], k[j], d[j]):
            continue
        stages["15m KDJ"] += 1
        if not histogram[i - 1] < 0 < histogram[i]:
            continue
        stages["5m MACD"] += 1
        entry = five[i + 1]
        signals.append((entry.start, entry.open))
        for hours in HORIZONS:
            count = hours * 12
            future = five[i + 1:i + 1 + count]
            if len(future) != count:
                continue
            outcome = outcomes[hours]
            outcome[0] += 1
            outcome[1] += int(max(c.high for c in future) > entry.open * 1.02)
            outcome[2] += int(future[-1].close > entry.open * 1.02)
    return signals, {hours: tuple(values) for hours, values in outcomes.items()}, stages


def utc_string(timestamp: int) -> str:
    return datetime.fromtimestamp(timestamp / 1000, tz=timezone.utc).strftime("%Y-%m-%d %H:%M UTC")


def parse_end(value: str) -> int:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("timezone required")
        return int(parsed.timestamp() * 1000)
    except (ValueError, OverflowError) as exc:
        raise argparse.ArgumentTypeError("Use an ISO timestamp with timezone, e.g. 2026-11-24T00:00:00Z") from exc


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--days", type=int, default=14, help="Lookback days (1-14; default: 14)")
    parser.add_argument("--end", type=parse_end, help="Exclusive end timestamp, ISO 8601 with timezone (default: now)")
    args = parser.parse_args(argv)
    if not 1 <= args.days <= 14:
        parser.error("--days must be between 1 and 14")
    now = int(datetime.now(timezone.utc).timestamp() * 1000)
    end = args.end if args.end is not None else now
    if end > now:
        parser.error("--end cannot be in the future")
    end = end // INTERVALS["5m"] * INTERVALS["5m"]
    start = end - args.days * 86_400_000
    try:
        verify_market()
        series = {
            interval: fetch_candles(interval, start - WARMUP * duration, end)
            for interval, duration in INTERVALS.items()
        }
        signals, outcomes, stages = backtest(series, start)
    except (RuntimeError, ValueError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1
    print(f"Market: UETH/USDC ({MARKET}), Hyperliquid mainnet spot; read-only")
    print(f"Signal period: {utc_string(start)} to {utc_string(end)} (exclusive)")
    print("Cumulative filter counts (5m decision points):")
    for label, count in stages.items():
        print(f"  {label}: {count}")
    print(f"Signals with next-bar entry available: {len(signals)}")
    print("Horizon | complete signals | high > 2% | final close > 2%")
    for hours in HORIZONS:
        eligible, high, close = outcomes[hours]
        high_text = f"{high}/{eligible} ({100 * high / eligible:.1f}%)" if eligible else "0/0 (N/A)"
        close_text = f"{close}/{eligible} ({100 * close / eligible:.1f}%)" if eligible else "0/0 (N/A)"
        print(f"{hours:>6}h | {eligible:>16} | {high_text} | {close_text}")
    print("Last 20 signals (entry time UTC, idealized next 5m open):")
    for timestamp, price in signals[-20:]:
        print(f"  {utc_string(timestamp)}  {price:g} USDC")
    print("Event statistics only; not realized returns. Fees, slippage and execution latency excluded.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
