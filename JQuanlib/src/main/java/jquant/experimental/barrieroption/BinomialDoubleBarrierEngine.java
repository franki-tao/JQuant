package jquant.experimental.barrieroption;

import jquant.Compounding;
import jquant.DiscretizedAsset;
import jquant.Handle;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.methods.lattices.BlackScholesLattice;
import jquant.methods.lattices.CoxRossRubinstein;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for double-barrier options using binomial trees.
 *
 * <p>Java port of the {@code BinomialDoubleBarrierEngine<T,D>} template from
 * {@code ql/experimental/barrieroption/binomialdoublebarrierengine.hpp}. Java
 * has no class templates, so the tree is always
 * {@link CoxRossRubinstein} and the discretization is selected with
 * {@link Discretization}.
 *
 * <p>Unlike the single-barrier binomial engine, the official double-barrier
 * engine has no Boyle-Lau timestep adjustment: it takes only the process and
 * the number of time steps.
 */
public class BinomialDoubleBarrierEngine extends jquant.DoubleBarrierOption.engine {

    /** Discretized double-barrier-option scheme used by the lattice. */
    public enum Discretization {
        /** Standard double-barrier discretization. */
        STANDARD,
        /** Derman-Kani enhanced double-barrier discretization. */
        DERMAN_KANI
    }

    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private final Discretization discretization_;

    public BinomialDoubleBarrierEngine(
            GeneralizedBlackScholesProcess process, int timeSteps) {
        this(process, timeSteps, Discretization.STANDARD);
    }

    public BinomialDoubleBarrierEngine(
            GeneralizedBlackScholesProcess process,
            int timeSteps,
            Discretization discretization) {
        process_ = process;
        timeSteps_ = timeSteps;
        discretization_ = discretization;

        QL_REQUIRE(timeSteps > 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        registerWith(process_);
    }

    @Override
    public void calculate() {

        DayCounter rfdc = process_.riskFreeRate().getValue().dayCounter();
        DayCounter divdc = process_.dividendYield().getValue().dayCounter();
        DayCounter voldc = process_.blackVolatility().getValue().dayCounter();
        Calendar volcal = process_.blackVolatility().getValue().calendar();

        double s0 = process_.stateVariable().getValue().value();
        QL_REQUIRE(s0 > 0.0, "negative or null underlying given");
        double v = process_.blackVolatility().getValue().blackVol(
                arguments_.exercise.lastDate(), s0);
        Date maturityDate = arguments_.exercise.lastDate();
        double r = process_.riskFreeRate().getValue().zeroRate(
                maturityDate, rfdc, Compounding.Continuous,
                Frequency.NO_FREQUENCY).rate();
        double q = process_.dividendYield().getValue().zeroRate(
                maturityDate, divdc, Compounding.Continuous,
                Frequency.NO_FREQUENCY).rate();
        Date referenceDate = process_.riskFreeRate().getValue().referenceDate();

        // binomial trees with constant coefficient
        YieldTermStructure flatRiskFree =
                new FlatForward(referenceDate, r, rfdc);
        YieldTermStructure flatDividends =
                new FlatForward(referenceDate, q, divdc);
        BlackVolTermStructure flatVol =
                new BlackConstantVol(referenceDate, volcal, v, voldc);

        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        double maturity = rfdc.yearFraction(referenceDate, maturityDate);

        BlackScholesMertonProcess bs = new BlackScholesMertonProcess(
                process_.stateVariable(),
                new Handle<>(flatDividends, true),
                new Handle<>(flatRiskFree, true),
                new Handle<>(flatVol, true));

        TimeGrid grid = new TimeGrid(maturity, timeSteps_);

        CoxRossRubinstein tree = new CoxRossRubinstein(
                bs, maturity, timeSteps_, payoff.strike());

        BlackScholesLattice lattice = new BlackScholesLattice(
                tree, r, maturity, timeSteps_);

        DiscretizedAsset option;
        if (discretization_ == Discretization.DERMAN_KANI) {
            option = new DiscretizedDermanKaniDoubleBarrierOption(
                    arguments_, process_, grid);
        } else {
            option = new DiscretizedDoubleBarrierOption(
                    arguments_, process_, grid);
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

        results_.value = p0;
        results_.delta = delta;
        results_.gamma = gamma;
        // theta can be approximated by calculating the numerical derivative
        // between mid value at third-last step and at t0. The underlying price
        // is the same, only time varies.
        results_.theta = (p2m - p0) / grid.get(2);
    }
}
