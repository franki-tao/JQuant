package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.randomnumbers.MersenneTwisterUniformRng;
import jquant.math.randomnumbers.SeedGenerator;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Functors for use with HybridSimulatedAnnealing
public final class HybridSimulatedAnnealingFunctors {

    private HybridSimulatedAnnealingFunctors() {
    }

    private static double uniform(MersenneTwisterUniformRng generator) {
        return generator.nextReal();
    }

    //! Lognormal Sampler
    /*! Sample from lognormal distribution. This means that the parameter
        space must have support on the positive side of the real line only.
    */
    public static class SamplerLogNormal implements HybridSimulatedAnnealing.Sampler {
        private final MersenneTwisterUniformRng generator_;
        private final InverseCumulativeNormal distribution_ = new InverseCumulativeNormal();

        public SamplerLogNormal() {
            this(SeedGenerator.INSTANCE.get());
        }

        public SamplerLogNormal(long seed) {
            generator_ = new MersenneTwisterUniformRng(seed);
        }

        @Override
        public void sample(Array newPoint, Array currentPoint, Array temp) {
            QL_REQUIRE(newPoint.size() == currentPoint.size(), "Incompatible input");
            QL_REQUIRE(newPoint.size() == temp.size(), "Incompatible input");
            for (int i = 0; i < currentPoint.size(); ++i)
                newPoint.set(i, currentPoint.get(i)
                        * Math.exp(Math.sqrt(temp.get(i))
                        * distribution_.value(uniform(generator_))));
        }
    }

    //! Gaussian Sampler
    /*! Sample from normal distribution. This means that the parameter space
        must have support on the whole real line.
    */
    public static class SamplerGaussian implements HybridSimulatedAnnealing.Sampler {
        private final MersenneTwisterUniformRng generator_;
        private final InverseCumulativeNormal distribution_ = new InverseCumulativeNormal();

        public SamplerGaussian() {
            this(SeedGenerator.INSTANCE.get());
        }

        public SamplerGaussian(long seed) {
            generator_ = new MersenneTwisterUniformRng(seed);
        }

        @Override
        public void sample(Array newPoint, Array currentPoint, Array temp) {
            QL_REQUIRE(newPoint.size() == currentPoint.size(), "Incompatible input");
            QL_REQUIRE(newPoint.size() == temp.size(), "Incompatible input");
            for (int i = 0; i < currentPoint.size(); ++i)
                newPoint.set(i, currentPoint.get(i)
                        + Math.sqrt(temp.get(i))
                        * distribution_.value(uniform(generator_)));
        }
    }

    //! Gaussian Ring Sampler
    /*! Sample from normal distribution, but constrained to lie within
        boundaries. If the value ends up beyond the boundary, the value is
        circled back from the other side.
    */
    public static class SamplerRingGaussian implements HybridSimulatedAnnealing.Sampler {
        private final MersenneTwisterUniformRng generator_;
        private final InverseCumulativeNormal distribution_ = new InverseCumulativeNormal();
        private final Array lower_, upper_;

        public SamplerRingGaussian(Array lower, Array upper) {
            this(lower, upper, SeedGenerator.INSTANCE.get());
        }

        public SamplerRingGaussian(Array lower, Array upper, long seed) {
            generator_ = new MersenneTwisterUniformRng(seed);
            lower_ = new Array(lower);
            upper_ = new Array(upper);
        }

        @Override
        public void sample(Array newPoint, Array currentPoint, Array temp) {
            QL_REQUIRE(newPoint.size() == currentPoint.size(), "Incompatible input");
            QL_REQUIRE(newPoint.size() == temp.size(), "Incompatible input");
            for (int i = 0; i < currentPoint.size(); ++i) {
                newPoint.set(i, currentPoint.get(i)
                        + Math.sqrt(temp.get(i))
                        * distribution_.value(uniform(generator_)));
                while (newPoint.get(i) < lower_.get(i) || newPoint.get(i) > upper_.get(i)) {
                    if (newPoint.get(i) < lower_.get(i)) {
                        newPoint.set(i, upper_.get(i) + newPoint.get(i) - lower_.get(i));
                    } else {
                        newPoint.set(i, lower_.get(i) + newPoint.get(i) - upper_.get(i));
                    }
                }
            }
        }
    }

    //! Gaussian Mirror Sampler
    /*! Sample from normal distribution, but constrained to lie within
        boundaries. If the value ends up beyond the boundary, the value is
        reflected back.
    */
    public static class SamplerMirrorGaussian implements HybridSimulatedAnnealing.Sampler {
        private final MersenneTwisterUniformRng generator_;
        private final InverseCumulativeNormal distribution_ = new InverseCumulativeNormal();
        private final Array lower_, upper_;

        public SamplerMirrorGaussian(Array lower, Array upper) {
            this(lower, upper, SeedGenerator.INSTANCE.get());
        }

        public SamplerMirrorGaussian(Array lower, Array upper, long seed) {
            generator_ = new MersenneTwisterUniformRng(seed);
            lower_ = new Array(lower);
            upper_ = new Array(upper);
        }

        @Override
        public void sample(Array newPoint, Array currentPoint, Array temp) {
            QL_REQUIRE(newPoint.size() == currentPoint.size(), "Incompatible input");
            QL_REQUIRE(newPoint.size() == temp.size(), "Incompatible input");
            for (int i = 0; i < currentPoint.size(); ++i) {
                newPoint.set(i, currentPoint.get(i)
                        + Math.sqrt(temp.get(i))
                        * distribution_.value(uniform(generator_)));
                while (newPoint.get(i) < lower_.get(i) || newPoint.get(i) > upper_.get(i)) {
                    if (newPoint.get(i) < lower_.get(i))
                        newPoint.set(i, lower_.get(i) + lower_.get(i) - newPoint.get(i));
                    else
                        newPoint.set(i, upper_.get(i) + upper_.get(i) - newPoint.get(i));
                }
            }
        }
    }

    //! Cauchy Sampler
    /*! Sample from Cauchy distribution. For lower dimensions it could be
        faster than the Gaussian sampler, especially when combined with the
        Cauchy temperature.
    */
    public static class SamplerCauchy implements HybridSimulatedAnnealing.Sampler {
        protected final MersenneTwisterUniformRng generator_;

        public SamplerCauchy() {
            this(SeedGenerator.INSTANCE.get());
        }

        public SamplerCauchy(long seed) {
            generator_ = new MersenneTwisterUniformRng(seed);
        }

        @Override
        public void sample(Array newPoint, Array currentPoint, Array temp) {
            QL_REQUIRE(newPoint.size() == currentPoint.size(), "Incompatible input");
            QL_REQUIRE(newPoint.size() == temp.size(), "Incompatible input");
            for (int i = 0; i < currentPoint.size(); ++i) {
                final double draw = uniform(generator_);
                final double cauchy = Math.tan(Math.PI * (draw - 0.5));
                newPoint.set(i, currentPoint.get(i) + temp.get(i) * cauchy);
            }
        }
    }

    //! Very Fast Annealing Sampler
    /*! For consistency should be used with TemperatureVeryFastAnnealing.
        Requires that the parameter space be bounded above and below.
    */
    public static class SamplerVeryFastAnnealing implements HybridSimulatedAnnealing.Sampler {
        private final Array lower_, upper_;
        private final MersenneTwisterUniformRng generator_;

        public SamplerVeryFastAnnealing(Array lower, Array upper) {
            this(lower, upper, SeedGenerator.INSTANCE.get());
        }

        public SamplerVeryFastAnnealing(Array lower, Array upper, long seed) {
            lower_ = new Array(lower);
            upper_ = new Array(upper);
            QL_REQUIRE(lower_.size() == upper_.size(), "Incompatible input");
            generator_ = new MersenneTwisterUniformRng(seed);
        }

        @Override
        public void sample(Array newPoint, Array currentPoint, Array temp) {
            QL_REQUIRE(newPoint.size() == currentPoint.size(), "Incompatible input");
            QL_REQUIRE(newPoint.size() == lower_.size(), "Incompatible input");
            QL_REQUIRE(newPoint.size() == temp.size(), "Incompatible input");
            for (int i = 0; i < currentPoint.size(); ++i) {
                newPoint.set(i, lower_.get(i) - 1.0);
                while (newPoint.get(i) < lower_.get(i)
                        || newPoint.get(i) > upper_.get(i)) {
                    final double draw = uniform(generator_);
                    final int sign = (0.5 < draw ? 1 : 0) - (draw < 0.5 ? 1 : 0);
                    final double y = sign * temp.get(i)
                            * (Math.pow(1.0 + 1.0 / temp.get(i),
                            Math.abs(2.0 * draw - 1.0)) - 1.0);
                    newPoint.set(i, currentPoint.get(i)
                            + y * (upper_.get(i) - lower_.get(i)));
                }
            }
        }
    }

    //! Always Downhill Probability
    /*! Only points that improve on the current solution are accepted.
        Depending on the problem, this makes it very unlikely that the
        optimizer will be able to escape a local optimum.
    */
    public static class ProbabilityAlwaysDownhill implements HybridSimulatedAnnealing.Probability {
        @Override
        public boolean accept(double currentValue, double newValue, Array temp) {
            return currentValue > newValue;
        }
    }

    //! Boltzmann Probability
    /*! The probability of accepting a new point is sampled from a Boltzmann
        distribution: 1 / (1 + exp(-(current-new)/T)) > u.
    */
    public static class ProbabilityBoltzmann implements HybridSimulatedAnnealing.Probability {
        private final MersenneTwisterUniformRng generator_;

        public ProbabilityBoltzmann() {
            this(SeedGenerator.INSTANCE.get());
        }

        public ProbabilityBoltzmann(long seed) {
            generator_ = new MersenneTwisterUniformRng(seed);
        }

        @Override
        public boolean accept(double currentValue, double newValue, Array temp) {
            double temperature = temp.max();
            return 1.0 / (1.0 + Math.exp((newValue - currentValue) / temperature))
                    > uniform(generator_);
        }
    }

    //! Boltzmann Downhill Probability
    /*! As ProbabilityBoltzmann, but if new < current the point is always
        accepted.
    */
    public static class ProbabilityBoltzmannDownhill
            implements HybridSimulatedAnnealing.Probability {
        private final MersenneTwisterUniformRng generator_;

        public ProbabilityBoltzmannDownhill() {
            this(SeedGenerator.INSTANCE.get());
        }

        public ProbabilityBoltzmannDownhill(long seed) {
            generator_ = new MersenneTwisterUniformRng(seed);
        }

        @Override
        public boolean accept(double currentValue, double newValue, Array temp) {
            if (newValue < currentValue)
                return true;
            double mTemperature = temp.max();
            return 1.0 / (1.0 + Math.exp((newValue - currentValue) / mTemperature))
                    > uniform(generator_);
        }
    }

    //! Temperature Boltzmann
    /*! For use with the Gaussian sampler. */
    public static class TemperatureBoltzmann implements HybridSimulatedAnnealing.Temperature {
        private final Array initialTemp_;

        public TemperatureBoltzmann(double initialTemp, int dimension) {
            initialTemp_ = new Array(dimension, initialTemp);
        }

        @Override
        public void apply(Array newTemperature, Array currentTemperature, Array steps) {
            QL_REQUIRE(currentTemperature.size() == initialTemp_.size(),
                    "Incompatible input");
            QL_REQUIRE(currentTemperature.size() == newTemperature.size(),
                    "Incompatible input");
            for (int i = 0; i < initialTemp_.size(); ++i)
                newTemperature.set(i, initialTemp_.get(i) / Math.log(steps.get(i)));
        }
    }

    //! Temperature Cauchy
    /*! For use with the Cauchy sampler. */
    public static class TemperatureCauchy implements HybridSimulatedAnnealing.Temperature {
        private final Array initialTemp_;

        public TemperatureCauchy(double initialTemp, int dimension) {
            initialTemp_ = new Array(dimension, initialTemp);
        }

        @Override
        public void apply(Array newTemperature, Array currentTemperature, Array steps) {
            QL_REQUIRE(currentTemperature.size() == initialTemp_.size(),
                    "Incompatible input");
            QL_REQUIRE(currentTemperature.size() == newTemperature.size(),
                    "Incompatible input");
            for (int i = 0; i < initialTemp_.size(); ++i)
                newTemperature.set(i, initialTemp_.get(i) / steps.get(i));
        }
    }

    public static class TemperatureCauchy1D implements HybridSimulatedAnnealing.Temperature {
        private final double inverseN_;
        private final Array initialTemp_;

        public TemperatureCauchy1D(double initialTemp, int dimension) {
            inverseN_ = 1.0 / dimension;
            initialTemp_ = new Array(dimension, initialTemp);
        }

        @Override
        public void apply(Array newTemperature, Array currentTemperature, Array steps) {
            QL_REQUIRE(currentTemperature.size() == initialTemp_.size(),
                    "Incompatible input");
            QL_REQUIRE(currentTemperature.size() == newTemperature.size(),
                    "Incompatible input");
            for (int i = 0; i < initialTemp_.size(); ++i)
                newTemperature.set(i, initialTemp_.get(i)
                        / Math.pow(steps.get(i), inverseN_));
        }
    }

    public static class TemperatureExponential implements HybridSimulatedAnnealing.Temperature {
        private final Array initialTemp_;
        private final double power_;

        public TemperatureExponential(double initialTemp, int dimension) {
            this(initialTemp, dimension, 0.95);
        }

        public TemperatureExponential(double initialTemp, int dimension, double power) {
            initialTemp_ = new Array(dimension, initialTemp);
            power_ = power;
        }

        @Override
        public void apply(Array newTemperature, Array currentTemperature, Array steps) {
            QL_REQUIRE(currentTemperature.size() == initialTemp_.size(),
                    "Incompatible input");
            QL_REQUIRE(currentTemperature.size() == newTemperature.size(),
                    "Incompatible input");
            for (int i = 0; i < initialTemp_.size(); ++i)
                newTemperature.set(i, initialTemp_.get(i)
                        * Math.pow(power_, steps.get(i)));
        }
    }

    //! Temperature Very Fast Annealing
    /*! For use with the Very Fast Annealing sampler. */
    public static class TemperatureVeryFastAnnealing
            implements HybridSimulatedAnnealing.Temperature {
        private final double inverseN_;
        private final Array initialTemp_, finalTemp_, exponent_;

        public TemperatureVeryFastAnnealing(double initialTemp, double finalTemp,
                                            double maxSteps, int dimension) {
            inverseN_ = 1.0 / dimension;
            initialTemp_ = new Array(dimension, initialTemp);
            finalTemp_ = new Array(dimension, finalTemp);
            exponent_ = new Array(dimension, 0.0);
            double coeff = Math.pow(maxSteps, -inverseN_);
            for (int i = 0; i < initialTemp_.size(); ++i)
                exponent_.set(i, -Math.log(finalTemp_.get(i) / initialTemp_.get(i))
                        * coeff);
        }

        @Override
        public void apply(Array newTemperature, Array currentTemperature, Array steps) {
            QL_REQUIRE(currentTemperature.size() == initialTemp_.size(),
                    "Incompatible input");
            QL_REQUIRE(currentTemperature.size() == newTemperature.size(),
                    "Incompatible input");
            for (int i = 0; i < initialTemp_.size(); ++i)
                newTemperature.set(i, initialTemp_.get(i)
                        * Math.exp(-exponent_.get(i)
                        * Math.pow(steps.get(i), inverseN_)));
        }
    }

    //! Reannealing Trivial
    /*! No reannealing is performed. */
    public static class ReannealingTrivial implements HybridSimulatedAnnealing.Reannealing {
        public ReannealingTrivial() {
        }

        @Override
        public void setProblem(Problem problem) {
        }

        @Override
        public void reanneal(Array steps, Array currentPoint, double currentValue,
                             Array currentTemperature) {
        }
    }

    //! Reannealing Finite Differences
    /*! Rescales the annealing step independently in each dimension according
        to the sensitivity of the objective in that direction.
    */
    public static class ReannealingFiniteDifferences
            implements HybridSimulatedAnnealing.Reannealing {
        private Problem problem_;
        private final double stepSize_, minSize_, functionTol_;
        private final int N_;
        private final Array lower_, upper_, initialTemp_, bounded_;
        private boolean bound_ = false;

        public ReannealingFiniteDifferences(double initialTemp, int dimension) {
            this(initialTemp, dimension, new Array(0), new Array(0),
                    1e-7, 1e-10, 1e-10);
        }

        public ReannealingFiniteDifferences(double initialTemp, int dimension,
                                            Array lower, Array upper) {
            this(initialTemp, dimension, lower, upper, 1e-7, 1e-10, 1e-10);
        }

        public ReannealingFiniteDifferences(double initialTemp, int dimension,
                                            Array lower, Array upper,
                                            double stepSize, double minSize,
                                            double functionTol) {
            stepSize_ = stepSize;
            minSize_ = minSize;
            functionTol_ = functionTol;
            N_ = dimension;
            lower_ = new Array(lower);
            upper_ = new Array(upper);
            initialTemp_ = new Array(dimension, initialTemp);
            bounded_ = new Array(dimension, 1.0);
            if (!lower_.empty() && !upper_.empty()) {
                QL_REQUIRE(lower_.size() == N_, "Incompatible input");
                QL_REQUIRE(upper_.size() == N_, "Incompatible input");
                bound_ = true;
                for (int i = 0; i < N_; ++i)
                    bounded_.set(i, upper_.get(i) - lower_.get(i));
            }
        }

        @Override
        public void setProblem(Problem problem) {
            problem_ = problem;
        }

        @Override
        public void reanneal(Array steps, Array currentPoint, double currentValue,
                             Array currentTemperature) {
            QL_REQUIRE(currentTemperature.size() == N_, "Incompatible input");
            QL_REQUIRE(steps.size() == N_, "Incompatible input");

            Array finiteDiffs = new Array(N_, 0.0);
            double finiteDiffMax = 0.0;
            Array offsetPoint = new Array(currentPoint);
            for (int i = 0; i < N_; ++i) {
                offsetPoint.set(i, offsetPoint.get(i) + stepSize_);
                finiteDiffs.set(i, bounded_.get(i)
                        * Math.abs((problem_.value(offsetPoint) - currentValue)
                        / stepSize_));
                offsetPoint.set(i, offsetPoint.get(i) - stepSize_);
                if (finiteDiffs.get(i) < minSize_)
                    finiteDiffs.set(i, minSize_);
                if (finiteDiffs.get(i) > finiteDiffMax)
                    finiteDiffMax = finiteDiffs.get(i);
            }
            for (int i = 0; i < N_; ++i) {
                double tRatio = initialTemp_.get(i) / currentTemperature.get(i);
                double sRatio = finiteDiffMax / finiteDiffs.get(i);
                if (sRatio * tRatio < functionTol_)
                    steps.set(i, Math.pow(Math.abs(Math.log(functionTol_)), (double) N_));
                else
                    steps.set(i, Math.pow(Math.abs(Math.log(sRatio * tRatio)),
                            (double) N_));
            }
        }
    }
}
