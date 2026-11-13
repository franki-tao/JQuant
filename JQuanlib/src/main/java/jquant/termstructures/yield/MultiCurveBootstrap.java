package jquant.termstructures.yield;

import jquant.math.Array;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.optimization.NoConstraint;
import jquant.math.optimization.OptimizationMethod;
import jquant.math.optimization.Problem;
import jquant.math.optimization.SimpleCostFunction;
import jquant.patterns.Observer;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Joint bootstrap of a set of curves whose dependencies form a cycle.
 *
 * <p>Port of QuantLib's {@code MultiCurveBootstrap} in
 * {@code ql/termstructures/globalbootstrap.cpp}. It concatenates the
 * contributors' initial guesses and error terms, optimizes the joint system
 * and finally marks every contributing curve as valid.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/globalbootstrap.cpp
 */
public class MultiCurveBootstrap {
    private OptimizationMethod optimizer_;
    private EndCriteria endCriteria_;
    private final List<MultiCurveBootstrapContributor> contributors_ = new ArrayList<>();
    private final List<Observer> observers_ = new ArrayList<>();

    public MultiCurveBootstrap(double accuracy) {
        optimizer_ = new LevenbergMarquardt(accuracy, accuracy, accuracy, false);
        endCriteria_ = new EndCriteria(1000, 10, accuracy, accuracy, accuracy);
    }

    public MultiCurveBootstrap(OptimizationMethod optimizer, EndCriteria endCriteria) {
        optimizer_ = optimizer;
        endCriteria_ = endCriteria;
        double accuracy = 1.0e-10;
        if (optimizer_ == null)
            optimizer_ = new LevenbergMarquardt(accuracy, accuracy, accuracy, false);
        if (endCriteria_ == null)
            endCriteria_ = new EndCriteria(1000, 10, accuracy, accuracy, accuracy);
    }

    public void add(MultiCurveBootstrapContributor c) {
        contributors_.add(c);
        c.setParentBootstrapper(this);
    }

    public void addObserver(Observer o) {
        observers_.add(o);
    }

    public void runMultiCurveBootstrap() {
        int[] guessSizes = new int[contributors_.size()];
        List<Double> globalGuess = new ArrayList<>();
        for (int c = 0; c < contributors_.size(); ++c) {
            Array guess = contributors_.get(c).setupCostFunction();
            guessSizes[c] = guess.size();
            for (int i = 0; i < guess.size(); ++i)
                globalGuess.add(guess.get(i));
        }

        SimpleCostFunction costFunction = new SimpleCostFunction(x -> {
            // call the contributors' cost functions' set part
            int offset = 0;
            for (int c = 0; c < contributors_.size(); ++c) {
                Array tmp = new Array(guessSizes[c]);
                for (int i = 0; i < guessSizes[c]; ++i)
                    tmp.set(i, x.get(offset + i));
                offset += guessSizes[c];
                contributors_.get(c).setCostFunctionArgument(tmp);
            }

            // update observers
            for (Observer o : observers_)
                o.update();

            // collect the contributors' results
            List<Array> results = new ArrayList<>(contributors_.size());
            int resultSize = 0;
            for (MultiCurveBootstrapContributor contributor : contributors_) {
                Array r = contributor.evaluateCostFunction();
                results.add(r);
                resultSize += r.size();
            }

            // concatenate the contributors' values
            Array result = new Array(resultSize);
            offset = 0;
            for (Array r : results) {
                for (int i = 0; i < r.size(); ++i)
                    result.set(offset + i, r.get(i));
                offset += r.size();
            }
            return result;
        });

        NoConstraint noConstraint = new NoConstraint();
        double[] guess = new double[globalGuess.size()];
        for (int i = 0; i < guess.length; ++i)
            guess[i] = globalGuess.get(i);
        Problem problem = new Problem(costFunction, noConstraint, new Array(guess));
        EndCriteria.Type endType = optimizer_.minimize(problem, endCriteria_);

        QL_REQUIRE(EndCriteria.succeeded(endType),
                "global bootstrap failed to minimize to required accuracy "
                        + "(during multi curve bootstrap): " + endType);

        // set all contributors to valid
        for (MultiCurveBootstrapContributor c : contributors_)
            c.setToValid();
    }
}
