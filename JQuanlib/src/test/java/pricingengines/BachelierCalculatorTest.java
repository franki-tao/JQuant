package pricingengines;

import jquant.Option;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.NormalDistribution;
import jquant.pricingengines.BachelierCalculator;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code test-suite/bacheliercalculator.cpp}. Prices and Greeks are
 * the published literals. Finite differences use that file's bumps.
 * Bachelier has one normal variable d = (F-K)/stdDev, not Black d1 and d2,
 * and the public header does not expose d.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/bacheliercalculator.cpp
 */
public class BachelierCalculatorTest {
    private static final double PRICE_TOLERANCE = 1.0e-8;
    private static final double GREEK_TOLERANCE = 1.0e-6;
    private static final double PARITY_TOLERANCE = 1.0e-10;

    @Test
    public void testBachelierCalculatorBasicValues() {
        double[][] values = {
                {1, 100.0, 100.0, 20.0, 1.0, 7.9788456080286538},
                {-1, 100.0, 100.0, 20.0, 1.0, 7.9788456080286538},
                {1, 90.0, 100.0, 20.0, 1.0, 13.955931148026121},
                {-1, 110.0, 100.0, 20.0, 1.0, 13.955931148026121},
                {1, 110.0, 100.0, 20.0, 1.0, 3.9559311480261217},
                {-1, 90.0, 100.0, 20.0, 1.0, 3.9559311480261217},
                {1, 100.0, 100.0, 0.0, 1.0, 0.0},
                {-1, 100.0, 100.0, 0.0, 1.0, 0.0},
                {1, 0.0, 100.0, 20.0, 1.0, 100.00000106923312}
        };
        for (double[] row : values) {
            Option.Type type = row[0] > 0 ? Option.Type.Call : Option.Type.Put;
            BachelierCalculator fromType = new BachelierCalculator(
                    type, row[1], row[2], row[3], row[4]);
            BachelierCalculator fromPayoff = new BachelierCalculator(
                    new PlainVanillaPayoff(type, row[1]), row[2], row[3], row[4]);
            assertEquals(row[5], fromType.value(), PRICE_TOLERANCE, type + " K=" + row[1]);
            assertEquals(fromType.value(), fromPayoff.value(), PRICE_TOLERANCE);
            if (row[3] == 0.0) {
                double intrinsic = row[4] * Math.max(0.0,
                        type == Option.Type.Call ? row[2] - row[1] : row[1] - row[2]);
                assertEquals(intrinsic, fromType.value(), PRICE_TOLERANCE);
            }
            assertTrue(fromType.value() >= -PRICE_TOLERANCE);
        }
    }

    @Test
    public void testBachelierCalculatorGreeks() {
        double forward = 100.0;
        double strike = 105.0;
        double stdDev = 20.0;
        double discount = 0.95;
        double spot = 98.0;
        double maturity = 1.0;
        BachelierCalculator calc = new BachelierCalculator(
                Option.Type.Call, strike, forward, stdDev, discount);
        assertEquals(0.38122899060122245, calc.deltaForward(), GREEK_TOLERANCE);
        assertEquals(0.38900917408288, calc.delta(spot), GREEK_TOLERANCE);
        assertEquals(0.018366735548135338, calc.gammaForward(), GREEK_TOLERANCE);
        assertEquals(0.019124047842706517, calc.gamma(spot), GREEK_TOLERANCE);
        assertEquals(-4.3159316452046594, calc.theta(spot, maturity), GREEK_TOLERANCE);
        assertEquals(0.36733471096270676, calc.vega(maturity), GREEK_TOLERANCE);
        assertEquals(32.682349793874224, calc.rho(maturity), GREEK_TOLERANCE);
        assertEquals(7.0071783554334051, calc.elasticityForward(), GREEK_TOLERANCE);
        assertEquals(7.0071783554334042, calc.elasticity(spot), GREEK_TOLERANCE);
        assertEquals(0.4012936743170763, calc.itmCashProbability(), GREEK_TOLERANCE);
        assertEquals(0.4012936743170763, calc.itmAssetProbability(), GREEK_TOLERANCE);
        assertEquals(-38.122899060122243, calc.dividendRho(maturity), GREEK_TOLERANCE);
        assertEquals(-0.38122899060122245, calc.strikeSensitivity(), GREEK_TOLERANCE);
        assertEquals(0.018366735548135338, calc.strikeGamma(), GREEK_TOLERANCE);
        assertEquals(0.0048333514600356151, calc.vanna(maturity), GREEK_TOLERANCE);
        assertEquals(0.0011479209717584586, calc.volga(maturity), GREEK_TOLERANCE);
    }

    @Test
    public void testBachelierCalculatorPutCallParity() {
        double forward = 100.0;
        double strike = 105.0;
        double stdDev = 25.0;
        double discount = 0.95;
        double call = new BachelierCalculator(Option.Type.Call, strike, forward, stdDev, discount).value();
        double put = new BachelierCalculator(Option.Type.Put, strike, forward, stdDev, discount).value();
        assertEquals(discount * (forward - strike), call - put, PARITY_TOLERANCE);
    }

    @Test
    public void testBachelierCalculatorEdgeCases() {
        assertEquals(0.0, new BachelierCalculator(Option.Type.Call, 100.0, 100.0, 0.0, 1.0).value(),
                PRICE_TOLERANCE);
        assertEquals(79.788456080286537,
                new BachelierCalculator(Option.Type.Call, 100.0, 100.0, 200.0, 1.0).value(),
                PRICE_TOLERANCE);
        double negativeStrike = new BachelierCalculator(Option.Type.Call, -50.0, 100.0, 20.0, 1.0).value();
        assertTrue(negativeStrike >= (100.0 - (-50.0)) - 10.0);
        double negativeForward = new BachelierCalculator(Option.Type.Call, 50.0, -100.0, 20.0, 1.0).value();
        assertTrue(negativeForward >= (-100.0 - 50.0) + 10.0);
        double deepItm = new BachelierCalculator(Option.Type.Call, 50.0, 100.0, 20.0, 1.0).value();
        assertTrue(deepItm >= 50.0 - PRICE_TOLERANCE);
        assertEquals(0.040082743582562863,
                new BachelierCalculator(Option.Type.Call, 150.0, 100.0, 20.0, 1.0).value(),
                PRICE_TOLERANCE);
    }

    @Test
    public void testBachelierCalculatorNumericalDerivatives() {
        double forward = 100.0;
        double strike = 100.0;
        double stdDev = 20.0;
        double discount = 0.95;
        double maturity = 1.0;
        double bump = 1.0e-4;
        double tolerance = 1.0e-3;
        BachelierCalculator calc = new BachelierCalculator(Option.Type.Call, strike, forward, stdDev, discount);
        BachelierCalculator up = new BachelierCalculator(Option.Type.Call, strike, forward + bump, stdDev, discount);
        BachelierCalculator down = new BachelierCalculator(Option.Type.Call, strike, forward - bump, stdDev, discount);
        assertEquals(calc.deltaForward(), (up.value() - down.value()) / (2.0 * bump), tolerance);
        assertEquals(calc.gammaForward(),
                (up.deltaForward() - down.deltaForward()) / (2.0 * bump), tolerance);

        BachelierCalculator volUp = new BachelierCalculator(Option.Type.Call, strike, forward, stdDev + bump, discount);
        BachelierCalculator volDown = new BachelierCalculator(Option.Type.Call, strike, forward, stdDev - bump, discount);
        double numericalVega = (volUp.value() - volDown.value()) / (2.0 * bump);
        assertEquals(calc.vega(maturity), numericalVega * Math.sqrt(maturity), tolerance);
        assertEquals(calc.vanna(maturity),
                (up.vega(maturity) - down.vega(maturity)) / (2.0 * bump), tolerance);
        assertEquals(calc.volga(maturity),
                (volUp.vega(maturity) - volDown.vega(maturity)) / (2.0 * bump), tolerance);
    }

    @Test
    public void testBachelierCalculatorAgainstAnalyticalFormula() {
        double forward = 100.0;
        double strike = 95.0;
        double stdDev = 15.0;
        double discount = 0.98;
        double d = (forward - strike) / stdDev;
        double analytical = discount * ((forward - strike) * new CumulativeNormalDistribution().value(d)
                + stdDev * new NormalDistribution().value(d));
        double calculated = new BachelierCalculator(
                Option.Type.Call, strike, forward, stdDev, discount).value();
        assertEquals(analytical, calculated, 1.0e-10);
    }

    @Test
    public void testBachelierCalculatorZeroVolatilityGreeks() {
        double forward = 100.0;
        double discount = 1.0;
        double spot = 98.0;
        double maturity = 1.0;
        double tolerance = 1.0e-10;
        Option.Type[] types = {
                Option.Type.Call, Option.Type.Put, Option.Type.Call, Option.Type.Put,
                Option.Type.Call, Option.Type.Put, Option.Type.Call, Option.Type.Put
        };
        double[] strikes = {90.0, 110.0, 100.0, 100.0, 90.0, 110.0, -10.0, 200.0};
        for (int i = 0; i < types.length; i++) {
            BachelierCalculator calc = new BachelierCalculator(types[i], strikes[i], forward, 0.0, discount);
            assertTrue(Double.isFinite(calc.deltaForward()));
            assertTrue(Double.isFinite(calc.delta(spot)));
            assertTrue(Double.isFinite(calc.gamma(spot)));
            assertTrue(Double.isFinite(calc.vega(maturity)));
            assertTrue(Double.isFinite(calc.theta(spot, maturity)));
            assertTrue(Double.isFinite(calc.rho(maturity)));
            assertTrue(Double.isFinite(calc.dividendRho(maturity)));
            assertEquals(0.0, calc.gammaForward(), tolerance);
            assertEquals(0.0, calc.gamma(spot), tolerance);
            assertEquals(0.0, calc.vega(maturity), tolerance);
            assertTrue(Double.isFinite(calc.strikeSensitivity()));
            assertTrue(Double.isFinite(calc.strikeGamma()));
            if (strikes[i] < forward - 5.0 && types[i] == Option.Type.Call) {
                assertEquals(1.0, calc.deltaForward(), 0.01);
            }
            if (strikes[i] > forward + 5.0 && types[i] == Option.Type.Call) {
                assertEquals(0.0, calc.deltaForward(), tolerance);
            }
            double expectedProb;
            if (types[i] == Option.Type.Call) {
                expectedProb = forward > strikes[i] ? 1.0 : (forward == strikes[i] ? 0.5 : 0.0);
            } else {
                expectedProb = forward < strikes[i] ? 1.0 : (forward == strikes[i] ? 0.5 : 0.0);
            }
            assertEquals(expectedProb, calc.itmCashProbability(), tolerance);
            assertEquals(expectedProb, calc.itmAssetProbability(), tolerance);
            assertEquals(0.0, calc.vanna(maturity), tolerance);
            assertEquals(0.0, calc.volga(maturity), tolerance);
        }
    }

    @Test
    public void testBachelierVsBlackConvergence() {
        double forward = 100.0;
        double strike = 100.0;
        double relativeVol = 0.01;
        double absoluteVol = relativeVol * forward;
        double bachelier = new BachelierCalculator(
                Option.Type.Call, strike, forward, absoluteVol, 1.0).value();
        double black = new BlackCalculator(
                Option.Type.Call, strike, forward, relativeVol, 1.0).value();
        assertEquals(0.0, Math.abs(bachelier - black) / black, 1.0e-2);
    }
}
