package jquant.models.marketmodels.evolvers.volprocesses;

import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.models.marketmodels.evolvers.MarketModelVolProcess;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/evolvers/volprocesses/squarerootandersen.hpp/.cpp} port.
 *
 * <p>Displaced diffusion LMM with uncorrelated vol process. Called "Shifted
 * BGM" with Heston vol by Brace in "Engineering BGM." Vol process is an
 * external input.
 */
public class SquareRootAndersen extends MarketModelVolProcess {

    private final double theta_; // mean level
    private final double k_; // reversion speed
    private final double epsilon_; // volvar
    private final double v0_; // initial value of instantaneous variance
    private final int numberSubSteps_; // sub steps per evolution time

    private final double[] dt_; // time step lengths
    private final double[] eMinuskDt_; // exp( - k * dt)

    private final double w1_; // weights to use for computing variance across step
    private final double w2_;
    private final double PsiC_; // cut-off between two types of evolution

    // evolving values
    private double v_;
    private int currentStep_;
    private int subStep_;
    private final double[] vPath_;

    private final List<Double> state_;

    public SquareRootAndersen(double meanLevel,
                              double reversionSpeed,
                              double volVar,
                              double v0,
                              List<Double> evolutionTimes,
                              int numberSubSteps,
                              double w1,
                              double w2,
                              double cutPoint) {
        theta_ = meanLevel;
        k_ = reversionSpeed;
        epsilon_ = volVar;
        v0_ = v0;
        numberSubSteps_ = numberSubSteps;
        dt_ = new double[evolutionTimes.size() * numberSubSteps];
        eMinuskDt_ = new double[evolutionTimes.size() * numberSubSteps];
        w1_ = w1;
        w2_ = w2;
        PsiC_ = cutPoint;
        vPath_ = new double[evolutionTimes.size() * numberSubSteps + 1];
        state_ = new ArrayList<>(1);
        state_.add(0.0);

        int j = 0;
        for (; j < numberSubSteps_; ++j)
            dt_[j] = evolutionTimes.get(0) / numberSubSteps_;

        for (int i = 1; i < evolutionTimes.size(); ++i) {
            double dt = (evolutionTimes.get(i) - evolutionTimes.get(i - 1)) / numberSubSteps_;

            double ekdt = Math.exp(-k_ * dt);
            QL_REQUIRE(dt > 0.0, "Steps must be of positive size.");

            for (int k = 0; k < numberSubSteps_; ++k) {
                dt_[j] = dt;
                eMinuskDt_[j] = ekdt;

                ++j;
            }
        }
        vPath_[0] = v0_;
    }

    @Override
    public int variatesPerStep() {
        return numberSubSteps_;
    }

    @Override
    public int numberSteps() {
        return dt_.length * numberSubSteps_;
    }

    @Override
    public void nextPath() {
        v_ = v0_;
        currentStep_ = 0;
        subStep_ = 0;
    }

    private double doOneSubStep(double vt, double z, int j) {
        double eminuskT = eMinuskDt_[j];
        double m = theta_ + (vt - theta_) * eminuskT;
        double s2 = vt * epsilon_ * epsilon_ * eminuskT * (1 - eminuskT) / k_
                + theta_ * epsilon_ * epsilon_ * (1 - eminuskT) * (1 - eminuskT) / (2 * k_);
        double s = Math.sqrt(s2);
        double psi = s * s / (m * m);
        if (psi <= PsiC_) {
            double psiinv = 1.0 / psi;
            double b2 = 2.0 * psiinv - 1 + Math.sqrt(2 * psiinv * (2 * psiinv - 1.0));
            double b = Math.sqrt(b2);
            double a = m / (1 + b2);
            vt = a * (b + z) * (b + z);
        } else {
            double p = (psi - 1.0) / (psi + 1.0);
            double beta = (1.0 - p) / m;
            double u = new CumulativeNormalDistribution().value(z);

            if (u < p) {
                vt = 0;
                return vt;
            }

            vt = Math.log((1.0 - p) / (1.0 - u)) / beta;
        }

        return vt;
    }

    @Override
    public double nextstep(List<Double> variates) {
        for (int j = 0; j < numberSubSteps_; ++j) {
            v_ = doOneSubStep(v_, variates.get(j), subStep_);
            ++subStep_;
            vPath_[subStep_] = v_;
        }

        ++currentStep_;

        return 1.0; // no importance sampling here
    }

    @Override
    public double stepSd() {
        QL_REQUIRE(currentStep_ > 0, "nextStep must be called before stepSd");
        double stepVariance = 0.0;
        int lastStepStart = (currentStep_ - 1) * numberSubSteps_;
        for (int k = 0; k < numberSubSteps_; ++k)
            stepVariance += w1_ * vPath_[k + lastStepStart] + w2_ * vPath_[k + lastStepStart + 1];

        stepVariance /= numberSubSteps_;

        return Math.sqrt(stepVariance);
    }

    @Override
    public List<Double> stateVariables() {
        state_.set(0, v_);
        return state_;
    }

    @Override
    public int numberStateVariables() {
        return 1;
    }
}
