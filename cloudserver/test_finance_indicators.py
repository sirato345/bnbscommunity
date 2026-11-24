import math
import unittest
from datetime import date

import finance_indicators as ind


def candle(index, close, high=None, low=None):
    return ind.Candle(index, close, close if high is None else high,
                      close if low is None else low, close)


class FinanceIndicatorsTests(unittest.TestCase):
    def test_ma_and_macd_warmup_and_seed(self):
        candles = [candle(i, float(i + 1)) for i in range(40)]
        ma = ind.moving_averages(candles, 10)
        self.assertIsNone(ma[9])  # finance's chart omits the oldest complete window
        self.assertEqual(ma[10], 6.5)
        dif, signal, histogram = ind.macd(candles)
        self.assertIsNone(dif[24])
        self.assertAlmostEqual(dif[25], 7)
        self.assertIsNone(signal[32])
        self.assertAlmostEqual(signal[33], 7)
        self.assertAlmostEqual(histogram[33], 0)
        with self.assertRaises(ValueError):
            ind.moving_average(candles, 2, 10)

    def test_kdj_and_sar_reversal(self):
        candles = [candle(i, 13, 15, 5) for i in range(9)]
        k, d, j = ind.kdj_full(candles)
        self.assertIsNone(k[7])
        self.assertAlmostEqual(k[8], 60)
        self.assertAlmostEqual(d[8], 160 / 3)
        self.assertAlmostEqual(j[8], 220 / 3)
        sar_candles = [candle(0, 10, 11, 9), candle(1, 11, 12, 10),
                       candle(2, 8, 10, 7), candle(3, 9, 10, 8)]
        self.assertEqual(ind.sar(sar_candles), [9, 9, 12, 12])

    def test_bollinger_and_td_java_examples(self):
        bands = ind.bollinger_bands(list(range(1, 22)))
        self.assertIsNone(bands[18])
        self.assertAlmostEqual(bands[19].middle, 10.5)
        self.assertAlmostEqual(bands[20].upper, 11.5 + 2 * math.sqrt(33.25))
        self.assertEqual(ind.td_sequential(list(range(13)))[-2:], [8, 9])
        self.assertEqual(ind.td_sequential(list(reversed(range(13))))[-2:], [-8, -9])
        self.assertEqual(ind.td_sequential([5.0] * 13)[-1], 0)
        with self.assertRaises(ValueError):
            ind.bollinger_bands([float("nan")])

    def test_finance_average_candles(self):
        candles = [ind.Candle(i, i, i + 2, i - 2, i + 1) for i in range(6)]
        actual = ind.average_candles(candles)
        self.assertEqual(actual[:4], [None] * 4)
        self.assertEqual(actual[4], ind.Candle(4, 2.5, 4.5, 0.5, 3.5))
        self.assertEqual(actual[5], ind.Candle(5, 3, 5.5, 1.5, 3.75))

    def test_lights_and_inversion(self):
        self.assertEqual(ind.direction_light(2, 1, 4, 3), "GREEN")
        self.assertEqual(ind.direction_light(2, 1, 4, 3, True), "RED")
        self.assertEqual(ind.direction_light(1, 1, 4, 3), "YELLOW")
        self.assertEqual(ind.position_light(1, 1, 3, 3), "GREEN")
        self.assertEqual(ind.position_light(2, 1, 4, 3, True), "RED")

    def test_divergence_confirmation_and_later_cross(self):
        candles = [candle(i, 20, 20, 15) for i in range(30)]
        candles[21] = candle(21, 20, 25, 15)
        dif = [0.0] * 30
        dif[9], dif[21], dif[27] = 8.0, 4.0, -1.0
        signal = [0.0] * 30
        self.assertEqual(ind.macd_divergences(candles[:24], dif[:24], signal[:24]), [])
        before_cross = ind.macd_divergences(candles[:25], dif[:25], signal[:25])[0]
        self.assertEqual((before_cross.kind, before_cross.confirmed_index, before_cross.cross_index),
                         ("TOP", 24, None))
        divergence = ind.macd_divergences(candles, dif, signal)[0]
        self.assertEqual((divergence.kind, divergence.comparison_index, divergence.pivot_index,
                          divergence.confirmed_index, divergence.cross_index,
                          divergence.cross_confirmed_index),
                         ("TOP_DEATH_CROSS", 9, 21, 24, 27, 27))
        self.assertEqual([divergence.kind_at(i) for i in (23, 24, 26, 27)],
                         [None, "TOP", "TOP", "TOP_DEATH_CROSS"])
        self.assertEqual(ind.macd_divergences(candles, dif, signal, inverted=True)[0].kind,
                         "BOTTOM_GOLDEN_CROSS")
        self.assertEqual(ind.macd_divergences(candles, dif, signal, visible_count=15), [])

    def test_bottom_divergence_cross_before_pivot_confirmation(self):
        candles = [candle(i, 20, 25, 20) for i in range(30)]
        candles[21] = candle(21, 20, 25, 15)
        dif = [0.0] * 30
        dif[9], dif[21], dif[22] = -8.0, -4.0, 1.0
        event = ind.macd_divergences(candles, dif, [0.0] * 30)[0]
        self.assertEqual((event.kind, event.cross_index, event.cross_confirmed_index),
                         ("BOTTOM_GOLDEN_CROSS", 22, 24))
        self.assertIsNone(event.kind_at(23))
        self.assertEqual(event.kind_at(24), "BOTTOM_GOLDEN_CROSS")

    def test_fear_greed_period_alignment(self):
        key = ind.fear_greed_period_key
        self.assertEqual(key(date(1970, 1, 3), "3d"), "0")
        self.assertEqual(key(date(1970, 1, 4), "3d"), "1")
        self.assertEqual(key(date(2026, 11, 21), "w"), "20261115")
        records = [(date(2026, 11, 17), 80), (date(2026, 11, 16), 20),
                   (date(2026, 11, 17), 12)]
        self.assertEqual(ind.fear_greed_latest_by_period(records, "w"), {"20261115": 80})
        with self.assertRaises(ValueError):
            ind.fear_greed_latest_by_period(records, "5m")

    def test_rejects_bad_inputs(self):
        with self.assertRaises(ValueError):
            ind.macd([candle(1, 10), candle(1, 10)])
        with self.assertRaises(ValueError):
            ind.ema([1.0, None, 2.0], 2)
        with self.assertRaises(ValueError):
            ind.fear_greed_latest_by_period([(date(2026, 1, 1), 101)], "d")


if __name__ == "__main__":
    unittest.main()
