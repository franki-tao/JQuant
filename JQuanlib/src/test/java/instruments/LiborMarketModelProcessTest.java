package instruments;

import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.TimeGrid;
import jquant.indexes.Euribor1Y;
import jquant.indexes.Euribor6M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.legacy.libormarketmodels.LfmCovarianceParameterization;
import jquant.legacy.libormarketmodels.LfmHullWhiteParameterization;
import jquant.legacy.libormarketmodels.LiborForwardModelProcess;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.LowDiscrepancy;
import jquant.math.statistics.GeneralStatistics;
import jquant.methods.montecarlo.MultiPathGenerator;
import jquant.methods.montecarlo.SampleMultiPath;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.optionlet.CapletVarianceCurve;
import jquant.termstructures.volatility.optionlet.ConstantOptionletVolatility;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Month;
import jquant.time.TimeUnit;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.ActualActual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/libormarketmodelprocess.cpp} port:
 * {@code testInitialisation}, {@code testLambdaBootstrapping} and
 * {@code testMonteCarloCapletPricing}.
 *
 * <p>Official {@code BOOST_ERROR} records a failure but continues the test
 * case; the messages are accumulated and the test fails at the end. Official
 * {@code BOOST_FAIL} aborts immediately.
 */
public class LiborMarketModelProcessTest {

    private static final int len = 10;

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

    // Official file-level makeIndex(): Euribor1Y, rates {0.01, 0.08},
    // Actual360.
    private IborIndex makeIndex() {
        DayCounter dayCounter = new Actual360();
        List<Date> dates = new ArrayList<>(Arrays.asList(
                new Date(4, Month.SEPTEMBER, 2005),
                new Date(4, Month.SEPTEMBER, 2018)));
        List<Double> rates = Arrays.asList(0.01, 0.08);

        RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>(new ZeroCurve(dates, rates, dayCounter), true);

        IborIndex index = new Euribor1Y(termStructure);

        Date todaysDate =
                index.fixingCalendar().adjust(new Date(4, Month.SEPTEMBER, 2005));
        Settings.instance.evaluationDate().equal(todaysDate);

        dates.set(0, index.fixingCalendar().advance(todaysDate,
                index.fixingDays(), TimeUnit.DAYS));

        termStructure.linkTo(new ZeroCurve(dates, rates, dayCounter), true);

        return index;
    }

    // Official file-level makeCapVolCurve(): 10 vols, process(len+1),
    // ActualActual(ISDA).
    private CapletVarianceCurve makeCapVolCurve(final Date todaysDate) {
        double[] vols = {14.40, 17.15, 16.81, 16.64, 16.17,
                15.78, 15.40, 15.21, 14.86, 14.54};

        List<Date> dates = new ArrayList<>();
        List<Double> capletVols = new ArrayList<>();
        LiborForwardModelProcess process =
                new LiborForwardModelProcess(len + 1, makeIndex());

        for (int i = 0; i < len; ++i) {
            capletVols.add(vols[i] / 100);
            dates.add(process.fixingDates().get(i + 1));
        }

        return new CapletVarianceCurve(todaysDate, dates, capletVols,
                new ActualActual(ActualActual.Convention.ISDA));
    }

    private LiborForwardModelProcess makeProcess() {
        // Official default argument is an empty Matrix().
        return makeProcess(new Matrix(0, 0, 0.0));
    }

    private LiborForwardModelProcess makeProcess(final Matrix volaComp) {
        int factors = (volaComp.empty() ? 1 : volaComp.cols());

        IborIndex index = makeIndex();
        LiborForwardModelProcess process =
                new LiborForwardModelProcess(len, index);

        // Official volaComp*transpose(volaComp); the empty default stays empty.
        Matrix correlation = volaComp.empty()
                ? volaComp
                : volaComp.multipy(volaComp.transpose());

        LfmCovarianceParameterization fct =
                new LfmHullWhiteParameterization(
                        process,
                        makeCapVolCurve(Settings.instance.evaluationDate().Date()),
                        correlation, factors);

        process.setCovarParam(fct);

        return process;
    }

    @Test
    void testInitialisation() {
        System.out.println("Testing caplet LMM process initialisation...");

        DayCounter dayCounter = new Actual360();
        RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>(flatRate(Date.todaysDate(), 0.04, dayCounter), true);

        IborIndex index = new Euribor6M(termStructure);
        OptionletVolatilityStructure capletVol =
                new ConstantOptionletVolatility(
                        termStructure.getValue().referenceDate(),
                        termStructure.getValue().calendar(),
                        BusinessDayConvention.FOLLOWING,
                        0.2,
                        termStructure.getValue().dayCounter());

        Calendar calendar = index.fixingCalendar();

        for (int daysOffset = 0; daysOffset < 1825 /* 5 year*/; daysOffset += 8) {
            Date todaysDate = calendar.adjust(Date.todaysDate().add(daysOffset));
            Settings.instance.evaluationDate().equal(todaysDate);
            Date settlementDate =
                    calendar.advance(todaysDate, index.fixingDays(), TimeUnit.DAYS);

            termStructure.linkTo(flatRate(settlementDate, 0.04, dayCounter), true);

            LiborForwardModelProcess process = new LiborForwardModelProcess(60, index);

            final List<Double> fixings = process.fixingTimes();
            for (int i = 1; i < fixings.size() - 1; ++i) {
                int ileft = process.nextIndexReset(fixings.get(i) - 0.000001);
                int iright = process.nextIndexReset(fixings.get(i) + 0.000001);
                int ii = process.nextIndexReset(fixings.get(i));

                if ((ileft != i) || (iright != i + 1) || (ii != i + 1)) {
                    boostErrors.add("Failed to next index resets");
                }
            }
        }

        failIfErrors();
    }

    @Test
    void testLambdaBootstrapping() {
        System.out.println("Testing caplet LMM lambda bootstrapping...");

        double tolerance = 1e-10;
        double[] lambdaExpected = {14.3010297550, 19.3821411939, 15.9816590141,
                15.9953118303, 14.0570815635, 13.5687599894,
                12.7477197786, 13.7056638165, 11.6191989567};

        LiborForwardModelProcess process = makeProcess();

        Matrix covar = process.covariance(0.0, new Array(0), 1.0);

        for (int i = 0; i < 9; ++i) {
            final double calculated = Math.sqrt(covar.get(i + 1, i + 1));
            final double expected = lambdaExpected[i] / 100;

            if (Math.abs(calculated - expected) > tolerance)
                boostErrors.add("Failed to reproduce expected lambda values"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected);
        }

        LfmCovarianceParameterization param = process.covarParam();

        List<Double> tmp = process.fixingTimes();
        TimeGrid grid = new TimeGrid(tmp, 14);

        for (int gi = 0; gi < grid.size(); ++gi) {
            double t = grid.get(gi);
            Matrix diff = param.integratedCovariance(t, new Array(0))
                    .subtract(param.integratedCovarianceBase(t));

            for (int i = 0; i < diff.rows(); ++i) {
                for (int j = 0; j < diff.cols(); ++j) {
                    if (Math.abs(diff.get(i, j)) > tolerance) {
                        fail("Failed to reproduce integrated covariance"
                                + "\n    i: " + i
                                + "\n    j: " + j
                                + "\nerror: " + diff.get(i, j));
                    }
                }
            }
        }

        failIfErrors();
    }

    @Test
    void testMonteCarloCapletPricing() {
        System.out.println("Testing caplet LMM Monte-Carlo caplet pricing...");

        /* factor loadings are taken from Hull & White article
           plus extra normalisation to get orthogonal eigenvectors
           http://www.rotman.utoronto.ca/~amackay/fin/libormktmodel2.pdf */
        double[] compValues = {0.85549771, 0.46707264, 0.22353259,
                0.91915359, 0.37716089, 0.11360610,
                0.96438280, 0.26413316, -0.01412414,
                0.97939148, 0.13492952, -0.15028753,
                0.95970595, -0.00000000, -0.28100621,
                0.97939148, -0.13492952, -0.15028753,
                0.96438280, -0.26413316, -0.01412414,
                0.91915359, -0.37716089, 0.11360610,
                0.85549771, -0.46707264, 0.22353259};

        Matrix volaComp = new Matrix(9, 3, 0.0);
        for (int i = 0; i < 9; ++i)
            for (int j = 0; j < 3; ++j)
                volaComp.set(i, j, compValues[i * 3 + j]);

        LiborForwardModelProcess process1 = makeProcess();
        LiborForwardModelProcess process2 = makeProcess(volaComp);
        List<Double> tmp = process1.fixingTimes();
        TimeGrid grid = new TimeGrid(tmp, 12);

        List<Integer> location = new ArrayList<>();
        for (int i = 0; i < tmp.size(); ++i) {
            location.add(find(grid, tmp.get(i)));
        }

        // set-up a small Monte-Carlo simulation to price caplets
        // and ratchet caps using a one- and a three factor libor market model
        long seed = 42;
        InverseCumulativeRsg rsg1 = LowDiscrepancy.make_sequence_generator(
                process1.factors() * (grid.size() - 1), seed);
        InverseCumulativeRsg rsg2 = LowDiscrepancy.make_sequence_generator(
                process2.factors() * (grid.size() - 1), seed);
        MultiPathGenerator generator1 = new MultiPathGenerator(process1, grid, rsg1, false);
        MultiPathGenerator generator2 = new MultiPathGenerator(process2, grid, rsg2, false);

        final int nrTrails = 250000;
        List<GeneralStatistics> stat1 = generalStatisticsList(process1.size());
        List<GeneralStatistics> stat2 = generalStatisticsList(process2.size());
        List<GeneralStatistics> stat3 = generalStatisticsList(process2.size() - 1);
        for (int i = 0; i < nrTrails; ++i) {
            SampleMultiPath path1 = generator1.next();
            SampleMultiPath path2 = generator2.next();

            List<Double> rates1 = new ArrayList<>(len);
            List<Double> rates2 = new ArrayList<>(len);
            for (int j = 0; j < process1.size(); ++j) {
                rates1.add(path1.value.get(j).get(location.get(j)));
                rates2.add(path2.value.get(j).get(location.get(j)));
            }

            List<Double> dis1 = process1.discountBond(rates1);
            List<Double> dis2 = process2.discountBond(rates2);

            for (int k = 0; k < process1.size(); ++k) {
                double accrualPeriod = process1.accrualEndTimes().get(k)
                        - process1.accrualStartTimes().get(k);
                // caplet payoff function, cap rate at 4%
                double payoff1 = Math.max(rates1.get(k) - 0.04, 0.0) * accrualPeriod;

                double payoff2 = Math.max(rates2.get(k) - 0.04, 0.0) * accrualPeriod;
                stat1.get(k).add(dis1.get(k) * payoff1, 1.0);
                stat2.get(k).add(dis2.get(k) * payoff2, 1.0);

                if (k != 0) {
                    // ratchet cap payoff function
                    double payoff3 = Math.max(
                            rates2.get(k) - (rates2.get(k - 1) + 0.0025), 0.0)
                            * accrualPeriod;
                    stat3.get(k - 1).add(dis2.get(k) * payoff3, 1.0);
                }
            }
        }

        double[] capletNpv = {0.000000000000, 0.000002841629, 0.002533279333,
                0.009577143571, 0.017746502618, 0.025216116835,
                0.031608230268, 0.036645683881, 0.039792254012,
                0.041829864365};

        double[] ratchetNpv = {0.0082644895, 0.0082754754, 0.0082159966,
                0.0082982822, 0.0083803357, 0.0084366961,
                0.0084173270, 0.0081803406, 0.0079533814};

        for (int k = 0; k < process1.size(); ++k) {

            double calculated1 = stat1.get(k).mean();
            double tolerance1 = stat1.get(k).errorEstimate();
            double expected = capletNpv[k];

            if (Math.abs(calculated1 - expected) > tolerance1) {
                boostErrors.add("Failed to reproduce expected caplet NPV"
                        + "\n    calculated: " + calculated1
                        + "\n    error int:  " + tolerance1
                        + "\n    expected:   " + expected);
            }

            double calculated2 = stat2.get(k).mean();
            double tolerance2 = stat2.get(k).errorEstimate();

            if (Math.abs(calculated2 - expected) > tolerance2) {
                boostErrors.add("Failed to reproduce expected caplet NPV"
                        + "\n    calculated: " + calculated2
                        + "\n    error int:  " + tolerance2
                        + "\n    expected:   " + expected);
            }

            if (k != 0) {
                double calculated3 = stat3.get(k - 1).mean();
                double tolerance3 = stat3.get(k - 1).errorEstimate();
                expected = ratchetNpv[k - 1];

                double refError = 1e-5; // 1e-5. error bars of the reference values

                if (Math.abs(calculated3 - expected) > tolerance3 + refError) {
                    boostErrors.add("Failed to reproduce expected caplet NPV"
                            + "\n    calculated: " + calculated3
                            + "\n    error int:  " + (tolerance3 + refError)
                            + "\n    expected:   " + expected);
                }
            }
        }

        failIfErrors();
    }

    // Official test-suite utilities.flatRate(Date, Rate, DayCounter).
    private YieldTermStructure flatRate(final Date today, double forward,
                                        final DayCounter dc) {
        return new FlatForward(today,
                new Handle<>(new jquant.quotes.SimpleQuote(forward), true), dc);
    }

    private static List<GeneralStatistics> generalStatisticsList(int n) {
        List<GeneralStatistics> result = new ArrayList<>(n);
        for (int i = 0; i < n; ++i)
            result.add(new GeneralStatistics());
        return result;
    }

    // Official std::find over the time grid: first exact match.
    private static int find(final TimeGrid grid, double t) {
        for (int i = 0; i < grid.size(); ++i)
            if (grid.get(i) == t)
                return i;
        fail("time " + t + " not found in the time grid");
        return -1;
    }

    private void failIfErrors() {
        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }
}
