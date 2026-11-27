package jquant.models.equity;

import jquant.Handle;
import jquant.Quote;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.interpolations.impl.Linear;
import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.BrownianGeneratorFactory;
import jquant.patterns.LazyObject;
import jquant.processes.HestonSLVProcess;
import jquant.processes.HestonProcess;
import jquant.termstructures.volatility.equityfx.FixedLocalVolSurface;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Calibration of a Heston stochastic local volatility model based on Monte
 * Carlo simulation.
 *
 * <p>Port of {@code ql/models/equity/hestonslvmcmodel.{hpp,cpp}}.
 */
public class HestonSLVMCModel extends LazyObject {

    private final Handle<LocalVolTermStructure> localVol_;
    private final Handle<HestonModel> hestonModel_;
    private final BrownianGeneratorFactory brownianGeneratorFactory_;
    private final Date endDate_;
    private final int nBins_, calibrationPaths_;
    private final double mixingFactor_;
    private final TimeGrid timeGrid_;

    private FixedLocalVolSurface leverageFunction_;

    public HestonSLVMCModel(Handle<LocalVolTermStructure> localVol,
                            Handle<HestonModel> hestonModel,
                            BrownianGeneratorFactory brownianGeneratorFactory,
                            final Date endDate) {
        this(localVol, hestonModel, brownianGeneratorFactory, endDate,
                365, 201, (1 << 15), new ArrayList<>(), 1.0);
    }

    public HestonSLVMCModel(Handle<LocalVolTermStructure> localVol,
                            Handle<HestonModel> hestonModel,
                            BrownianGeneratorFactory brownianGeneratorFactory,
                            final Date endDate,
                            int timeStepsPerYear) {
        this(localVol, hestonModel, brownianGeneratorFactory, endDate,
                timeStepsPerYear, 201, (1 << 15), new ArrayList<>(), 1.0);
    }

    public HestonSLVMCModel(Handle<LocalVolTermStructure> localVol,
                            Handle<HestonModel> hestonModel,
                            BrownianGeneratorFactory brownianGeneratorFactory,
                            final Date endDate,
                            int timeStepsPerYear,
                            int nBins) {
        this(localVol, hestonModel, brownianGeneratorFactory, endDate,
                timeStepsPerYear, nBins, (1 << 15), new ArrayList<>(), 1.0);
    }

    public HestonSLVMCModel(Handle<LocalVolTermStructure> localVol,
                            Handle<HestonModel> hestonModel,
                            BrownianGeneratorFactory brownianGeneratorFactory,
                            final Date endDate,
                            int timeStepsPerYear,
                            int nBins,
                            int calibrationPaths) {
        this(localVol, hestonModel, brownianGeneratorFactory, endDate,
                timeStepsPerYear, nBins, calibrationPaths, new ArrayList<>(), 1.0);
    }

    public HestonSLVMCModel(Handle<LocalVolTermStructure> localVol,
                            Handle<HestonModel> hestonModel,
                            BrownianGeneratorFactory brownianGeneratorFactory,
                            final Date endDate,
                            int timeStepsPerYear,
                            int nBins,
                            int calibrationPaths,
                            List<Date> mandatoryDates) {
        this(localVol, hestonModel, brownianGeneratorFactory, endDate,
                timeStepsPerYear, nBins, calibrationPaths, mandatoryDates, 1.0);
    }

    public HestonSLVMCModel(Handle<LocalVolTermStructure> localVol,
                            Handle<HestonModel> hestonModel,
                            BrownianGeneratorFactory brownianGeneratorFactory,
                            final Date endDate,
                            int timeStepsPerYear,
                            int nBins,
                            int calibrationPaths,
                            List<Date> mandatoryDates,
                            double mixingFactor) {
        localVol_ = localVol;
        hestonModel_ = hestonModel;
        brownianGeneratorFactory_ = brownianGeneratorFactory;
        endDate_ = endDate;
        nBins_ = nBins;
        calibrationPaths_ = calibrationPaths;
        mixingFactor_ = mixingFactor;

        registerWith(localVol_.get());
        registerWith(hestonModel_.get());

        final DayCounter dc = hestonModel_.currentLink().process()
                .riskFreeRate().currentLink().dayCounter();
        final Date refDate = hestonModel_.currentLink().process()
                .riskFreeRate().currentLink().referenceDate();

        final List<Double> gridTimes = new ArrayList<>(mandatoryDates.size() + 1);
        for (Date mandatoryDate : mandatoryDates) {
            gridTimes.add(dc.yearFraction(refDate, mandatoryDate));
        }
        gridTimes.add(dc.yearFraction(refDate, endDate));

        timeGrid_ = new TimeGrid(gridTimes,
                Math.max(2, (int) (gridTimes.get(gridTimes.size() - 1)
                        * timeStepsPerYear)));
    }

    public HestonProcess hestonProcess() {
        return hestonModel_.currentLink().process();
    }

    public LocalVolTermStructure localVol() {
        return localVol_.currentLink();
    }

    public LocalVolTermStructure leverageFunction() {
        calculate();

        return leverageFunction_;
    }

    @Override
    protected void performCalculations() {
        final HestonProcess hestonProcess = hestonModel_.currentLink().process();
        final Quote spot = hestonProcess.s0().currentLink();

        final double v0 = hestonProcess.v0();
        final DayCounter dc = hestonProcess.riskFreeRate().currentLink().dayCounter();
        final Date referenceDate = hestonProcess.riskFreeRate().currentLink()
                .referenceDate();

        final double lv0 = localVol_.currentLink()
                .localVol(0.0, spot.value(), false) / Math.sqrt(v0);

        final Matrix L = new Matrix(nBins_, timeGrid_.size(), 0.0);

        final List<List<Double>> vStrikes =
                new ArrayList<>(timeGrid_.size());
        for (int i = 0; i < timeGrid_.size(); ++i) {
            final int u = nBins_ / 2;
            final double dx = spot.value() * Math.sqrt(QL_EPSILON);

            final List<Double> strikes = new ArrayList<>(nBins_);

            for (int j = 0; j < nBins_; ++j)
                strikes.add(spot.value() + (j - u) * dx);

            vStrikes.add(strikes);
        }

        for (int j = 0; j < nBins_; ++j)
            L.set(j, 0, lv0);

        final double[] timesArray = new double[timeGrid_.size()];
        for (int i = 0; i < timeGrid_.size(); ++i)
            timesArray[i] = timeGrid_.at(i);
        final double[][] strikesArray = new double[vStrikes.size()][];
        for (int i = 0; i < vStrikes.size(); ++i) {
            final List<Double> strikes = vStrikes.get(i);
            strikesArray[i] = new double[strikes.size()];
            for (int j = 0; j < strikes.size(); ++j)
                strikesArray[i][j] = strikes.get(j);
        }
        leverageFunction_ = new FixedLocalVolSurface(
                referenceDate, timesArray, strikesArray, L, dc);

        final HestonSLVProcess slvProcess = new HestonSLVProcess(
                hestonProcess, leverageFunction_, mixingFactor_);

        final List<double[]> pairs = new ArrayList<>(calibrationPaths_);
        for (int i = 0; i < calibrationPaths_; ++i)
            pairs.add(new double[]{spot.value(), v0});

        final int k = calibrationPaths_ / nBins_;
        final int m = calibrationPaths_ % nBins_;

        final int timeSteps = timeGrid_.size() - 1;

        final double[][][] paths =
                new double[calibrationPaths_][timeSteps][2];

        final BrownianGenerator brownianGenerator =
                brownianGeneratorFactory_.create(2, timeSteps);

        for (int i = 0; i < calibrationPaths_; ++i) {
            brownianGenerator.nextPath();
            final List<Double> tmp = new ArrayList<>(2);
            tmp.add(0.0);
            tmp.add(0.0);
            for (int j = 0; j < timeSteps; ++j) {
                brownianGenerator.nextStep(tmp);
                paths[i][j][0] = tmp.get(0);
                paths[i][j][1] = tmp.get(1);
            }
        }

        for (int n = 1; n < timeGrid_.size(); ++n) {
            final double t0 = timeGrid_.at(n - 1);
            final double t1 = timeGrid_.at(n);
            final double dt = timeGrid_.dt(n - 1);

            final Array x0 = new Array(2);
            final Array dw = new Array(2);

            for (int i = 0; i < calibrationPaths_; ++i) {
                x0.set(0, pairs.get(i)[0]);
                x0.set(1, pairs.get(i)[1]);

                dw.set(0, paths[i][n - 1][0]);
                dw.set(1, paths[i][n - 1][1]);

                final Array x0New = slvProcess.evolve(t0, x0, dt, dw);

                pairs.get(i)[0] = x0New.get(0);
                pairs.get(i)[1] = x0New.get(1);
            }

            Collections.sort(pairs, new Comparator<double[]>() {
                @Override
                public int compare(double[] a, double[] b) {
                    final int c = Double.compare(a[0], b[0]);
                    return (c != 0) ? c : Double.compare(a[1], b[1]);
                }
            });

            int s = 0, e = 0;
            for (int i = 0; i < nBins_; ++i) {
                final int inc = k + (i < m ? 1 : 0);
                e = s + inc;

                double sum = 0.0;
                for (int j = s; j < e; ++j) {
                    sum += pairs.get(j)[1];
                }
                sum /= inc;

                final double strike = 0.5 * (pairs.get(e - 1)[0] + pairs.get(s)[0]);
                vStrikes.get(n).set(i, strike);
                // The conditional variance and leverage slice are both at the
                // end of the step.
                L.set(i, n, Math.sqrt(squared(
                        localVol_.currentLink().localVol(t1, strike, true)) / sum));

                s = e;
            }

            // Official code updates the shared strike vector in place; the
            // Java surface owns a copy, so push the slice before rebuilding.
            leverageFunction_.setStrikes(n, vStrikes.get(n));
            leverageFunction_.setInterpolation(new Linear());
        }

        // Official slvProcess is a function-local shared_ptr; the C++ dtor
        // unregisters it from the Heston process and leverage surface at the
        // end of performCalculations. Java has no destructor.
        slvProcess.unregisterWithAll();
    }

    private static double squared(double x) {
        return x * x;
    }
}
