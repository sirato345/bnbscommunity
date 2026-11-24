"""Finance chart calculations. All input and output series are oldest first."""

import math
from dataclasses import dataclass
from datetime import date, timedelta
from typing import Optional, Sequence


@dataclass(frozen=True)
class Candle:
    start: int
    open: float
    high: float
    low: float
    close: float


def _validate_candles(candles: Sequence[Candle]) -> None:
    for i, candle in enumerate(candles):
        if i and candle.start <= candles[i - 1].start:
            raise ValueError("candles must have strictly increasing timestamps")
        if (not all(math.isfinite(value) for value in (candle.open, candle.high, candle.low, candle.close))
                or candle.low > min(candle.open, candle.close)
                or candle.high < max(candle.open, candle.close)
                or candle.low > candle.high):
            raise ValueError(f"invalid OHLC values at index {i}")


def ema(values: Sequence[Optional[float]], period: int) -> list[Optional[float]]:
    if period <= 0:
        raise ValueError("period must be positive")
    result: list[Optional[float]] = [None] * len(values)
    seed: list[float] = []
    previous: Optional[float] = None
    started = False
    for i, value in enumerate(values):
        if value is None:
            if started:
                raise ValueError("EMA inputs cannot contain gaps after the first value")
            continue
        started = True
        if not math.isfinite(value):
            raise ValueError("EMA inputs must be finite or None")
        if previous is None:
            seed.append(value)
            if len(seed) < period:
                continue
            previous = sum(seed) / period
        else:
            previous = (2 * value + (period - 1) * previous) / (period + 1)
        result[i] = previous
    return result


def macd(candles: Sequence[Candle]) -> tuple[list[Optional[float]], list[Optional[float]], list[Optional[float]]]:
    _validate_candles(candles)
    closes = [c.close for c in candles]
    fast, slow = ema(closes, 12), ema(closes, 26)
    dif = [a - b if a is not None and b is not None else None for a, b in zip(fast, slow)]
    signal = ema(dif, 9)
    histogram = [a - b if a is not None and b is not None else None for a, b in zip(dif, signal)]
    return dif, signal, histogram


def kdj_full(candles: Sequence[Candle]) -> tuple[list[Optional[float]], list[Optional[float]], list[Optional[float]]]:
    _validate_candles(candles)
    ks: list[Optional[float]] = [None] * len(candles)
    ds: list[Optional[float]] = [None] * len(candles)
    js: list[Optional[float]] = [None] * len(candles)
    k = d = 50.0
    for i in range(8, len(candles)):
        window = candles[i - 8:i + 1]
        high, low = max(c.high for c in window), min(c.low for c in window)
        rsv = 50.0 if high == low else (candles[i].close - low) / (high - low) * 100
        k = (2 * k + rsv) / 3
        d = (2 * d + k) / 3
        ks[i], ds[i], js[i] = k, d, 3 * k - 2 * d
    return ks, ds, js


def kdj(candles: Sequence[Candle]) -> tuple[list[Optional[float]], list[Optional[float]]]:
    k, d, _ = kdj_full(candles)
    return k, d


def sar(candles: Sequence[Candle]) -> list[Optional[float]]:
    _validate_candles(candles)
    values: list[Optional[float]] = [None] * len(candles)
    if len(candles) < 2:
        return values
    first, second = candles[:2]
    up = second.close >= first.close
    current = min(first.low, second.low) if up else max(first.high, second.high)
    extreme = max(first.high, second.high) if up else min(first.low, second.low)
    acceleration = 0.02
    values[0] = values[1] = current
    for i in range(2, len(candles)):
        candle = candles[i]
        next_sar = current + acceleration * (extreme - current)
        if up:
            next_sar = min(next_sar, candles[i - 1].low, candles[i - 2].low)
            if candle.low < next_sar:
                up, next_sar, extreme, acceleration = False, extreme, candle.low, 0.02
            elif candle.high > extreme:
                extreme = candle.high
                acceleration = min(acceleration + 0.02, 0.2)
        else:
            next_sar = max(next_sar, candles[i - 1].high, candles[i - 2].high)
            if candle.high > next_sar:
                up, next_sar, extreme, acceleration = True, extreme, candle.high, 0.02
            elif candle.low < extreme:
                extreme = candle.low
                acceleration = min(acceleration + 0.02, 0.2)
        current = next_sar
        values[i] = current
    return values


def moving_average(candles: Sequence[Candle], index: int, period: int) -> float:
    if period <= 0 or index < period - 1 or index >= len(candles):
        raise ValueError("invalid moving-average period or index")
    closes = [c.close for c in candles[index - period + 1:index + 1]]
    if not all(math.isfinite(value) for value in closes):
        raise ValueError("moving-average inputs must be finite")
    return sum(closes) / period


def moving_averages(candles: Sequence[Candle], period: int) -> list[Optional[float]]:
    if period <= 0:
        raise ValueError("period must be positive")
    _validate_candles(candles)
    # StickView omits the oldest otherwise-complete window.
    return [moving_average(candles, i, period) if i >= period else None
            for i in range(len(candles))]


@dataclass(frozen=True)
class BollingerBands:
    middle: float
    upper: float
    lower: float


def bollinger_bands(closes: Sequence[float]) -> list[Optional[BollingerBands]]:
    if not all(math.isfinite(value) for value in closes):
        raise ValueError("Bollinger inputs must be finite")
    result: list[Optional[BollingerBands]] = [None] * len(closes)
    for i in range(19, len(closes)):
        window = closes[i - 19:i + 1]
        middle = sum(window) / 20
        deviation = math.sqrt(sum((value - middle) ** 2 for value in window) / 20)
        result[i] = BollingerBands(middle, middle + 2 * deviation, middle - 2 * deviation)
    return result


def td_sequential(closes: Sequence[float]) -> list[int]:
    if not all(math.isfinite(value) for value in closes):
        raise ValueError("TD inputs must be finite")
    result = [0] * len(closes)
    for i in range(len(closes)):
        direction = count = 0
        for j in range(i, max(i - 9, 3), -1):
            comparison = (closes[j] > closes[j - 4]) - (closes[j] < closes[j - 4])
            if comparison == 0 or (direction != 0 and comparison != direction):
                break
            direction = comparison
            count += 1
        if count >= 8:
            result[i] = direction * count
    return result


def average_candles(candles: Sequence[Candle]) -> list[Optional[Candle]]:
    """Finance's four-candle smoothing and non-recursive average candles."""
    _validate_candles(candles)
    result: list[Optional[Candle]] = [None] * len(candles)
    smoothed: list[Candle] = []
    for i in range(4, len(candles)):
        window = candles[i - 3:i + 1]
        smoothed.append(Candle(candles[i].start, sum(c.open for c in window) / 4,
                               sum(c.high for c in window) / 4,
                               sum(c.low for c in window) / 4,
                               sum(c.close for c in window) / 4))
    for offset, candle in enumerate(smoothed):
        if offset == 0:
            result[4] = candle
            continue
        previous = smoothed[offset - 1]
        opening = (previous.open + previous.close) / 2
        closing = (candle.open + candle.high + candle.low + candle.close) / 4
        result[offset + 4] = Candle(candle.start, opening, max(candle.high, opening, closing),
                                    min(candle.low, opening, closing), closing)
    return result


def direction_light(current_j: float, previous_j: float, current_dif: float,
                    previous_dif: float, inverted: bool = False) -> str:
    if not all(math.isfinite(v) for v in (current_j, previous_j, current_dif, previous_dif)):
        raise ValueError("light inputs must be finite")
    j_delta, dif_delta = current_j - previous_j, current_dif - previous_dif
    if inverted:
        j_delta, dif_delta = -j_delta, -dif_delta
    if j_delta > 0 and dif_delta > 0:
        return "GREEN"
    if j_delta < 0 and dif_delta < 0:
        return "RED"
    return "YELLOW"


def position_light(k: float, d: float, dif: float, signal: float, inverted: bool = False) -> str:
    if not all(math.isfinite(v) for v in (k, d, dif, signal)):
        raise ValueError("light inputs must be finite")
    bearish = (int(k > d) + int(dif > signal)) if inverted else (int(k < d) + int(dif < signal))
    return ("GREEN", "YELLOW", "RED")[bearish]


@dataclass(frozen=True)
class Divergence:
    kind: str
    pivot_index: int
    comparison_index: int
    confirmed_index: int
    cross_index: Optional[int]
    cross_confirmed_index: Optional[int]

    def kind_at(self, index: int) -> Optional[str]:
        if index < self.confirmed_index:
            return None
        if self.cross_confirmed_index is not None and index >= self.cross_confirmed_index:
            return self.kind
        return self.kind.split("_", 1)[0]


def macd_divergences(candles: Sequence[Candle], dif: Sequence[Optional[float]],
                     signal: Sequence[Optional[float]], *, visible_count: Optional[int] = None,
                     inverted: bool = False) -> list[Divergence]:
    """Return chart divergences with the first index at which each result was knowable."""
    size = len(candles)
    if len(dif) != size or len(signal) != size:
        raise ValueError("candles, DIF and signal must have the same length")
    _validate_candles(candles)
    if any(value is not None and not math.isfinite(value) for value in (*dif, *signal)):
        raise ValueError("DIF and signal must be finite or None")
    if visible_count is None:
        visible_count = size
    if visible_count < 0 or visible_count > size:
        raise ValueError("visible_count outside series")
    valid = [i for i in range(size) if dif[i] is not None and signal[i] is not None]
    if not valid:
        return []
    first = max(size - visible_count, valid[0])
    if any(dif[i] is None or signal[i] is None for i in range(first, size)):
        raise ValueError("DIF and signal must be continuous after warmup")
    result: list[Divergence] = []
    previous_high: Optional[int] = None
    previous_low: Optional[int] = None
    for i in range(first + 3, size - 3):
        value = dif[i]
        assert value is not None
        nearby = [dif[i + offset] for offset in (-3, -2, -1, 1, 2, 3)]
        if any(other is None for other in nearby):
            raise ValueError("DIF is unavailable in pivot window")
        is_high = value > 0 and all(other is not None and value > other for other in nearby)
        is_low = value < 0 and all(other is not None and value < other for other in nearby)
        previous = previous_high if is_high else previous_low if is_low else None
        if previous is not None and i - previous >= 10:
            old = dif[previous]
            assert old is not None
            old_price = candles[previous].high if is_high else candles[previous].low
            price = candles[i].high if is_high else candles[i].low
            inverse = (old < value and old_price > price) or (old > value and old_price < price)
            if inverse and abs(old) > abs(value):
                cross_index = None
                for j in range(i + 1, size):
                    current, current_signal, prior, prior_signal = dif[j], signal[j], dif[j - 1], signal[j - 1]
                    assert current is not None and current_signal is not None
                    assert prior is not None and prior_signal is not None
                    if ((is_high and current < current_signal and prior >= prior_signal)
                            or (is_low and current > current_signal and prior <= prior_signal)):
                        cross_index = j
                        break
                kind = "TOP" if is_high else "BOTTOM"
                if inverted:
                    kind = "BOTTOM" if is_high else "TOP"
                if cross_index is not None:
                    kind += "_DEATH_CROSS" if kind == "TOP" else "_GOLDEN_CROSS"
                result.append(Divergence(kind, i, previous, i + 3, cross_index,
                                         max(i + 3, cross_index) if cross_index is not None else None))
        if is_high:
            previous_high = i
        if is_low:
            previous_low = i
    return result


def fear_greed_period_key(day: date, timeframe: str) -> str:
    if timeframe == "d":
        return day.strftime("%Y%m%d")
    if timeframe == "3d":
        return str((day - date(1970, 1, 1)).days // 3)
    if timeframe == "w":
        return (day - timedelta(days=(day.weekday() + 1) % 7)).strftime("%Y%m%d")
    if timeframe == "m":
        return f"{day.year}-{day.month}"
    if timeframe == "q":
        return f"{day.year}-{(day.month - 1) // 3}"
    if timeframe == "y":
        return str(day.year)
    raise ValueError(f"Unsupported timeframe: {timeframe}")


def fear_greed_latest_by_period(records: Sequence[tuple[date, float]], timeframe: str) -> dict[str, float]:
    fear_greed_period_key(date(1970, 1, 1), timeframe)
    latest: dict[str, tuple[date, float]] = {}
    for day, score in records:
        if not math.isfinite(score) or not 0 <= score <= 100:
            raise ValueError("fear/greed score must be finite and between 0 and 100")
        key = fear_greed_period_key(day, timeframe)
        if key not in latest or day > latest[key][0]:
            latest[key] = (day, score)
    return {key: value[1] for key, value in latest.items()}
