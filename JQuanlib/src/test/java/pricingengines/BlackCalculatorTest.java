package pricingengines;

import jquant.Option;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code test-suite/blackcalculator.cpp}. Prices, cash-measure
 * ITM probability and Greeks are the published literals. Finite-difference
 * checks follow that file's bumps rather than a new expected value.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/blackcalculator.cpp
 */
public class BlackCalculatorTest {
    private static final double PRICE_TOLERANCE = 1.0e-8;
    private static final double GREEK_TOLERANCE = 1.0e-6;
    private static final double PARITY_TOLERANCE = 1.0e-10;
    private static final double EDGE_TOLERANCE = 1.0e-10;

    @Test
    public void testBlackCalculatorBasicValues() {
        // type sign, strike, forward, stdDev, discount, value
        double[][] values = {
                {1, 100.0, 100.0, 0.20, 1.0, 7.9655674554058038},
                {-1, 100.0, 100.0, 0.20, 1.0, 7.9655674554058038},
                {1, 90.0, 100.0, 0.20, 1.0, 13.589108116054803},
                {-1, 110.0, 100.0, 0.20, 1.0, 14.292010941409899},
                {1, 110.0, 100.0, 0.20, 1.0, 4.2920109414098846},
                {-1, 90.0, 100.0, 0.20, 1.0, 3.5891081160548062},
                {1, 100.0, 100.0, 0.0, 1.0, 0.0},
                {-1, 100.0, 100.0, 0.0, 1.0, 0.0}
        };
        for (double[] row : values) {
            Option.Type type = row[0] > 0 ? Option.Type.Call : Option.Type.Put;
            BlackCalculator fromType = new BlackCalculator(type, row[1], row[2], row[3], row[4]);
            BlackCalculator fromPayoff = new BlackCalculator(
                    new PlainVanillaPayoff(type, row[1]), row[2], row[3], row[4]);
            assertEquals(row[5], fromType.value(), PRICE_TOLERANCE, type + " K=" + row[1]);
            assertEquals(fromType.value(), fromPayoff.value(), PRICE_TOLERANCE);
            // alpha/beta substitution: discount * (F * alpha + K * beta).
            double substituted = row[4] * (row[2] * fromType.alpha() + row[1] * fromType.beta());
            assertEquals(row[5], substituted, PRICE_TOLERANCE);
            if (row[3] == 0.0) {
                double intrinsic = row[4] * Math.max(0.0,
                        type == Option.Type.Call ? row[2] - row[1] : row[1] - row[2]);
                assertEquals(intrinsic, fromType.value(), PRICE_TOLERANCE);
            }
            assertTrue(fromType.value() >= -PRICE_TOLERANCE);
        }
    }

    @Test
    public void testBlackCalculatorGreeks() {
        double forward = 100.0;
        double strike = 105.0;
        double stdDev = 0.20;
        double discount = 0.95;
        double spot = 98.0;
        double maturity = 1.0;
        BlackCalculator calc = new BlackCalculator(
                Option.Type.Call, strike, forward, stdDev, discount);

        assertEquals(0.42063116955653351, calc.deltaForward(), GREEK_TOLERANCE);
        assertEquals(0.42921547913932068, calc.delta(spot), GREEK_TOLERANCE);
        assertEquals(0.018754435012086908, calc.gammaForward(), GREEK_TOLERANCE);
        assertEquals(0.019527733248736884, calc.gamma(spot), GREEK_TOLERANCE);
        assertEquals(-4.31290436588883, calc.theta(spot, maturity), GREEK_TOLERANCE);
        assertEquals(37.508870024173795, calc.vega(maturity), GREEK_TOLERANCE);
        assertEquals(36.452803157675653, calc.rho(maturity), GREEK_TOLERANCE);
        assertEquals(7.4974624362037199, calc.elasticityForward(), GREEK_TOLERANCE);
        assertEquals(7.4974624362037323, calc.elasticity(spot), GREEK_TOLERANCE);
        // Cash measure is the monetary ITM probability, N(d2).
        assertEquals(0.36544163566592136, calc.itmCashProbability(), GREEK_TOLERANCE);
        assertEquals(0.44276965216477238, calc.itmAssetProbability(), GREEK_TOLERANCE);
        assertEquals(-42.063116955653371, calc.dividendRho(maturity), GREEK_TOLERANCE);
        assertEquals(-0.34716955388262527, calc.strikeSensitivity(), GREEK_TOLERANCE);
        assertEquals(0.017010825407788574, calc.strikeGamma(), GREEK_TOLERANCE);
        assertEquals(0.65822482825836837, calc.vanna(spot, maturity), GREEK_TOLERANCE);
        assertEquals(9.2856964243177753, calc.volga(maturity), GREEK_TOLERANCE);
    }

    @Test
    public void testBlackCalculatorPutCallParity() {
        double forward = 100.0;
        double strike = 105.0;
        double stdDev = 0.25;
        double discount = 0.95;
        double call = new BlackCalculator(Option.Type.Call, strike, forward, stdDev, discount).value();
        double put = new BlackCalculator(Option.Type.Put, strike, forward, stdDev, discount).value();
        assertEquals(discount * (forward - strike), call - put, PARITY_TOLERANCE);
    }

    @Test
    public void testBlackCalculatorEdgeCases() {
        assertEquals(0.0, new BlackCalculator(Option.Type.Call, 100.0, 100.0, 0.0, 1.0).value(),
                EDGE_TOLERANCE);
        assertEquals(68.268949213708595,
                new BlackCalculator(Option.Type.Call, 100.0, 100.0, 2.0, 1.0).value(),
                EDGE_TOLERANCE);
        double deepItm = new BlackCalculator(Option.Type.Call, 50.0, 100.0, 0.20, 1.0).value();
        assertTrue(deepItm >= 50.0 - EDGE_TOLERANCE);
        double deepOtm = new BlackCalculator(Option.Type.Call, 150.0, 100.0, 0.20, 1.0).value();
        assertTrue(deepOtm >= 0.0 && deepOtm <= 10.0);
    }

    @Test
    public void testBlackCalculatorNumericalDerivatives() {
        double forward = 100.0;
        double strike = 100.0;
        double stdDev = 0.20;
        double discount = 0.95;
        double maturity = 1.0;
        double bump = 1.0e-4;
        double tolerance = 1.0e-3;
        BlackCalculator calc = new BlackCalculator(Option.Type.Call, strike, forward, stdDev, discount);
        BlackCalculator up = new BlackCalculator(Option.Type.Call, strike, forward + bump, stdDev, discount);
        BlackCalculator down = new BlackCalculator(Option.Type.Call, strike, forward - bump, stdDev, discount);

        double numericalDelta = (up.value() - down.value()) / (2.0 * bump);
        assertEquals(calc.deltaForward(), numericalDelta, tolerance);

        double numericalGamma = (up.deltaForward() - down.deltaForward()) / (2.0 * bump);
        assertEquals(calc.gammaForward(), numericalGamma, tolerance);

        double vannaFd = (up.vega(maturity) - down.vega(maturity)) / (2.0 * bump);
        assertEquals(calc.vanna(forward, maturity), vannaFd, tolerance);

        BlackCalculator volUp = new BlackCalculator(Option.Type.Call, strike, forward, stdDev + bump, discount);
        BlackCalculator volDown = new BlackCalculator(Option.Type.Call, strike, forward, stdDev - bump, discount);
        double volgaFd = (volUp.vega(maturity) - volDown.vega(maturity)) / (2.0 * bump);
        assertEquals(calc.volga(maturity), volgaFd, tolerance);
    }

    @Test
    public void testBlackCalculatorZeroVolatilityGreeks() {
        double forward = 100.0;
        double discount = 1.0;
        double spot = 98.0;
        double maturity = 1.0;
        double tolerance = 1.0e-10;
        Option.Type[] types = {
                Option.Type.Call, Option.Type.Put, Option.Type.Call, Option.Type.Put,
                Option.Type.Call, Option.Type.Put
        };
        double[] strikes = {90.0, 110.0, 100.0, 100.0, 110.0, 90.0};
        for (int i = 0; i < types.length; i++) {
            BlackCalculator calc = new BlackCalculator(types[i], strikes[i], forward, 0.0, discount);
            assertTrue(Double.isFinite(calc.deltaForward()));
            assertTrue(Double.isFinite(calc.delta(spot)));
            assertTrue(Double.isFinite(calc.gammaForward()));
            assertTrue(Double.isFinite(calc.gamma(spot)));
            assertTrue(Double.isFinite(calc.vega(maturity)));
            assertTrue(Double.isFinite(calc.theta(spot, maturity)));
            assertTrue(Double.isFinite(calc.rho(maturity)));
            assertTrue(Double.isFinite(calc.dividendRho(maturity)));
            assertEquals(0.0, calc.gammaForward(), tolerance);
            assertEquals(0.0, calc.gamma(spot), tolerance);
            assertEquals(0.0, calc.vega(maturity), tolerance);
            assertEquals(0.0, calc.vanna(spot, maturity), tolerance);
            assertEquals(0.0, calc.volga(maturity), tolerance);
            assertTrue(Double.isFinite(calc.strikeSensitivity()));
            assertTrue(Double.isFinite(calc.strikeGamma()));
            assertEquals(0.0, calc.strikeGamma(), tolerance);
            if (strikes[i] < forward * 0.95 && types[i] == Option.Type.Call) {
                assertEquals(1.0, calc.deltaForward(), 0.01);
            }
            if (strikes[i] > forward * 1.05 && types[i] == Option.Type.Call) {
                assertEquals(0.0, calc.deltaForward(), tolerance);
            }
        }
        BlackCalculator tiny = new BlackCalculator(Option.Type.Call, 100.0, forward, 1.0e-12, discount);
        assertTrue(Double.isFinite(tiny.deltaForward()));
        assertTrue(Double.isFinite(tiny.gammaForward()));
        assertTrue(Double.isFinite(tiny.vega(maturity)));
        assertEquals(discount * 0.5, tiny.deltaForward(), 0.1);
    }
}
