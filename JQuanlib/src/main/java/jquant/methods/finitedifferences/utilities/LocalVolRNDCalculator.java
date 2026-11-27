package jquant.methods.finitedifferences.utilities;

import jquant.Quote;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.Point;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.distributions.NormalDistribution;
import jquant.math.integrals.DiscreteSimpsonIntegral;
import jquant.math.integrals.GaussLobattoIntegral;
import jquant.math.interpolations.CubicNaturalSpline;
import jquant.math.Interpolation;
import jquant.methods.finitedifferences.meshers.Concentrating1dMesher;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.meshers.Predefined1dMesher;
import jquant.methods.finitedifferences.operators.FdmLocalVolFwdOp;
import jquant.methods.finitedifferences.schemes.DouglasScheme;
import jquant.patterns.LazyObject;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.CommonUtil.toArray;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Local volatility risk neutral terminal density calculation.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/localvolrndcalculator.{hpp,cpp}}.
 */
public class LocalVolRNDCalculator extends LazyObject
        implements RiskNeutralDensityCalculator {

    private final int xGrid_;
    private final int tGrid_;
    private final double x0Density_;
    private final double localVolProbEps_;
    private final int maxIter_;
    private final double gaussianStepSize_;
    private final Quote spot_;
    private final LocalVolTermStructure localVol_;
    private final YieldTermStructure rTS_;
    private final YieldTermStructure qTS_;
    private final TimeGrid timeGrid_;
    private Fdm1dMesher[] xm_;
    private final Matrix pm_;
    private List<Integer> rescaleTimeSteps_;
    private Interpolation[] pFct_;

    public LocalVolRNDCalculator(final Quote spot,
                                 final YieldTermStructure rTS,
                                 final YieldTermStructure qTS,
                                 final LocalVolTermStructure localVol) {
        this(spot, rTS, qTS, localVol, 101, 51, 0.1, 1e-6, 10000, Double.NaN);
    }

    public LocalVolRNDCalculator(final Quote spot,
                                 final YieldTermStructure rTS,
                                 final YieldTermStructure qTS,
                                 final LocalVolTermStructure localVol,
                                 int xGrid,
                                 int tGrid,
                                 double x0Density,
                                 double eps,
                                 int maxIter,
                                 double gaussianStepSize) {
        xGrid_ = xGrid;
        tGrid_ = tGrid;
        x0Density_ = x0Density;
        localVolProbEps_ = eps;
        maxIter_ = maxIter;
        gaussianStepSize_ = gaussianStepSize;
        spot_ = spot;
        localVol_ = localVol;
        rTS_ = rTS;
        qTS_ = qTS;
        timeGrid_ = new TimeGrid(localVol.maxTime(), tGrid);
        xm_ = new Fdm1dMesher[tGrid];
        pm_ = new Matrix(tGrid, xGrid, 0.0);

        registerWith(spot_);
        registerWith(rTS_);
        registerWith(qTS_);
        registerWith(localVol_);
    }

    public LocalVolRNDCalculator(final Quote spot,
                                 final YieldTermStructure rTS,
                                 final YieldTermStructure qTS,
                                 final LocalVolTermStructure localVol,
                                 final TimeGrid timeGrid,
                                 int xGrid,
                                 double x0Density,
                                 double eps,
                                 int maxIter) {
        this(spot, rTS, qTS, localVol, timeGrid, xGrid, x0Density, eps, maxIter,
                Double.NaN);
    }

    public LocalVolRNDCalculator(final Quote spot,
                                 final YieldTermStructure rTS,
                                 final YieldTermStructure qTS,
                                 final LocalVolTermStructure localVol,
                                 final TimeGrid timeGrid,
                                 int xGrid,
                                 double x0Density,
                                 double eps,
                                 int maxIter,
                                 double gaussianStepSize) {
        xGrid_ = xGrid;
        tGrid_ = timeGrid.size() - 1;
        x0Density_ = x0Density;
        localVolProbEps_ = eps;
        maxIter_ = maxIter;
        gaussianStepSize_ = gaussianStepSize;
        spot_ = spot;
        localVol_ = localVol;
        rTS_ = rTS;
        qTS_ = qTS;
        timeGrid_ = timeGrid;
        xm_ = new Fdm1dMesher[tGrid_];
        pm_ = new Matrix(tGrid_, xGrid_, 0.0);

        registerWith(spot_);
        registerWith(rTS_);
        registerWith(qTS_);
        registerWith(localVol_);
    }

    @Override
    public double pdf(double x, double t) {
        calculate();

        QL_REQUIRE(t > 0, "positive time expected");
        QL_REQUIRE(t <= timeGrid_.back(),
                "given time exceeds local vol time grid");

        final double tMin = Math.min(timeGrid_.at(1), 1.0 / 365);

        if (t <= tMin) {
            final double vol = localVol_.localVol(0.0, spot_.value(), false);
            final double stdDev = vol * Math.sqrt(t);
            final double xm = -0.5 * stdDev * stdDev
                    + Math.log(spot_.value() * qTS_.discount(t) / rTS_.discount(t));

            return new NormalDistribution(xm, stdDev).value(x);
        } else if (t <= timeGrid_.at(1)) {
            final double vol = localVol_.localVol(0.0, spot_.value(), false);
            final double stdDev = vol * Math.sqrt(tMin);
            final double xm = -0.5 * stdDev * stdDev
                    + Math.log(spot_.value() * qTS_.discount(tMin)
                    / rTS_.discount(tMin));

            final NormalDistribution gaussianPDF = new NormalDistribution(xm, stdDev);

            final double deltaT = timeGrid_.at(1) - tMin;
            return gaussianPDF.value(x) * (timeGrid_.at(1) - t) / deltaT
                    + probabilityInterpolation(0, x) * (t - tMin) / deltaT;
        } else {
            final int lb = lowerBound(timeGrid_, t);
            final int llb = lb - 1;

            final int idx = lb - 1;

            final double deltaT = timeGrid_.at(lb) - timeGrid_.at(llb);
            return probabilityInterpolation(idx - 1, x)
                    * (timeGrid_.at(lb) - t) / deltaT
                    + probabilityInterpolation(idx, x)
                    * (t - timeGrid_.at(llb)) / deltaT;
        }
    }

    @Override
    public double cdf(double x, double t) {
        calculate();

        // get the left side of the integral
        final double tc = timeGrid_.closestTime(t);
        final int idx = (tc > t) ? timeGrid_.index(tc) - 1
                : Math.min(xm_.length - 1, timeGrid_.index(tc));

        double xl = xm_[idx].locations().get(0);
        double xr = xm_[idx].locations().get(xm_[idx].size() - 1);

        if (x < xl)
            return 0.0;
        else if (x > xr)
            return 1.0;

        double addition = 0.1 * (xr - xl);

        // left or right hand integral
        if (x > 0.5 * (xr + xl)) {
            while (pdf(xr, t) > 0.01 * localVolProbEps_) {
                addition *= 1.1;
                xr += addition;
            }

            return 1.0 - new GaussLobattoIntegral(maxIter_,
                    0.1 * localVolProbEps_).value(
                    _x -> pdf(_x, t), x, xr);
        } else {
            while (pdf(xl, t) > 0.01 * localVolProbEps_) {
                addition *= 1.1;
                xl -= addition;
            }

            return new GaussLobattoIntegral(maxIter_,
                    0.1 * localVolProbEps_).value(
                    _x -> pdf(_x, t), xl, x);
        }
    }

    @Override
    public double invcdf(double p, double t) {
        calculate();

        final double closeGridTime = timeGrid_.closestTime(t);

        if (closeGridTime == 0.0) {
            final double stepSize = 0.02 * (
                    xm_[0].locations().get(xm_[0].size() - 1)
                            - xm_[0].locations().get(0));
            return new RiskNeutralDensityCalculator.InvCDFHelper(
                    this, Math.log(spot_.value()),
                    0.1 * localVolProbEps_, maxIter_, stepSize).inverseCDF(p, t);
        } else {
            final Array xp = new Array(xGrid_);
            final int idx = timeGrid_.index(closeGridTime) - 1;

            final Array x = new Array(toArray(xm_[idx].locations()));
            final double stepSize = 0.005 * (x.back() - x.front());

            final Array pmRow = pm_.getRowArray(idx);
            for (int i = 0; i < x.size(); ++i) {
                xp.set(i, x.get(i) * pmRow.get(i));
            }

            final double xm = new DiscreteSimpsonIntegral()
                    .value(x.getList(), xp.getList());
            return new RiskNeutralDensityCalculator.InvCDFHelper(
                    this, xm, 0.1 * localVolProbEps_, maxIter_, stepSize)
                    .inverseCDF(p, t);
        }
    }

    public Fdm1dMesher mesher(double t) {
        calculate();

        final int idx = timeGrid_.index(t);
        QL_REQUIRE(idx <= xm_.length, "inconsistent time " + t + " given");

        if (idx > 0) {
            return xm_[idx - 1];
        } else {
            final List<Double> locations = new ArrayList<>(xGrid_);
            for (int i = 0; i < xGrid_; ++i)
                locations.add(Math.log(spot_.value()));
            return new Predefined1dMesher(locations);
        }
    }

    public TimeGrid timeGrid() {
        return timeGrid_;
    }

    public List<Integer> rescaleTimeSteps() {
        calculate();

        return rescaleTimeSteps_;
    }

    @Override
    protected void performCalculations() {
        rescaleTimeSteps_ = new ArrayList<>();

        final double sT = timeGrid_.at(1);
        double t = Math.min(sT, (gaussianStepSize_ > 0.0)
                ? gaussianStepSize_ : 0.5 * sT);
        final double vol = localVol_.localVol(0.0, spot_.value(), false);

        final double stdDev = vol * Math.sqrt(t);
        double xm = -0.5 * stdDev * stdDev
                + Math.log(spot_.value() * qTS_.discount(t) / rTS_.discount(t));

        final double stdDevOfFirstStep = vol * Math.sqrt(sT);
        final double normInvEps = new InverseCumulativeNormal()
                .value(1 - localVolProbEps_);

        double sLowerBound = xm - normInvEps * stdDevOfFirstStep;
        double sUpperBound = xm + normInvEps * stdDevOfFirstStep;

        Fdm1dMesher mesher = new Concentrating1dMesher(
                sLowerBound, sUpperBound, xGrid_,
                new Point<>(xm, x0Density_), true);

        Array p = new Array(mesher.size());
        Array x = new Array(toArray(mesher.locations()));

        final NormalDistribution gaussianPDF =
                new NormalDistribution(xm, vol * Math.sqrt(t));

        for (int idx = 0; idx < p.size(); ++idx) {
            p.set(idx, gaussianPDF.value(x.get(idx)));
        }
        p = rescalePDF(x, p);

        QL_REQUIRE(x.size() > 10, "x grid is too small. "
                + "Minimum size is greater than 10");

        final int b = Math.max(1, (int) (x.size() * 0.04));

        DouglasScheme evolver = new DouglasScheme(0.5,
                new FdmLocalVolFwdOp(
                        new FdmMesherComposite(mesher),
                        spot_, rTS_, qTS_, localVol_),
                new FdmBoundaryConditionSet());

        pFct_ = new Interpolation[tGrid_];

        for (int i = 1; i <= tGrid_; ++i) {
            final double dt = timeGrid_.at(i) - t;

            // leaking probability mass?
            double minL = Double.POSITIVE_INFINITY, maxL = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < b; ++j) {
                minL = Math.min(minL, p.get(j));
                maxL = Math.max(maxL, p.get(j));
            }
            final double maxLeftValue = Math.max(Math.abs(minL), Math.abs(maxL));

            double minR = Double.POSITIVE_INFINITY, maxR = Double.NEGATIVE_INFINITY;
            for (int j = p.size() - b; j < p.size(); ++j) {
                minR = Math.min(minR, p.get(j));
                maxR = Math.max(maxR, p.get(j));
            }
            final double maxRightValue = Math.max(Math.abs(minR), Math.abs(maxR));

            if (Math.max(maxLeftValue, maxRightValue) > localVolProbEps_) {
                rescaleTimeSteps_.add(i);

                final double oldLowerBound = sLowerBound;
                final double oldUpperBound = sUpperBound;

                xm = new DiscreteSimpsonIntegral()
                        .value(x.getList(), x.multiply(p).getList());
                final Array vols = new Array(x.size());
                for (int j = 0; j < vols.size(); ++j) {
                    vols.set(j, localVol_.localVol(t + dt, Math.exp(x.get(j)), false));
                }

                final double vm = new DiscreteSimpsonIntegral()
                        .value(x.getList(), vols.getList())
                        / (x.back() - x.front());

                final double scalingFactor = vm * Math.sqrt(0.5 * timeGrid_.back());

                if (maxLeftValue > localVolProbEps_)
                    sLowerBound -= scalingFactor * (oldUpperBound - oldLowerBound);
                if (maxRightValue > localVolProbEps_)
                    sUpperBound += scalingFactor * (oldUpperBound - oldLowerBound);

                mesher = new Concentrating1dMesher(
                        sLowerBound, sUpperBound, xGrid_,
                        new Point<>(xm, 0.1), false);

                final CubicNaturalSpline pSpline =
                        new CubicNaturalSpline(x.toArray(), p.toArray());
                final Array xn = new Array(toArray(mesher.locations()));
                final Array pn = new Array(xn.size(), 0.0);

                for (int j = 0; j < xn.size(); ++j) {
                    if (xn.get(j) >= oldLowerBound && xn.get(j) <= oldUpperBound)
                        pn.set(j, pSpline.value(xn.get(j), false));
                }

                x = xn;
                p = rescalePDF(xn, pn);

                evolver = new DouglasScheme(0.5,
                        new FdmLocalVolFwdOp(
                                new FdmMesherComposite(mesher),
                                spot_, rTS_, qTS_, localVol_),
                        new FdmBoundaryConditionSet());
            }
            evolver.setStep(dt);
            t += dt;

            if (dt > QL_EPSILON) {
                p = evolver.step(p, t);
                p = rescalePDF(x, p);
            }

            xm_[i - 1] = mesher;
            for (int j = 0; j < p.size(); ++j)
                pm_.set(i - 1, j, p.get(j));

            pFct_[i - 1] = new CubicNaturalSpline(
                    toArray(xm_[i - 1].locations()),
                    pm_.getRowArray(i - 1).toArray());
        }
    }

    private double probabilityInterpolation(int idx, double x) {
        calculate();

        final List<Double> locations = xm_[idx].locations();
        if (x < locations.get(0)
                || x > locations.get(locations.size() - 1))
            return 0.0;
        else
            return pFct_[idx].value(x, false);
    }

    private Array rescalePDF(final Array x, final Array p) {
        return p.div(new DiscreteSimpsonIntegral()
                .value(x.getList(), p.getList()));
    }

    private static int lowerBound(TimeGrid grid, double t) {
        int lo = 0, hi = grid.size();
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (grid.at(mid) < t)
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo;
    }
}
