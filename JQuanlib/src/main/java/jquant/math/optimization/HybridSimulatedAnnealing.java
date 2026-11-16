package jquant.math.optimization;

import jquant.math.Array;

import static jquant.math.MathUtils.QL_MAX_INTEGER;

//! Hybrid Simulated Annealing
/*! Implementation based on:
    Very Fast Simulated Re-Annealing, Lester Ingber,
    Mathl. Comput. Modelling, 967-973, 1989

    The sampler provides a probability density for the parameters, the
    probability determines whether a new point is accepted, the temperature
    controls both of them, and reannealing rescales the iteration independently
    for each dimension.
*/
public class HybridSimulatedAnnealing extends OptimizationMethod {

    //! Sampler interface: operator()(Array &newPoint,
    //! const Array &currentPoint, const Array &temp)
    public interface Sampler {
        void sample(Array newPoint, Array currentPoint, Array temp);
    }

    //! Probability interface: operator()(Real currentValue,
    //! Real newValue, const Array &temp)
    public interface Probability {
        boolean accept(double currentValue, double newValue, Array temp);
    }

    //! Temperature interface: operator()(Array &newTemp,
    //! const Array &currTemp, const Array &steps)
    public interface Temperature {
        void apply(Array newTemperature, Array currentTemperature, Array steps);
    }

    //! Reannealing interface: setProblem(Problem&) and
    //! operator()(Array &steps, const Array &currentPoint,
    //! Real currentValue, const Array &currTemp)
    public interface Reannealing {
        void setProblem(Problem problem);

        void reanneal(Array steps, Array currentPoint, double currentValue,
                      Array currentTemperature);
    }

    public enum LocalOptimizeScheme {
        NoLocalOptimize,
        EveryNewPoint,
        EveryBestPoint
    }

    public enum ResetScheme {
        NoResetScheme,
        ResetToBestPoint,
        ResetToOrigin
    }

    private final Sampler sampler_;
    private final Probability probability_;
    private final Temperature temperature_;
    private final Reannealing reannealing_;
    private final double startTemperature_;
    private final double endTemperature_;
    private final int reAnnealSteps_;
    private final ResetScheme resetScheme_;
    private final int resetSteps_;
    private final OptimizationMethod localOptimizer_;
    private final LocalOptimizeScheme optimizeScheme_;

    public HybridSimulatedAnnealing(Sampler sampler,
                                    Probability probability,
                                    Temperature temperature) {
        this(sampler, probability, temperature, new HybridSimulatedAnnealingFunctors.ReannealingTrivial(),
                200.0, 0.01, 50, ResetScheme.ResetToBestPoint, 150,
                new LevenbergMarquardt(1.0e-8, 1.0e-8, 1.0e-8, false),
                LocalOptimizeScheme.EveryBestPoint);
    }

    public HybridSimulatedAnnealing(Sampler sampler,
                                    Probability probability,
                                    Temperature temperature,
                                    Reannealing reannealing) {
        this(sampler, probability, temperature, reannealing,
                200.0, 0.01, 50, ResetScheme.ResetToBestPoint, 150,
                new LevenbergMarquardt(1.0e-8, 1.0e-8, 1.0e-8, false),
                LocalOptimizeScheme.EveryBestPoint);
    }

    public HybridSimulatedAnnealing(Sampler sampler,
                                    Probability probability,
                                    Temperature temperature,
                                    Reannealing reannealing,
                                    double startTemperature,
                                    double endTemperature,
                                    int reAnnealSteps,
                                    ResetScheme resetScheme,
                                    int resetSteps,
                                    OptimizationMethod localOptimizer,
                                    LocalOptimizeScheme optimizeScheme) {
        sampler_ = sampler;
        probability_ = probability;
        temperature_ = temperature;
        reannealing_ = reannealing;
        startTemperature_ = startTemperature;
        endTemperature_ = endTemperature;
        reAnnealSteps_ = reAnnealSteps == 0 ? QL_MAX_INTEGER : reAnnealSteps;
        resetScheme_ = resetScheme;
        resetSteps_ = resetSteps == 0 ? QL_MAX_INTEGER : resetSteps;
        localOptimizer_ = localOptimizer;
        optimizeScheme_ = localOptimizer != null ? optimizeScheme : LocalOptimizeScheme.NoLocalOptimize;
    }

    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria endCriteria) {
        EndCriteria.Type ecType = EndCriteria.Type.None;
        P.reset();
        reannealing_.setProblem(P);
        Array x = P.currentValue();
        int n = x.size();
        int k = 1;
        int kStationary = 1;
        int kReAnneal = 1;
        int kReset = 1;
        int maxK = endCriteria.maxIterations();
        int maxKStationary = endCriteria.maxStationaryStateIterations();
        boolean temperatureBreached = false;
        Array currentTemperature = new Array(n, startTemperature_);
        Array annealStep = new Array(n, 1.0);
        Array bestPoint = new Array(x);
        Array currentPoint = new Array(x);
        final Array startingPoint = x;
        Array newPoint = new Array(x);
        double bestValue = P.value(bestPoint);
        double currentValue = bestValue;
        double startingValue = bestValue; // to reset to starting point if desired

        while (k <= maxK && kStationary <= maxKStationary && !temperatureBreached) {
            // Draw a new sample point
            sampler_.sample(newPoint, currentPoint, currentTemperature);
            try {
                // Evaluate new point
                double newValue = P.value(newPoint);

                // Determine if new point is accepted
                if (probability_.accept(currentValue, newValue, currentTemperature)) {
                    if (optimizeScheme_ == LocalOptimizeScheme.EveryNewPoint) {
                        P.setCurrentValue(new Array(newPoint));
                        P.setFunctionValue(newValue);
                        localOptimizer_.minimize(P, endCriteria);
                        newPoint = P.currentValue();
                        newValue = P.functionValue();
                    }
                    currentPoint = new Array(newPoint);
                    currentValue = newValue;
                }

                // Check if we have a new best point
                if (newValue < bestValue) {
                    if (optimizeScheme_ == LocalOptimizeScheme.EveryBestPoint) {
                        P.setCurrentValue(new Array(newPoint));
                        P.setFunctionValue(newValue);
                        localOptimizer_.minimize(P, endCriteria);
                        newPoint = P.currentValue();
                        newValue = P.functionValue();
                    }
                    kStationary = 0;
                    bestValue = newValue;
                    bestPoint = new Array(newPoint);
                }
            } catch (Exception e) {
                // Do nothing, move on to new draw
            }

            // Increase steps
            k++;
            kStationary++;
            for (int i = 0; i < n; ++i)
                annealStep.set(i, annealStep.get(i) + 1.0);

            // Reanneal if necessary
            if (kReAnneal == reAnnealSteps_) {
                kReAnneal = 0;
                reannealing_.reanneal(annealStep, currentPoint, currentValue,
                        currentTemperature);
            }
            kReAnneal++;

            // Reset if necessary
            if (kReset == resetSteps_) {
                kReset = 0;
                switch (resetScheme_) {
                    case NoResetScheme:
                        break;
                    case ResetToOrigin:
                        currentPoint = new Array(startingPoint);
                        currentValue = startingValue;
                        break;
                    case ResetToBestPoint:
                        currentPoint = new Array(bestPoint);
                        currentValue = bestValue;
                        break;
                }
            }
            kReset++;

            // Update the current temperature according to current step
            temperature_.apply(currentTemperature, currentTemperature, annealStep);

            // Check if temperature condition is breached
            temperatureBreached = true;
            for (int i = 0; i < n; ++i) {
                if (!(currentTemperature.get(i) < endTemperature_)) {
                    temperatureBreached = false;
                    break;
                }
            }
        }

        // Change end criteria type if appropriate
        if (k > maxK)
            ecType = EndCriteria.Type.MaxIterations;
        else if (kStationary > maxKStationary)
            ecType = EndCriteria.Type.StationaryPoint;

        // Set result to best point
        P.setCurrentValue(new Array(bestPoint));
        P.setFunctionValue(bestValue);
        return ecType;
    }
}
