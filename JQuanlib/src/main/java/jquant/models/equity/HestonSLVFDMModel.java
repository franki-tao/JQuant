package jquant.models.equity;

import jquant.Handle;
import jquant.Quote;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.integrals.DiscreteSimpsonIntegral;
import jquant.math.interpolations.BilinearInterpolation;
import jquant.math.interpolations.Interpolation2D;
import jquant.methods.finitedifferences.meshers.impl.Tuple;
import jquant.methods.finitedifferences.meshers.Concentrating1dMesher;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.meshers.Predefined1dMesher;
import jquant.methods.finitedifferences.operators.FdmHestonFwdOp;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.operators.FdmSquareRootFwdOp;
import jquant.methods.finitedifferences.schemes.CraigSneydScheme;
import jquant.methods.finitedifferences.schemes.DouglasScheme;
import jquant.methods.finitedifferences.schemes.ExplicitEulerScheme;
import jquant.methods.finitedifferences.schemes.FdmScheme;
import jquant.methods.finitedifferences.schemes.HundsdorferScheme;
import jquant.methods.finitedifferences.schemes.ImplicitEulerScheme;
import jquant.methods.finitedifferences.schemes.ModifiedCraigSneydScheme;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmHestonGreensFct;
import jquant.methods.finitedifferences.utilities.FdmMesherIntegral;
import jquant.methods.finitedifferences.utilities.LocalVolRNDCalculator;
import jquant.methods.finitedifferences.utilities.SquareRootProcessRNDCalculator;
import jquant.patterns.LazyObject;
import jquant.processes.HestonProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.FixedLocalVolSurface;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.CommonUtil.toArray;

/**
 * Heston stochastic local volatility model, calibrated by a Fokker-Planck
 * forward equation.
 *
 * <p>Port of {@code ql/models/equity/hestonslvfdmmodel.{hpp,cpp}}.
 */
public class HestonSLVFDMModel extends LazyObject {

    private final Handle<LocalVolTermStructure> localVol_;
    private final Handle<HestonModel> hestonModel_;
    private final Date endDate_;
    private final HestonSLVFokkerPlanckFdmParams params_;
    private final List<Date> mandatoryDates_;
    private final double mixingFactor_;

    private LocalVolTermStructure leverageFunction_;

    private final boolean logging_;
    private final List<LogEntry> logEntries_ = new ArrayList<>();

    public static class LogEntry {
        public final double t;
        public final Array prob;
        public final FdmMesherComposite mesher;

        public LogEntry(double t, Array prob, FdmMesherComposite mesher) {
            this.t = t;
            this.prob = prob;
            this.mesher = mesher;
        }
    }

    public HestonSLVFDMModel(Handle<LocalVolTermStructure> localVol,
                             Handle<HestonModel> hestonModel,
                             final Date endDate,
                             HestonSLVFokkerPlanckFdmParams params) {
        this(localVol, hestonModel, endDate, params, false,
                new ArrayList<>(), 1.0);
    }

    public HestonSLVFDMModel(Handle<LocalVolTermStructure> localVol,
                             Handle<HestonModel> hestonModel,
                             final Date endDate,
                             HestonSLVFokkerPlanckFdmParams params,
                             boolean logging) {
        this(localVol, hestonModel, endDate, params, logging,
                new ArrayList<>(), 1.0);
    }

    public HestonSLVFDMModel(Handle<LocalVolTermStructure> localVol,
                             Handle<HestonModel> hestonModel,
                             final Date endDate,
                             HestonSLVFokkerPlanckFdmParams params,
                             boolean logging,
                             List<Date> mandatoryDates) {
        this(localVol, hestonModel, endDate, params, logging,
                mandatoryDates, 1.0);
    }

    public HestonSLVFDMModel(Handle<LocalVolTermStructure> localVol,
                             Handle<HestonModel> hestonModel,
                             final Date endDate,
                             HestonSLVFokkerPlanckFdmParams params,
                             boolean logging,
                             List<Date> mandatoryDates,
                             double mixingFactor) {
        localVol_ = localVol;
        hestonModel_ = hestonModel;
        endDate_ = endDate;
        params_ = params;
        mandatoryDates_ = mandatoryDates;
        mixingFactor_ = mixingFactor;
        logging_ = logging;

        registerWith(localVol_.get());
        registerWith(hestonModel_.get());
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

    public List<LogEntry> logEntries() {
        calculate();
        return logEntries_;
    }

    @Override
    protected void performCalculations() {
        logEntries_.clear();

        final HestonProcess hestonProcess = hestonModel_.currentLink().process();
        final Quote spot = hestonProcess.s0().currentLink();
        final YieldTermStructure rTS = hestonProcess.riskFreeRate().currentLink();
        final YieldTermStructure qTS = hestonProcess.dividendYield().currentLink();

        final double v0 = hestonProcess.v0();
        final double kappa = hestonProcess.kappa();
        final double theta = hestonProcess.theta();
        final double sigma = hestonProcess.sigma();
        final double mixedSigma = mixingFactor_ * sigma;
        final double alpha = 2 * kappa * theta / (mixedSigma * mixedSigma);

        final int xGrid = params_.xGrid;
        final int vGrid = params_.vGrid;

        final DayCounter dc = rTS.dayCounter();
        final Date referenceDate = rTS.referenceDate();

        final double T = dc.yearFraction(referenceDate, endDate_);

        QL_REQUIRE(TimeUtils.less(referenceDate, endDate_),
                "reference date must be smaller than final calibration date");

        QL_REQUIRE(localVol_.currentLink().maxTime() >= T,
                "final calibration maturity exceeds local volatility surface");

        // set-up exponential time step scheme
        final double maxDt = 1.0 / params_.tMaxStepsPerYear;
        final double minDt = 1.0 / params_.tMinStepsPerYear;

        double tIdx = 0.0;
        final List<Double> times = new ArrayList<>();
        times.add(tIdx);
        while (tIdx < T) {
            final double decayFactor = Math.exp(-params_.tStepNumberDecay * tIdx);
            final double dt = maxDt * decayFactor + minDt * (1.0 - decayFactor);

            tIdx = Math.min(T, tIdx + dt);
            times.add(tIdx);
        }

        for (Date mandatoryDate : mandatoryDates_) {
            times.add(dc.yearFraction(referenceDate, mandatoryDate));
        }

        final TimeGrid timeGrid = new TimeGrid(times);

        // build 1d meshers
        final LocalVolRNDCalculator localVolRND = new LocalVolRNDCalculator(
                spot, rTS, qTS, localVol_.currentLink(),
                timeGrid, xGrid,
                params_.x0Density,
                params_.localVolEpsProb,
                params_.maxIntegrationIterations);

        final List<Integer> rescaleSteps = localVolRND.rescaleTimeSteps();

        final SquareRootProcessRNDCalculator squareRootRnd =
                new SquareRootProcessRNDCalculator(v0, kappa, theta, mixedSigma);

        final FdmSquareRootFwdOp.TransformationType trafoType = params_.trafoType;

        final List<Fdm1dMesher> xMesher = new ArrayList<>(timeGrid.size());
        final List<Fdm1dMesher> vMesher = new ArrayList<>(timeGrid.size());

        xMesher.add(localVolRND.mesher(0.0));
        {
            final List<Double> v0Locations = new ArrayList<>(vGrid);
            for (int i = 0; i < vGrid; ++i)
                v0Locations.add(v0);
            vMesher.add(new Predefined1dMesher(v0Locations));
        }

        int rescaleIdx = 0;
        for (int i = 1; i < timeGrid.size(); ++i) {
            xMesher.add(localVolRND.mesher(timeGrid.at(i)));

            if ((rescaleIdx < rescaleSteps.size())
                    && (i == rescaleSteps.get(rescaleIdx))) {
                ++rescaleIdx;
                vMesher.add(varianceMesher(squareRootRnd,
                        timeGrid.at(rescaleSteps.get(rescaleIdx - 1)),
                        (rescaleIdx < rescaleSteps.size())
                                ? timeGrid.at(rescaleSteps.get(rescaleIdx))
                                : timeGrid.back(),
                        vGrid, v0, params_));
            } else
                vMesher.add(vMesher.get(vMesher.size() - 1));
        }

        // start probability distribution
        FdmMesherComposite mesher = new FdmMesherComposite(
                Arrays.asList(xMesher.get(1), vMesher.get(1)));

        final double lv0 = localVol_.currentLink().localVol(0.0, spot.value(), false)
                / Math.sqrt(v0);

        final Matrix L = new Matrix(xGrid, timeGrid.size(), 0.0);

        final double l0 = lv0;
        for (int j = 0; j < xGrid; ++j) {
            L.set(j, 0, l0);
            L.set(j, 1, l0);
        }

        // create strikes from meshers
        final List<List<Double>> vStrikes = new ArrayList<>(timeGrid.size());

        for (int i = 0; i < timeGrid.size(); ++i) {
            final List<Double> strikes = new ArrayList<>(xGrid);
            final List<Double> locations = xMesher.get(i).locations();
            if (locations.get(0).doubleValue()
                    == locations.get(locations.size() - 1).doubleValue()) {
                for (int j = 0; j < xGrid; ++j)
                    strikes.add(Math.exp(locations.get(0)));
            } else {
                for (double x : locations)
                    strikes.add(Math.exp(x));
            }
            vStrikes.add(strikes);
        }

        final double[] timesArray = new double[times.size()];
        for (int i = 0; i < times.size(); ++i)
            timesArray[i] = times.get(i);
        final double[][] strikesArray = new double[vStrikes.size()][];
        for (int i = 0; i < vStrikes.size(); ++i) {
            final List<Double> strikes = vStrikes.get(i);
            strikesArray[i] = new double[strikes.size()];
            for (int j = 0; j < strikes.size(); ++j)
                strikesArray[i][j] = strikes.get(j);
        }
        final FixedLocalVolSurface leverageFct = new FixedLocalVolSurface(
                referenceDate, timesArray, strikesArray, L, dc);

        FdmHestonFwdOp hestonFwdOp = new FdmHestonFwdOp(
                mesher, hestonProcess, trafoType, leverageFct, mixingFactor_);

        Array p = new FdmHestonGreensFct(mesher, hestonProcess, trafoType, lv0)
                .get(timeGrid.at(1), params_.greensAlgorithm);

        if (logging_) {
            logEntries_.add(new LogEntry(timeGrid.at(1), new Array(p), mesher));
        }

        for (int i = 2; i < times.size(); ++i) {
            final double t = timeGrid.at(i);
            final double dt = t - timeGrid.at(i - 1);

            if (mesher.getFdm1dMeshers().get(0) != xMesher.get(i)
                    || mesher.getFdm1dMeshers().get(1) != vMesher.get(i)) {
                final FdmMesherComposite newMesher =
                        new FdmMesherComposite(
                                Arrays.asList(xMesher.get(i), vMesher.get(i)));

                p = reshapePDF(p, mesher, newMesher);
                mesher = newMesher;

                p = rescalePDF(p, mesher, trafoType, alpha);

                hestonFwdOp = new FdmHestonFwdOp(
                        mesher, hestonProcess, trafoType, leverageFct, mixingFactor_);
            }

            Array pn = new Array(p);
            final Array x = exp(new Array(toArray(
                    mesher.getFdm1dMeshers().get(0).locations())));
            final Array v = new Array(toArray(
                    mesher.getFdm1dMeshers().get(1).locations()));

            // predictor corrector steps
            for (int r = 0; r < params_.predictionCorretionSteps; ++r) {
                final FdmSchemeDesc fdmSchemeDesc
                        = (i < params_.nRannacherTimeSteps + 2)
                        ? FdmSchemeDesc.ImplicitEuler()
                        : params_.schemeDesc;

                final FdmScheme fdmScheme = fdmSchemeFactory(fdmSchemeDesc, hestonFwdOp);

                for (int j = 0; j < x.size(); ++j) {
                    final Array pSlice = new Array(vGrid);
                    for (int k = 0; k < vGrid; ++k)
                        pSlice.set(k, pn.get(j + k * xGrid));

                    final double pInt = (trafoType == FdmSquareRootFwdOp.TransformationType.Power)
                            ? new DiscreteSimpsonIntegral().value(v.getList(),
                                    pow(v, alpha - 1).multiply(pSlice).getList())
                            : new DiscreteSimpsonIntegral().value(v.getList(),
                                    pSlice.getList());

                    final double vpInt = (trafoType == FdmSquareRootFwdOp.TransformationType.Log)
                            ? new DiscreteSimpsonIntegral().value(v.getList(),
                                    exp(v).multiply(pSlice).getList())
                            : (trafoType == FdmSquareRootFwdOp.TransformationType.Power)
                            ? new DiscreteSimpsonIntegral().value(v.getList(),
                                    pow(v, alpha).multiply(pSlice).getList())
                            : new DiscreteSimpsonIntegral().value(v.getList(),
                                    v.multiply(pSlice).getList());

                    final double scale = pInt / vpInt;
                    final double localVol = localVol_.currentLink()
                            .localVol(t, x.get(j), false);

                    final double l = (scale >= 0.0)
                            ? localVol * Math.sqrt(scale) : 1.0;

                    L.set(j, i, Math.min(50.0, Math.max(0.001, l)));

                    leverageFct.setInterpolation();
                }

                final double sLowerBound = Math.max(x.front(),
                        Math.exp(localVolRND.invcdf(
                                params_.leverageFctPropEps, t)));
                final double sUpperBound = Math.min(x.back(),
                        Math.exp(localVolRND.invcdf(
                                1.0 - params_.leverageFctPropEps, t)));

                final double lowerL = leverageFct.localVol(t, sLowerBound, false);
                final double upperL = leverageFct.localVol(t, sUpperBound, false);

                for (int j = 0; j < x.size(); ++j) {
                    if (x.get(j) < sLowerBound)
                        L.set(j, i, lowerL);
                    else if (x.get(j) > sUpperBound)
                        L.set(j, i, upperL);
                    else if (Double.isNaN(L.get(j, i)))
                        QL_FAIL("internal error");
                }
                leverageFct.setInterpolation();

                pn = new Array(p);

                fdmScheme.setStep(dt);
                pn = fdmScheme.step(pn, t);
            }
            p = pn;
            p = rescalePDF(p, mesher, trafoType, alpha);

            if (logging_) {
                logEntries_.add(new LogEntry(t, new Array(p), mesher));
            }
        }

        leverageFunction_ = leverageFct;

        // Official localVolRND is a function-local; the C++ Observer dtor
        // unregisters it from the spot quote and term structures at the end
        // of performCalculations. Java has no destructor.
        localVolRND.unregisterWithAll();
    }

    private static Fdm1dMesher varianceMesher(
            final SquareRootProcessRNDCalculator rnd,
            double t0, double t1, int vGrid,
            double v0, final HestonSLVFokkerPlanckFdmParams params) {

        final List<Tuple> cPoints = new ArrayList<>();

        final double v0Density = params.v0Density;
        final double upperBoundDensity = params.vUpperBoundDensity;
        final double lowerBoundDensity = params.vLowerBoundDensity;

        double lowerBound = Double.POSITIVE_INFINITY;
        double upperBound = Double.NEGATIVE_INFINITY;

        for (int i = 0; i <= 10; ++i) {
            final double t = t0 + i / 10.0 * (t1 - t0);
            lowerBound = Math.min(lowerBound, rnd.invcdf(params.vLowerEps, t));
            upperBound = Math.max(upperBound,
                    rnd.invcdf(1.0 - params.vUpperEps, t));
        }

        lowerBound = Math.max(lowerBound, params.vMin);
        switch (params.trafoType) {
            case Log: {
                lowerBound = Math.log(lowerBound);
                upperBound = Math.log(upperBound);

                final double v0Center = Math.log(v0);

                cPoints.add(new Tuple(lowerBound, lowerBoundDensity, false));
                cPoints.add(new Tuple(v0Center, v0Density, true));
                cPoints.add(new Tuple(upperBound, upperBoundDensity, false));

                return new Concentrating1dMesher(
                        lowerBound, upperBound, vGrid, cPoints, 1e-8);
            }
            case Plain: {
                final double v0Center = v0;

                cPoints.add(new Tuple(lowerBound, lowerBoundDensity, false));
                cPoints.add(new Tuple(v0Center, v0Density, true));
                cPoints.add(new Tuple(upperBound, upperBoundDensity, false));

                return new Concentrating1dMesher(
                        lowerBound, upperBound, vGrid, cPoints, 1e-8);
            }
            case Power: {
                final double v0Center = v0;

                cPoints.add(new Tuple(lowerBound, lowerBoundDensity, false));
                cPoints.add(new Tuple(v0Center, v0Density, true));
                cPoints.add(new Tuple(upperBound, upperBoundDensity, false));

                return new Concentrating1dMesher(
                        lowerBound, upperBound, vGrid, cPoints, 1e-8);
            }
            default:
                QL_FAIL("transformation type is not implemented");
                return null;
        }
    }

    private static double integratePDF(
            final Array p,
            final FdmMesherComposite mesher,
            FdmSquareRootFwdOp.TransformationType trafoType,
            double alpha) {

        if (trafoType != FdmSquareRootFwdOp.TransformationType.Power) {
            return new FdmMesherIntegral(
                    mesher, new DiscreteSimpsonIntegral()).integrate(p);
        } else {
            final Array tp = new Array(p.size());
            for (FdmLinearOpIterator iter : mesher.layout()) {
                final int idx = iter.index();
                final double nu = mesher.location(iter, 1);

                tp.set(idx, p.get(idx) * Math.pow(nu, alpha - 1));
            }

            return new FdmMesherIntegral(
                    mesher, new DiscreteSimpsonIntegral()).integrate(tp);
        }
    }

    private static Array rescalePDF(
            final Array p,
            final FdmMesherComposite mesher,
            FdmSquareRootFwdOp.TransformationType trafoType,
            double alpha) {

        return p.div(integratePDF(p, mesher, trafoType, alpha));
    }

    private static Array reshapePDF(
            final Array p,
            final FdmMesherComposite oldMesher,
            final FdmMesherComposite newMesher) {

        QL_REQUIRE(oldMesher.layout().size() == newMesher.layout().size()
                        && oldMesher.layout().size() == p.size(),
                "inconsistent mesher or vector size given");

        final int dim0 = oldMesher.layout().dim()[0];
        final int dim1 = oldMesher.layout().dim()[1];

        final double[][] m = new double[dim1][dim0];
        for (int i = 0; i < dim1; ++i) {
            for (int j = 0; j < dim0; ++j)
                m[i][j] = p.get(j + i * dim0);
        }

        final List<Fdm1dMesher> old1d = oldMesher.getFdm1dMeshers();
        final BilinearInterpolation interpol = new BilinearInterpolation(
                toArray(old1d.get(0).locations()),
                toArray(old1d.get(1).locations()), m);

        final Array pNew = new Array(p.size());
        for (FdmLinearOpIterator iter : newMesher.layout()) {
            final double x = newMesher.location(iter, 0);
            final double v = newMesher.location(iter, 1);

            if (x > interpol.xMax() || x < interpol.xMin()
                    || v > interpol.yMax() || v < interpol.yMin()) {
                pNew.set(iter.index(), 0.0);
            } else {
                pNew.set(iter.index(), interpol.value(x, v, false));
            }
        }

        return pNew;
    }

    private static FdmScheme fdmSchemeFactory(
            final FdmSchemeDesc desc,
            final FdmHestonFwdOp op) {

        final FdmBoundaryConditionSet bcSet = new FdmBoundaryConditionSet();
        final FdmSchemeDesc.FdmSchemeType type = desc.type;
        if (type == FdmSchemeDesc.FdmSchemeType.HundsdorferType)
            return new HundsdorferScheme(desc.theta, desc.mu, op, bcSet);
        else if (type == FdmSchemeDesc.FdmSchemeType.DouglasType)
            return new DouglasScheme(desc.theta, op, bcSet);
        else if (type == FdmSchemeDesc.FdmSchemeType.CraigSneydType)
            return new CraigSneydScheme(desc.theta, desc.mu, op, bcSet);
        else if (type == FdmSchemeDesc.FdmSchemeType.ModifiedCraigSneydType)
            return new ModifiedCraigSneydScheme(desc.theta, desc.mu, op, bcSet);
        else if (type == FdmSchemeDesc.FdmSchemeType.ImplicitEulerType)
            return new ImplicitEulerScheme(op, bcSet);
        else if (type == FdmSchemeDesc.FdmSchemeType.ExplicitEulerType)
            return new ExplicitEulerScheme(op, bcSet);
        else {
            QL_FAIL("Unknown scheme type");
            return null;
        }
    }

    private static Array exp(Array a) {
        final Array retVal = new Array(a.size());
        for (int i = 0; i < a.size(); ++i)
            retVal.set(i, Math.exp(a.get(i)));
        return retVal;
    }

    private static Array pow(Array a, double p) {
        final Array retVal = new Array(a.size());
        for (int i = 0; i < a.size(); ++i)
            retVal.set(i, Math.pow(a.get(i), p));
        return retVal;
    }
}
