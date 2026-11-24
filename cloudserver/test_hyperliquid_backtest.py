import unittest
from unittest.mock import patch

import hyperliquid_backtest as backtest


class IndicatorTests(unittest.TestCase):
    def test_sma_seeded_ema_and_kdj_flat_prices(self):
        self.assertEqual(backtest.ema([1.0, 2.0, 3.0, 4.0], 3), [None, None, 2.0, 3.0])
        candles = [backtest.Candle(i, 100, 100, 100, 100) for i in range(10)]
        k, d = backtest.kdj(candles)
        self.assertEqual((k[7], k[8], d[8]), (None, 50.0, 50.0))

    def test_only_completed_higher_timeframe_is_visible(self):
        starts = [0, 900_000, 1_800_000]
        self.assertEqual(backtest.last_closed(starts, 900_000, 1_799_999), 0)
        self.assertEqual(backtest.last_closed(starts, 900_000, 1_800_000), 1)


class CandleTests(unittest.TestCase):
    def test_missing_and_duplicate_candles_are_rejected(self):
        candle = {"t": 0, "T": 299_999, "s": "@151", "i": "5m",
                  "o": "100", "h": "101", "l": "99", "c": "100"}
        with patch.object(backtest, "api_request", return_value=[candle, candle]):
            with self.assertRaisesRegex(ValueError, "missing or duplicated"):
                backtest.fetch_candles("5m", 0, 600_000)


class StrategyTests(unittest.TestCase):
    def test_entry_outcomes_threshold_and_incomplete_windows(self):
        step = backtest.INTERVALS["5m"]
        signal_index = 60 * 48 + 2
        series = {}
        end = (signal_index + 100) * step
        for interval, duration in backtest.INTERVALS.items():
            series[interval] = [
                backtest.Candle(t, 100, 100, 100, 100)
                for t in range(0, end, duration)
            ]
        decision = (signal_index + 1) * step
        four_index = backtest.last_closed(
            [c.start for c in series["4h"]], backtest.INTERVALS["4h"], decision
        )
        current = series["4h"][four_index]
        series["4h"][four_index] = backtest.Candle(current.start, 100, 120, 100, 120)
        entry_index = signal_index + 1
        series["5m"][entry_index] = backtest.Candle(entry_index * step, 100, 102, 100, 100)
        end_4h = entry_index + 47
        series["5m"][end_4h] = backtest.Candle(end_4h * step, 100, 103, 100, 103)
        hist = [0.0] * len(series["5m"])
        hist[signal_index - 1], hist[signal_index] = -1.0, 1.0
        hour_dif = [1.0] * len(series["1h"])
        hour_signal = [0.0] * len(series["1h"])
        k = [0.0] * len(series["15m"])
        d = [1.0] * len(k)
        k_index = backtest.last_closed(
            [c.start for c in series["15m"]], backtest.INTERVALS["15m"], decision
        )
        k[k_index] = 2.0
        with patch.object(backtest, "macd", side_effect=[
            ([], [], hist), (hour_dif, hour_signal, [])
        ]), patch.object(backtest, "kdj", return_value=(k, d)), patch.object(
            backtest, "sar", return_value=[90.0] * len(series["4h"])
        ):
            signals, results, stages = backtest.backtest(series, decision)
        self.assertEqual(signals, [(decision, 100)])
        self.assertEqual(results[4], (1, 1, 1))
        self.assertEqual(results[8], (1, 1, 0))
        self.assertEqual(results[24], (0, 0, 0))
        self.assertEqual(stages["5m MACD"], 1)

        series["5m"][end_4h] = backtest.Candle(end_4h * step, 100, 102, 100, 102)
        with patch.object(backtest, "macd", side_effect=[
            ([], [], hist), (hour_dif, hour_signal, [])
        ]), patch.object(backtest, "kdj", return_value=(k, d)), patch.object(
            backtest, "sar", return_value=[90.0] * len(series["4h"])
        ):
            _, results, _ = backtest.backtest(series, decision)
        self.assertEqual(results[4], (1, 0, 0))


if __name__ == "__main__":
    unittest.main()
