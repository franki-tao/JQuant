package jquant.pricingengines.vanilla;

import jquant.Compounding;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.EuropeanOption;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.methods.lattices.AdditiveEQPBinomialTree;
import jquant.methods.lattices.BinomialTree;
import jquant.methods.lattices.BlackScholesLattice;
import jquant.methods.lattices.CoxRossRubinstein;
import jquant.methods.lattices.JarrowRudd;
import jquant.methods.lattices.Joshi4;
import jquant.methods.lattices.LeisenReimer;
import jquant.methods.lattices.Tian;
import jquant.methods.lattices.Trigeorgis;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.Greeks;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for vanilla options using binomial trees.
 *
 * <p>Java port of the {@code BinomialVanillaEngine<T>} template from
 * {@code ql/pricingengines/vanilla/binomialengine.hpp}. Java has no class
 * templates, so the tree is selected at construction time.
 */
public class BinomialVanillaEngine implements PricingEngine {
    /** Binomial tree used by the engine. */
    public enum TreeType {
        JARROW_RUDD,
        COX_ROSS_RUBINSTEIN,
        ADDITIVE_EQP,
        TRIGEORGIS,
        TIAN,
        LEISEN_REIMER,
        JOSHI
    }

    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private final TreeType treeType_;
    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public BinomialVanillaEngine(GeneralizedBlackScholesProcess process,
                                 int timeSteps,
                                 TreeType treeType) {
        QL_REQUIRE(timeSteps >= 2,
                "at least 2 time steps required, " + timeSteps + " provided");
        process_ = process;
        timeSteps_ = timeSteps;
        treeType_ = treeType;
        results_.additionalResults = new HashMap<>();
        process_.registerObserver(new Observer() {
            @Override
            public void registerWith(Observable observable) {
            }

            @Override
            public void unregisterWithAll() {
            }

            @Override
            public void unregisterWith(Observable observable) {
            }

            @Override
            public void update() {
                notifyObservers();
            }
        });
    }

    @Override
    public arguments getArguments() {
        return arguments_;
    }

    @Override
    public results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_.value = Double.NaN;
        results_.errorEstimate = Double.NaN;
        results_.valuationDate = new Date();
        results_.delta = Double.NaN;
        results_.gamma = Double.NaN;
        results_.theta = Double.NaN;
        results_.thetaPerDay = Double.NaN;
        results_.vega = Double.NaN;
        results_.rho = Double.NaN;
        results_.dividendRho = Double.NaN;
        results_.elasticity = Double.NaN;
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void calculate() {
        DayCounter rfdc = process_.riskFreeRate().getValue().dayCounter();
        DayCounter divdc = process_.dividendYield().getValue().dayCounter();
        DayCounter voldc = process_.blackVolatility().getValue().dayCounter();
        Calendar volcal = process_.blackVolatility().getValue().calendar();

        double s0 = process_.stateVariable().getValue().value();
        QL_REQUIRE(s0 > 0.0, "negative or null underlying given");
        Date maturityDate = arguments_.exercise.lastDate();
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European option");
        double v = process_.blackVolatility().getValue()
                .blackVol(maturityDate, s0, false);
        double r = process_.riskFreeRate().getValue()
                .zeroRate(maturityDate, rfdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY, false).rate();
        double q = process_.dividendYield().getValue()
                .zeroRate(maturityDate, divdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY, false).rate();
        Date referenceDate = process_.riskFreeRate().getValue().referenceDate();

        // binomial trees with constant coefficient
        YieldTermStructure flatRiskFree = new FlatForward(
                referenceDate, r, rfdc, Compounding.Continuous, Frequency.ANNUAL);
        YieldTermStructure flatDividends = new FlatForward(
                referenceDate, q, divdc, Compounding.Continuous, Frequency.ANNUAL);
        BlackConstantVol flatVol = new BlackConstantVol(referenceDate, volcal, v, voldc);

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        double maturity = rfdc.yearFraction(referenceDate, maturityDate,
                new Date(), new Date());

        BlackScholesMertonProcess bs = new BlackScholesMertonProcess(
                process_.stateVariable(),
                new Handle<>(flatDividends, true),
                new Handle<>(flatRiskFree, true),
                new Handle<>(flatVol, true));

        TimeGrid grid = new TimeGrid(maturity, timeSteps_);
        BinomialTree<?> tree = makeTree(bs, maturity, timeSteps_, payoff.strike());
        BlackScholesLattice lattice = new BlackScholesLattice(
                tree, r, maturity, timeSteps_);

        DiscretizedVanillaOption option = new DiscretizedVanillaOption(
                arguments_, process_, grid);
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

        double gamma;
        if (s2u > s2m && s2m > s2d) {
            // calculate gamma by taking the first derivative of the two deltas
            double delta2u = (p2u - p2m) / (s2u - s2m);
            double delta2d = (p2m - p2d) / (s2m - s2d);
            gamma = (delta2u - delta2d) / ((s2u - s2d) / 2);
        } else {
            gamma = Double.NaN;
        }

        // Rollback to second-last step, and get option values (p1) at
        // this point
        option.rollback(grid.get(1));
        Array va = option.values();
        QL_REQUIRE(va.size() == 2, "Expect 2 nodes in grid at first step");
        double p1u = va.get(1);
        double p1d = va.get(0);
        double s1u = lattice.underlying(1, 1); // up (high) price
        double s1d = lattice.underlying(1, 0); // down (low) price

        double delta = (s1u > s1d) ? (p1u - p1d) / (s1u - s1d) : Double.NaN;

        // Finally, rollback to t=0
        option.rollback(0.0);
        double p0 = option.presentValue();

        results_.value = p0;
        results_.delta = delta;
        results_.gamma = gamma;
        if (Double.isNaN(delta) || Double.isNaN(gamma)) {
            results_.theta = Double.NaN;
        } else {
            results_.theta = Greeks.blackScholesTheta(
                    process_, results_.value, results_.delta, results_.gamma);
        }
        results_.valuationDate = referenceDate;
        results_.errorEstimate = Double.NaN;
    }

    private BinomialTree<?> makeTree(GeneralizedBlackScholesProcess bs,
                                     double maturity, int steps, double strike) {
        return switch (treeType_) {
            case JARROW_RUDD -> new JarrowRudd(bs, maturity, steps, strike);
            case COX_ROSS_RUBINSTEIN -> new CoxRossRubinstein(bs, maturity, steps, strike);
            case ADDITIVE_EQP -> new AdditiveEQPBinomialTree(bs, maturity, steps, strike);
            case TRIGEORGIS -> new Trigeorgis(bs, maturity, steps, strike);
            case TIAN -> new Tian(bs, maturity, steps, strike);
            case LEISEN_REIMER -> new LeisenReimer(bs, maturity, steps, strike);
            case JOSHI -> new Joshi4(bs, maturity, steps, strike);
        };
    }

    @Override
    public void registerObserver(Observer observer) {
        observers_.add(observer);
    }

    @Override
    public void unregisterObserver(Observer observer) {
        observers_.remove(observer);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers_);
        } else {
            for (Observer observer : Set.copyOf(observers_)) {
                observer.update();
            }
        }
    }
}
