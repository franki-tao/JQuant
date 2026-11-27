package instruments;

import jquant.EuropeanExercise;
import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.TimeGrid;
import jquant.cashflows.IborCoupon;
import jquant.indexes.Euribor6M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.CapFloor;
import jquant.instruments.Settlement;
import jquant.instruments.Swaption;
import jquant.instruments.VanillaSwap;
import jquant.legacy.libormarketmodels.LfmCovarianceProxy;
import jquant.legacy.libormarketmodels.LfmHullWhiteParameterization;
import jquant.legacy.libormarketmodels.LfmSwaptionEngine;
import jquant.legacy.libormarketmodels.LiborForwardModel;
import jquant.legacy.libormarketmodels.LiborForwardModelProcess;
import jquant.legacy.libormarketmodels.LmCorrelationModel;
import jquant.legacy.libormarketmodels.LmExponentialCorrelationModel;
import jquant.legacy.libormarketmodels.LmExtLinearExponentialVolModel;
import jquant.legacy.libormarketmodels.LmFixedVolatilityModel;
import jquant.legacy.libormarketmodels.LmLinearExponentialCorrelationModel;
import jquant.legacy.libormarketmodels.LmLinearExponentialVolatilityModel;
import jquant.legacy.libormarketmodels.LmVolatilityModel;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.PseudoRandom;
import jquant.math.statistics.GeneralStatistics;
import jquant.methods.montecarlo.MultiPathGenerator;
import jquant.methods.montecarlo.SampleMultiPath;
import jquant.models.AffineModel;
import jquant.models.BlackCalibrationHelper;
import jquant.models.CalibrationHelper;
import jquant.models.shortrate.calibrationhelpers.CapHelper;
import jquant.models.shortrate.calibrationhelpers.SwaptionHelper;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.pricingengines.capfloor.AnalyticCapFloorEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.optionlet.CapletVarianceCurve;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.termstructures.yield.ZeroCurve;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/libormarketmodel.cpp} port:
 * {@code testSimpleCovarianceModels}, {@code testCapletPricing},
 * {@code testCalibration} and {@code testSwaptionPricing}. Official comments
 * these as "tests for deprecated classes".
 *
 * <p>Official {@code BOOST_ERROR} records a failure but continues the test
 * case; the messages are accumulated and the test fails at the end. Official
 * {@code BOOST_FAIL} is not used in this file.
 */
public class LiborMarketModelTest {

    private SavedSettings savedSettings;

    private final List<String> boostErrors = new ArrayList<>();

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    // Official file-level makeIndex(dates, rates): Euribor6M, Actual360.
    private IborIndex makeIndex(List<Date> dates, List<Double> rates) {
        DayCounter dayCounter = new Actual360();

        RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();

        IborIndex index = new Euribor6M(termStructure);

        Date todaysDate =
                index.fixingCalendar().adjust(new Date(4, Month.SEPTEMBER, 2005));
        Settings.instance.evaluationDate().equal(todaysDate);

        dates.set(0, index.fixingCalendar().advance(todaysDate,
                index.fixingDays(), TimeUnit.DAYS));

        termStructure.linkTo(new ZeroCurve(dates, rates, dayCounter), true);

        return index;
    }

    private IborIndex makeIndex() {
        List<Date> dates = new ArrayList<>(Arrays.asList(
                new Date(4, Month.SEPTEMBER, 2005),
                new Date(4, Month.SEPTEMBER, 2018)));
        List<Double> rates = Arrays.asList(0.039, 0.041);

        return makeIndex(dates, rates);
    }

    private OptionletVolatilityStructure makeCapVolCurve(final Date todaysDate) {
        double[] vols = {14.40, 17.15, 16.81, 16.64, 16.17,
                15.78, 15.40, 15.21, 14.86};

        List<Date> dates = new ArrayList<>();
        List<Double> capletVols = new ArrayList<>();
        LiborForwardModelProcess process =
                new LiborForwardModelProcess(10, makeIndex());

        for (int i = 0; i < 9; ++i) {
            capletVols.add(vols[i] / 100.0);
            dates.add(process.fixingDates().get(i + 1));
        }

        return new CapletVarianceCurve(todaysDate, dates, capletVols,
                new Actual360());
    }

    @Test
    void testSimpleCovarianceModels() {
        System.out.println("Testing simple covariance models...");

        final int size = 10;
        final double tolerance = 1e-14;

        LmCorrelationModel corrModel =
                new LmExponentialCorrelationModel(size, 0.1);

        Matrix recon = corrModel.correlation(0.0)
                .subtract(corrModel.pseudoSqrt(0.0)
                        .multipy(corrModel.pseudoSqrt(0.0).transpose()));

        for (int i = 0; i < size; ++i) {
            for (int j = 0; j < size; ++j) {
                if (Math.abs(recon.get(i, j)) > tolerance)
                    boostErrors.add("Failed to reproduce correlation matrix"
                            + "\n    calculated: " + recon.get(i, j)
                            + "\n    expected:   0");
            }
        }

        List<Double> fixingTimes = new ArrayList<>(size);
        for (int i = 0; i < size; ++i)
            fixingTimes.add(0.5 * i);

        final double a = 0.2;
        final double b = 0.1;
        final double c = 2.1;
        final double d = 0.3;

        LmVolatilityModel volaModel =
                new LmLinearExponentialVolatilityModel(fixingTimes, a, b, c, d);

        LfmCovarianceProxy covarProxy =
                new LfmCovarianceProxy(volaModel, corrModel);

        LiborForwardModelProcess process =
                new LiborForwardModelProcess(size, makeIndex());

        LiborForwardModel liborModel =
                new LiborForwardModel(process, volaModel, corrModel);

        for (double t = 0.0; t < 4.6; t += 0.31) {
            recon = covarProxy.covariance(t)
                    .subtract(covarProxy.diffusion(t)
                            .multipy(covarProxy.diffusion(t).transpose()));

            for (int i = 0; i < size; ++i) {
                for (int j = 0; j < size; ++j) {
                    if (Math.abs(recon.get(i, j)) > tolerance)
                        boostErrors.add("Failed to reproduce correlation matrix"
                                + "\n    calculated: " + recon.get(i, j)
                                + "\n    expected:   0");
                }
            }

            Array volatility = volaModel.volatility(t);

            for (int k = 0; k < size; ++k) {
                double expected = 0.0;
                if ((double) k > 2.0 * t) {
                    final double T = fixingTimes.get(k);
                    expected = (a * (T - t) + d) * Math.exp(-b * (T - t)) + c;
                }

                if (Math.abs(expected - volatility.get(k)) > tolerance)
                    boostErrors.add("Failed to reproduce volatities"
                            + "\n    calculated: " + volatility.get(k)
                            + "\n    expected:   " + expected);
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    void testCapletPricing() {
        System.out.println("Testing caplet pricing...");

        boolean usingAtParCoupons =
                IborCoupon.Settings.instance().usingAtParCoupons();

        final int size = 10;
        double tolerance = usingAtParCoupons ? 1e-12 : 1e-5;

        IborIndex index = makeIndex();
        LiborForwardModelProcess process =
                new LiborForwardModelProcess(size, index);

        // set-up pricing engine
        final OptionletVolatilityStructure capVolCurve =
                makeCapVolCurve(Settings.instance.evaluationDate().Date());

        Array variances = new LfmHullWhiteParameterization(process, capVolCurve)
                .covariance(0.0).diagonal();

        LmVolatilityModel volaModel =
                new LmFixedVolatilityModel(MatrixUtil.Sqrt(variances),
                        process.fixingTimes());

        LmCorrelationModel corrModel =
                new LmExponentialCorrelationModel(size, 0.3);

        AffineModel model =
                new LiborForwardModel(process, volaModel, corrModel);

        final Handle<YieldTermStructure> termStructure =
                process.index().forwardingTermStructure();

        AnalyticCapFloorEngine engine1 =
                new AnalyticCapFloorEngine(model, termStructure);

        CapFloor.Cap cap1 = new CapFloor.Cap(process.cashFlows(),
                new ArrayList<>(Collections.nCopies(size, 0.04)));
        cap1.setPricingEngine(engine1);

        final double expected = 0.015853935178;
        final double calculated = cap1.NPV();

        if (Math.abs(expected - calculated) > tolerance)
            boostErrors.add("Failed to reproduce npv"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected);

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    void testCalibration() {
        System.out.println("Testing calibration of a Libor forward model...");

        final int size = 14;
        final double tolerance = 8e-3;

        double[] capVols = {0.145708, 0.158465, 0.166248, 0.168672,
                0.169007, 0.167956, 0.166261, 0.164239,
                0.162082, 0.159923, 0.157781, 0.155745,
                0.153776, 0.151950, 0.150189, 0.148582,
                0.147034, 0.145598, 0.144248};

        double[] swaptionVols = {0.170595, 0.166844, 0.158306, 0.147444,
                0.136930, 0.126833, 0.118135, 0.175963,
                0.166359, 0.155203, 0.143712, 0.132769,
                0.122947, 0.114310, 0.174455, 0.162265,
                0.150539, 0.138734, 0.128215, 0.118470,
                0.110540, 0.169780, 0.156860, 0.144821,
                0.133537, 0.123167, 0.114363, 0.106500,
                0.164521, 0.151223, 0.139670, 0.128632,
                0.119123, 0.110330, 0.103114, 0.158956,
                0.146036, 0.134555, 0.124393, 0.115038,
                0.106996, 0.100064};

        IborIndex index = makeIndex();
        LiborForwardModelProcess process =
                new LiborForwardModelProcess(size, index);
        Handle<YieldTermStructure> termStructure =
                index.forwardingTermStructure();

        // set-up the model
        LmVolatilityModel volaModel =
                new LmExtLinearExponentialVolModel(process.fixingTimes(),
                        0.5, 0.6, 0.1, 0.1);

        LmCorrelationModel corrModel =
                new LmLinearExponentialCorrelationModel(size, 0.5, 0.8);

        LiborForwardModel model =
                new LiborForwardModel(process, volaModel, corrModel);

        int swapVolIndex = 0;
        DayCounter dayCounter = termStructure.currentLink().dayCounter();

        // set-up calibration helper
        List<CalibrationHelper> calibrationHelpers = new ArrayList<>();

        for (int i = 2; i < size; ++i) {
            final Period maturity = TimeUtils.multiply(i, index.tenor());
            Handle<jquant.Quote> capVol = new Handle<>(
                    new SimpleQuote(capVols[i - 2]), true);

            CapHelper caphelper = new CapHelper(maturity, capVol, index,
                    Frequency.ANNUAL, index.dayCounter(), true, termStructure,
                    BlackCalibrationHelper.CalibrationErrorType.ImpliedVolError);

            caphelper.setPricingEngine(
                    new AnalyticCapFloorEngine(model, termStructure));

            calibrationHelpers.add(caphelper);

            if (i <= size / 2) {
                // add a few swaptions to test swaption calibration as well
                for (int j = 1; j <= size / 2; ++j) {
                    final Period len = TimeUtils.multiply(j, index.tenor());
                    Handle<jquant.Quote> swaptionVol = new Handle<>(
                            new SimpleQuote(swaptionVols[swapVolIndex++]), true);

                    SwaptionHelper swaptionHelper = new SwaptionHelper(
                            maturity, len, swaptionVol, index, index.tenor(),
                            dayCounter, index.dayCounter(), termStructure,
                            BlackCalibrationHelper.CalibrationErrorType.ImpliedVolError);

                    swaptionHelper.setPricingEngine(
                            new LfmSwaptionEngine(model, termStructure));

                    calibrationHelpers.add(swaptionHelper);
                }
            }
        }

        LevenbergMarquardt om = new LevenbergMarquardt(1e-6, 1e-6, 1e-6);
        model.calibrate(calibrationHelpers, om,
                new EndCriteria(2000, 100, 1e-6, 1e-6, 1e-6));

        // measure the calibration error
        double calculated = 0.0;
        for (int i = 0; i < calibrationHelpers.size(); ++i) {
            double diff = calibrationHelpers.get(i).calibrationError();
            calculated += diff * diff;
        }

        if (Math.sqrt(calculated) > tolerance)
            boostErrors.add("Failed to calibrate libor forward model"
                    + "\n    calculated diff: " + Math.sqrt(calculated)
                    + "\n    expected : smaller than  " + tolerance);

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    void testSwaptionPricing() {
        System.out.println("Testing forward swap and swaption pricing...");

        boolean usingAtParCoupons =
                IborCoupon.Settings.instance().usingAtParCoupons();

        final int size = 10;
        final int steps = 8 * size;

        double tolerance = usingAtParCoupons ? 1e-12 : 1e-6;

        List<Date> dates = new ArrayList<>(Arrays.asList(
                new Date(4, Month.SEPTEMBER, 2005),
                new Date(4, Month.SEPTEMBER, 2011)));
        List<Double> rates = Arrays.asList(0.04, 0.08);

        IborIndex index = makeIndex(dates, rates);

        LiborForwardModelProcess process =
                new LiborForwardModelProcess(size, index);

        LmCorrelationModel corrModel =
                new LmExponentialCorrelationModel(size, 0.5);

        LmVolatilityModel volaModel =
                new LmLinearExponentialVolatilityModel(process.fixingTimes(),
                        0.291, 1.483, 0.116, 0.00001);

        // set-up pricing engine
        process.setCovarParam(new LfmCovarianceProxy(volaModel, corrModel));

        // set-up a small Monte-Carlo simulation to price swations
        List<Double> tmp = process.fixingTimes();
        TimeGrid grid = new TimeGrid(tmp, steps);

        List<Integer> location = new ArrayList<>();
        for (int i = 0; i < tmp.size(); ++i) {
            int found = -1;
            for (int k = 0; k < grid.size(); ++k) {
                if (grid.get(k) == tmp.get(i)) {
                    found = k;
                    break;
                }
            }
            location.add(found);
        }

        InverseCumulativeRsg rsg = PseudoRandom.make_sequence_generator(
                process.factors() * (grid.size() - 1), 42L);

        final int nrTrails = 5000;
        MultiPathGenerator generator =
                new MultiPathGenerator(process, grid, rsg, false);

        LiborForwardModel liborModel =
                new LiborForwardModel(process, volaModel, corrModel);

        Calendar calendar = index.fixingCalendar();
        DayCounter dayCounter = index.forwardingTermStructure()
                .currentLink().dayCounter();
        BusinessDayConvention convention = index.businessDayConvention();

        Date settlement = index.forwardingTermStructure()
                .currentLink().referenceDate();

        for (int i = 1; i < size; ++i) {
            for (int j = 1; j <= size - i; ++j) {
                Date fwdStart = settlement.add(new Period(6 * i, TimeUnit.MONTHS));
                Date fwdMaturity = fwdStart.add(new Period(6 * j, TimeUnit.MONTHS));

                Schedule schedule = new Schedule(fwdStart, fwdMaturity,
                        index.tenor(), calendar,
                        convention, convention, DateGenerationRule.FORWARD,
                        false, new Date(), new Date());

                double swapRate = 0.0404;
                VanillaSwap forwardSwap = new VanillaSwap(
                        VanillaSwap.Type.Receiver, 1.0,
                        schedule, swapRate, dayCounter,
                        schedule, index, 0.0, index.dayCounter());
                forwardSwap.setPricingEngine(
                        new DiscountingSwapEngine(index.forwardingTermStructure()));

                // check forward pricing first
                final double expected = forwardSwap.fairRate();
                final double calculated = liborModel.S_0(i - 1, i + j - 1);

                if (Math.abs(expected - calculated) > tolerance)
                    boostErrors.add("Failed to reproduce fair forward swap rate"
                            + "\n    calculated: " + calculated
                            + "\n    expected:   " + expected);

                swapRate = forwardSwap.fairRate();
                forwardSwap = new VanillaSwap(
                        VanillaSwap.Type.Receiver, 1.0,
                        schedule, swapRate, dayCounter,
                        schedule, index, 0.0, index.dayCounter());
                forwardSwap.setPricingEngine(
                        new DiscountingSwapEngine(index.forwardingTermStructure()));

                if (i == j && i <= size / 2) {
                    LfmSwaptionEngine engine =
                            new LfmSwaptionEngine(liborModel,
                                    index.forwardingTermStructure());
                    EuropeanExercise exercise =
                            new EuropeanExercise(process.fixingDates().get(i));

                    Swaption swaption = new Swaption(forwardSwap, exercise,
                            Settlement.Type.Physical,
                            Settlement.Method.PhysicalOTC);
                    swaption.setPricingEngine(engine);

                    GeneralStatistics stat = new GeneralStatistics();

                    for (int n = 0; n < nrTrails; ++n) {
                        SampleMultiPath path = (n % 2) != 0
                                ? generator.antithetic() : generator.next();

                        List<Double> pathRates =
                                new ArrayList<>(Collections.nCopies(size, 0.0));
                        for (int k = 0; k < process.size(); ++k)
                            pathRates.set(k,
                                    path.value.get(k).get(location.get(i)));

                        List<Double> dis = process.discountBond(pathRates);

                        double npv = 0.0;
                        for (int m = i; m < i + j; ++m) {
                            npv += (swapRate - pathRates.get(m))
                                    * (process.accrualEndTimes().get(m)
                                    - process.accrualStartTimes().get(m))
                                    * dis.get(m);
                        }
                        stat.add(Math.max(npv, 0.0), 1.0);
                    }

                    if (Math.abs(swaption.NPV() - stat.mean())
                            > stat.errorEstimate() * 2.35)
                        boostErrors.add("Failed to reproduce swaption npv"
                                + "\n    calculated: " + stat.mean()
                                + "\n    expected:   " + swaption.NPV());
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }
}
