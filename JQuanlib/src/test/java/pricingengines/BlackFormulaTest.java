package pricingengines;

import jquant.Option;
import jquant.pricingengines.BlackFormula;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grounded in official QuantLib:
 * <ul>
 *   <li>{@code ql/option.hpp}: {@code enum Type { Put = -1, Call = 1 }}</li>
 *   <li>{@code ql/pricingengines/blackformula.cpp}: {@code sign = Integer(optionType)}</li>
 *   <li>{@code test-suite/blackcalculator.cpp}: published Black prices, parity and the
 *       zero-volatility intrinsic definition</li>
 * </ul>
 * https://github.com/lballabio/QuantLib/blob/master/test-suite/blackcalculator.cpp
 * https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/blackformula.cpp
 */
public class BlackFormulaTest {

    // QuantLib BlackCalculatorTests::testBlackCalculatorBasicValues tolerance.
    private static final double PRICE_TOLERANCE = 1.0e-8;
    // QuantLib BlackCalculatorTests::testBlackCalculatorPutCallParity tolerance.
    private static final double PARITY_TOLERANCE = 1.0e-10;
    private static final double STD_DEV_TOLERANCE = 1.0e-6;

    /**
     * Rows are the reference values published in
     * QuantLib test-suite/blackcalculator.cpp (testBlackCalculatorBasicValues
     * and the high-volatility edge case).
     */
    private static final double[][] PUBLISHED = {
            // type (1=Call, -1=Put), strike, forward, stdDev, discount, value
            {1, 100.0, 100.0, 0.20, 1.0, 7.9655674554058038},
            {-1, 100.0, 100.0, 0.20, 1.0, 7.9655674554058038},
            {1, 90.0, 100.0, 0.20, 1.0, 13.589108116054803},
            {-1, 110.0, 100.0, 0.20, 1.0, 14.292010941409899},
            {1, 110.0, 100.0, 0.20, 1.0, 4.2920109414098846},
            {-1, 90.0, 100.0, 0.20, 1.0, 3.5891081160548062},
            {1, 100.0, 100.0, 0.0, 1.0, 0.0},
            {-1, 100.0, 100.0, 0.0, 1.0, 0.0},
            {1, 100.0, 100.0, 2.0, 1.0, 68.268949213708595}
    };

    @Test
    public void testPublishedBlackPrices() {
        for (double[] row : PUBLISHED) {
            Option.Type type = row[0] > 0.0 ? Option.Type.Call : Option.Type.Put;
            double calculated = BlackFormula.blackFormula(
                    type, row[1], row[2], row[3], row[4], 0.0);
            assertEquals(row[5], calculated, PRICE_TOLERANCE,
                    type + " strike=" + row[1] + " forward=" + row[2]
                            + " stdDev=" + row[3]);
        }
    }

    @Test
    public void testPutCallParity() {
        // QuantLib testBlackCalculatorPutCallParity:
        // C - P = discount * (F - K)
        double forward = 100.0;
        double strike = 105.0;
        double stdDev = 0.25;
        double discount = 0.95;
        double call = BlackFormula.blackFormula(
                Option.Type.Call, strike, forward, stdDev, discount, 0.0);
        double put = BlackFormula.blackFormula(
                Option.Type.Put, strike, forward, stdDev, discount, 0.0);
        assertEquals(discount * (forward - strike), call - put, PARITY_TOLERANCE);

        // Same identity on the published ITM/OTM pair (strike 90 and strike 110).
        assertEquals(1.0 * (100.0 - 90.0),
                13.589108116054803 - 3.5891081160548062, PRICE_TOLERANCE);
        assertEquals(1.0 * (100.0 - 110.0),
                4.2920109414098846 - 14.292010941409899, PRICE_TOLERANCE);

        double call90 = price(Option.Type.Call, 90.0, 100.0, 0.20, 1.0);
        double put90 = price(Option.Type.Put, 90.0, 100.0, 0.20, 1.0);
        double call110 = price(Option.Type.Call, 110.0, 100.0, 0.20, 1.0);
        double put110 = price(Option.Type.Put, 110.0, 100.0, 0.20, 1.0);
        assertEquals(10.0, call90 - put90, PARITY_TOLERANCE);
        assertEquals(-10.0, call110 - put110, PARITY_TOLERANCE);
    }

    @Test
    public void testZeroVolatilityIntrinsic() {
        // QuantLib blackformula.cpp:
        //   stdDev == 0 -> max((forward - strike) * sign, 0) * discount
        // with sign = +1 for Call and -1 for Put. The blackcalculator test
        // states the same intrinsic: discount * max(0, Call ? F-K : K-F).
        assertIntrinsic(Option.Type.Call, 90.0, 100.0, 1.0, 10.0);
        assertIntrinsic(Option.Type.Put, 110.0, 100.0, 1.0, 10.0);
        assertIntrinsic(Option.Type.Call, 110.0, 100.0, 1.0, 0.0);
        assertIntrinsic(Option.Type.Put, 90.0, 100.0, 1.0, 0.0);
        assertIntrinsic(Option.Type.Call, 100.0, 100.0, 1.0, 0.0);
        assertIntrinsic(Option.Type.Put, 100.0, 100.0, 1.0, 0.0);
        assertIntrinsic(Option.Type.Call, 90.0, 100.0, 0.95, 9.5);
        assertIntrinsic(Option.Type.Put, 110.0, 100.0, 0.95, 9.5);
        assertIntrinsic(Option.Type.Call, 110.0, 100.0, 0.95, 0.0);
        assertIntrinsic(Option.Type.Put, 90.0, 100.0, 0.95, 0.0);

        // strike == 0 branch (positive stdDev): call is forward*discount, put is 0.
        assertEquals(95.0, price(Option.Type.Call, 0.0, 100.0, 0.20, 0.95), 0.0);
        assertEquals(0.0, price(Option.Type.Put, 0.0, 100.0, 0.20, 0.95), 0.0);
    }

    @Test
    public void testImpliedStdDevRecoversPublishedVolatility() {
        for (double[] row : PUBLISHED) {
            if (row[3] == 0.0) {
                continue;
            }
            Option.Type type = row[0] > 0.0 ? Option.Type.Call : Option.Type.Put;
            double implied = BlackFormula.blackFormulaImpliedStdDev(
                    type, row[1], row[2], row[5], row[4], 0.0,
                    Double.NaN, 1.0e-12, 100);
            assertEquals(row[3], implied, STD_DEV_TOLERANCE,
                    type + " strike=" + row[1] + " price=" + row[5]);
            double repriced = BlackFormula.blackFormula(
                    type, row[1], row[2], implied, row[4], 0.0);
            assertEquals(row[5], repriced, PRICE_TOLERANCE);
        }
    }

    @Test
    public void testDisplacedAtmScalesWithShiftedForward() {
        // Displacement shifts both forward and strike. ATM d1 is unchanged, so
        // the price scales by (forward + displacement) / forward. The undiscounted
        // ATM reference is the published 7.9655674554058038.
        double displacement = 10.0;
        double expected = 7.9655674554058038 * (100.0 + displacement) / 100.0;
        assertEquals(expected,
                BlackFormula.blackFormula(Option.Type.Call, 100.0, 100.0, 0.20, 1.0, displacement),
                PRICE_TOLERANCE);
        assertEquals(expected,
                BlackFormula.blackFormula(Option.Type.Put, 100.0, 100.0, 0.20, 1.0, displacement),
                PRICE_TOLERANCE);

        double implied = BlackFormula.blackFormulaImpliedStdDev(
                Option.Type.Call, 100.0, 100.0, expected, 1.0, displacement,
                0.20, 1.0e-8, 100);
        assertEquals(0.20, implied, STD_DEV_TOLERANCE);
    }

    @Test
    public void testCallIsNotThePutPrice() {
        // A swapped sign would price the strike-90 call at the published put.
        double call = price(Option.Type.Call, 90.0, 100.0, 0.20, 1.0);
        double put = price(Option.Type.Put, 90.0, 100.0, 0.20, 1.0);
        assertTrue(call > put);
        assertEquals(13.589108116054803, call, PRICE_TOLERANCE);
        assertEquals(3.5891081160548062, put, PRICE_TOLERANCE);
    }

    private static double price(Option.Type type, double strike, double forward,
                                double stdDev, double discount) {
        return BlackFormula.blackFormula(type, strike, forward, stdDev, discount, 0.0);
    }

    private static void assertIntrinsic(Option.Type type, double strike, double forward,
                                        double discount, double expected) {
        double calculated = price(type, strike, forward, 0.0, discount);
        assertEquals(expected, calculated, 0.0,
                type + " strike=" + strike + " forward=" + forward + " discount=" + discount);
    }
}
