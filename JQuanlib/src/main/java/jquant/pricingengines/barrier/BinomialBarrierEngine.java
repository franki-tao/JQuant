package jquant.pricingengines.barrier;

import jquant.Compounding;
import jquant.Handle;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.methods.lattices.BinomialTree;
import jquant.methods.lattices.BlackScholesLattice;
import jquant.methods.lattices.CoxRossRubinstein;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Calendar;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

/**
 * Pricing engine for barrier options using binomial trees.
 *
 * <p>Java port of {@code BinomialBarrierEngine<T,D>} from
 * {@code ql/pricingengines/barrier/binomialbarrierengine.hpp}. Java has no
 * class templates, so the tree is always
 * {@link CoxRossRubinstein} and the discretization is selected with
 * {@link Discretization}.
 *
 * <p>Timesteps for Cox-Ross-Rubinstein trees are adjusted using the Boyle
 * and Lau algorithm. The {@code maxTimeSteps} parameter limits the number of
 * steps when using the optimisation: if zero (the default) the maximum is
 * {@code max(1000, 5 * timeSteps)}; if equal to {@code timeSteps} Boyle-Lau
 * is disabled.
 */
public class BinomialBarrierEngine extends jquant.BarrierOption.engine {

    /** Discretized barrier-option scheme used by the lattice. */
    public enum Discretization {
        /** Standard barrier discretization (Boyle-Lau enabled). */
        STANDARD,
        /** Derman-Kani enhanced barrier discretization. */
        DERMAN_KANI
    }

    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private int maxTimeSteps_;
    private final Discretization discretization_;

    public BinomialBarrierEngine(GeneralizedBlackScholesProcess process,
                                 int timeSteps) {
        this(process, timeSteps, 0, Discretization.STANDARD);
    }

    public BinomialBarrierEngine(GeneralizedBlackScholesProcess process,
                                 int timeSteps,
                                 int maxTimeSteps) {
        this(process, timeSteps, maxTimeSteps, Discretization.STANDARD);
    }

    public BinomialBarrierEngine(GeneralizedBlackScholesProcess process,
                                 int timeSteps,
                                 int maxTimeSteps,
                                 Discretization discretization) {
        process_ = process;
        timeSteps_ = timeSteps;
        maxTimeSteps_ = maxTimeSteps;
        discretization_ = discretization;

        QL_REQUIRE(timeSteps > 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        QL_REQUIRE(maxTimeSteps == 0 || maxTimeSteps >= timeSteps,
                "maxTimeSteps must be zero or greater than or equal to "
                        + "timeSteps, " + maxTimeSteps + " not allowed");
        if (maxTimeSteps_ == 0)
            maxTimeSteps_ = Math.max(1000, timeSteps_ * 5);
        registerWith(process_);
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;
        QL_REQUIRE(payoff.strike() > 0.0, "strike must be positive");

        double s0 = process_.stateVariable().getValue().value();
        QL_REQUIRE(s0 > 0.0, "negative or null underlying given");
        QL_REQUIRE(!triggered(s0), "barrier touched");

        DayCounter rfdc = process_.riskFreeRate().getValue().dayCounter();
        DayCounter divdc = process_.dividendYield().getValue().dayCounter();
        DayCounter voldc = process_.blackVolatility().getValue().dayCounter();
        Calendar volcal = process_.blackVolatility().getValue().calendar();

        Date maturityDate = arguments_.exercise.lastDate();
        double v = process_.blackVolatility().getValue()
                .blackVol(maturityDate, s0);
        double r = process_.riskFreeRate().getValue()
                .zeroRate(maturityDate, rfdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY).rate();
        double q = process_.dividendYield().getValue()
                .zeroRate(maturityDate, divdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY).rate();
        Date referenceDate = process_.riskFreeRate().getValue().referenceDate();

        // binomial trees with constant coefficient
        YieldTermStructure flatRiskFree =
                new FlatForward(referenceDate, r, rfdc);
        YieldTermStructure flatDividends =
                new FlatForward(referenceDate, q, divdc);
        BlackVolTermStructure flatVol =
                new BlackConstantVol(referenceDate, volcal, v, voldc);

        double maturity = rfdc.yearFraction(referenceDate, maturityDate);

        BlackScholesMertonProcess bs = new BlackScholesMertonProcess(
                process_.stateVariable(),
                new Handle<>(flatDividends, true),
                new Handle<>(flatRiskFree, true),
                new Handle<>(flatVol, true));

        // correct timesteps to ensure a (local) minimum, using Boyle and Lau
        // approach. See Journal of Derivatives, 1/1994,
        // "Bumping up against the barrier with the binomial method"
        int optimumSteps = timeSteps_;
        double barrier = arguments_.barrier;
        if (maxTimeSteps_ > timeSteps_ && s0 > 0 && barrier > 0) {
            double divisor;
            if (s0 > barrier)
                divisor = Math.log(s0 / barrier) * Math.log(s0 / barrier);
            else
                divisor = Math.log(barrier / s0) * Math.log(barrier / s0);
            if (!close(divisor, 0.0)) {
                for (int i = 1; i < timeSteps_; ++i) {
                    int optimum = (int) ((i * (double) i * v * v * maturity)
                            / divisor);
                    if (timeSteps_ < optimum) {
                        optimumSteps = optimum;
                        break; // found first minimum with iterations>=timesteps
                    }
                }
            }

            if (optimumSteps > maxTimeSteps_)
                optimumSteps = maxTimeSteps_; // too high, limit
        }

        TimeGrid grid = new TimeGrid(maturity, optimumSteps);

        CoxRossRubinstein tree = new CoxRossRubinstein(
                bs, maturity, optimumSteps, payoff.strike());

        BlackScholesLattice lattice = new BlackScholesLattice(
                tree, r, maturity, optimumSteps);

        jquant.DiscretizedAsset option;
        if (discretization_ == Discretization.DERMAN_KANI) {
            option = new DiscretizedDermanKaniBarrierOption(
                    arguments_, process_, grid);
        } else {
            option = new DiscretizedBarrierOption(arguments_, process_, grid);
        }
        option.initialize(lattice, maturity);

        // Partial derivatives calculated from various points in the
        // binomial tree
        // (see J.C.Hull, "Options, Futures and other derivatives", 6th edition, pp 397/398)

        // Rollback to third-last step, and get underlying prices (s2) &
        // option values (p2) at this point
        option.rollback(grid.get(2));
        Array va2 = option.values();
        QL_REQUIRE(va2.size() == 3, "Expect 3 nodes in grid at second step");
        double p2u = va2.get(2); // up
        double p2m = va2.get(1); // mid
        double p2d = va2.get(0); // down (low)
        double s2u = lattice.underlying(2, 2); // up price
        double s2m = lattice.underlying(2, 1); // middle price
        double s2d = lattice.underlying(2, 0); // down (low) price

        // calculate gamma by taking the first derivative of the two deltas
        double delta2u = (p2u - p2m) / (s2u - s2m);
        double delta2d = (p2m - p2d) / (s2m - s2d);
        double gamma = (delta2u - delta2d) / ((s2u - s2d) / 2);

        // Rollback to second-last step, and get option values (p1) at
        // this point
        option.rollback(grid.get(1));
        Array va = option.values();
        QL_REQUIRE(va.size() == 2, "Expect 2 nodes in grid at first step");
        double p1u = va.get(1);
        double p1d = va.get(0);
        double s1u = lattice.underlying(1, 1); // up (high) price
        double s1d = lattice.underlying(1, 0); // down (low) price

        double delta = (p1u - p1d) / (s1u - s1d);

        // Finally, rollback to t=0
        option.rollback(0.0);
        double p0 = option.presentValue();

        // Store results
        results_.value = p0;
        results_.delta = delta;
        results_.gamma = gamma;
        // theta can be approximated by calculating the numerical derivative
        // between mid value at third-last step and at t0. The underlying price
        // is the same, only time varies.
        results_.theta = (p2m - p0) / grid.get(2);
    }
}
