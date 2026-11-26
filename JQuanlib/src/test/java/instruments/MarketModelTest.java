package instruments;

import jquant.Option;
import jquant.Payoff;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.legacy.libormarketmodels.LmExtLinearExponentialVolModel;
import jquant.math.Array;
import jquant.math.MathUtils;
import jquant.math.Matrix;
import jquant.math.integrals.SegmentIntegral;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.Simplex;
import jquant.math.statistics.GenericRiskStatistics;
import jquant.methods.montecarlo.GenericEarlyExerciseOptimization;
import jquant.methods.montecarlo.GenericLongstaffSchwartzRegression;
import jquant.methods.montecarlo.NodeData;
import jquant.models.marketmodels.AccountingEngine;
import jquant.models.marketmodels.BrownianGeneratorFactory;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.ForwardForwardMappings;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.MarketModelEvolver;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.MarketModelPathwiseMultiProduct;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.SequenceStatisticsInc;
import jquant.models.marketmodels.SwapForwardMappings;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.browniangenerators.MTBrownianGeneratorFactory;
import jquant.models.marketmodels.browniangenerators.SobolBrownianGeneratorBase;
import jquant.models.marketmodels.browniangenerators.SobolBrownianGeneratorFactory;
import jquant.models.marketmodels.callability.CollectNodeData;
import jquant.models.marketmodels.callability.LongstaffSchwartzExerciseStrategy;
import jquant.models.marketmodels.callability.NothingExerciseValue;
import jquant.models.marketmodels.callability.ParametricExerciseAdapter;
import jquant.models.marketmodels.callability.SwapBasisSystem;
import jquant.models.marketmodels.callability.SwapRateTrigger;
import jquant.models.marketmodels.callability.TriggeredSwapExercise;
import jquant.models.marketmodels.callability.UpperBoundEngine;
import jquant.models.marketmodels.correlations.ExponentialCorrelations;
import jquant.models.marketmodels.correlations.TimeHomogeneousForwardCorrelation;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.models.marketmodels.driftcomputation.LMMDriftCalculator;
import jquant.models.marketmodels.evolvers.LogNormalFwdRateBalland;
import jquant.models.marketmodels.evolvers.LogNormalFwdRateIpc;
import jquant.models.marketmodels.evolvers.LogNormalFwdRatePc;
import jquant.models.marketmodels.evolvers.NormalFwdRatePc;
import jquant.models.marketmodels.models.AbcdVol;
import jquant.models.marketmodels.models.FlatVol;
import jquant.models.marketmodels.models.FwdPeriodAdapter;
import jquant.models.marketmodels.models.FwdToCotSwapAdapter;
import jquant.models.marketmodels.products.MultiProductComposite;
import jquant.models.marketmodels.products.multistep.CallSpecifiedMultiProduct;
import jquant.models.marketmodels.products.multistep.ExerciseAdapter;
import jquant.models.marketmodels.products.multistep.MultiProductPathwiseWrapper;
import jquant.models.marketmodels.products.multistep.MultiStepCoinitialSwaps;
import jquant.models.marketmodels.products.multistep.MultiStepCoterminalSwaps;
import jquant.models.marketmodels.products.multistep.MultiStepCoterminalSwaptions;
import jquant.models.marketmodels.products.multistep.MultiStepForwards;
import jquant.models.marketmodels.products.multistep.MultiStepInverseFloater;
import jquant.models.marketmodels.products.multistep.MultiStepNothing;
import jquant.models.marketmodels.products.multistep.MultiStepOptionlets;
import jquant.models.marketmodels.products.multistep.MultiStepPeriodCapletSwaptions;
import jquant.models.marketmodels.products.multistep.MultiStepSwap;
import jquant.models.marketmodels.products.onestep.OneStepForwards;
import jquant.models.marketmodels.products.onestep.OneStepOptionlets;
import jquant.models.marketmodels.products.pathwise.MarketModelPathwiseInverseFloater;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.termstructures.volatility.AbcdCalibration;
import jquant.termstructures.volatility.AbcdFunction;
import jquant.termstructures.volatility.AbcdSquared;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.SimpleDayCounter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/marketmodel.cpp} port, first slice.
 *
 * <p>Ported from QuantLib at SHA {@code cf74c9f}. The twelve official named
 * cases below are kept with the official data, 32767 paths, seed 42, the
 * per-case generator choices and the official standard-error thresholds.
 * Unused official code branches (CalibratedMM, ProductSuggested, commented
 * Sobol/MT swaps) stay unrun.
 */
public class MarketModelTest {

    private SavedSettings savedSettings;

    private Date todaysDate;
    private Date endDate;
    private Schedule dates;
    private List<Double> rateTimes;
    private List<Double> paymentTimes;
    private List<Double> accruals;
    private Calendar calendar;
    private DayCounter dayCounter;
    private List<Double> todaysForwards;
    private List<Double> todaysCoterminalSwapRates;
    private double meanForward;
    private List<Double> coterminalAnnuity;
    private double displacement;
    private List<Double> todaysDiscounts;
    private List<Double> volatilities;
    private List<Double> blackVols;
    private List<Double> normalVols;
    private List<Double> swaptionsVolatilities;
    private List<Double> swaptionsBlackVols;
    private double a, b, c, d;
    private double longTermCorrelation, beta;
    private int measureOffset_;
    private long seed_;
    private int paths_, trainingPaths_;
    private boolean printReport_ = false;

    // a simple structure to store some data which will be used during tests
    private static class SubProductExpectedValues {
        SubProductExpectedValues(String description) {
            this.description = description;
        }

        String description;
        List<Double> values = new ArrayList<>();
        boolean testBias = false;
        double errorThreshold;
    }

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private void setup() {
        // Times
        calendar = new NullCalendar();
        todaysDate = Settings.instance.evaluationDate().Date();
        //startDate = todaysDate + 5*Years;
        endDate = todaysDate.add(new Period(5, TimeUnit.YEARS));
        dates = new Schedule(todaysDate, endDate,
                new Period(Frequency.SEMIANNUAL),
                calendar, BusinessDayConvention.FOLLOWING,
                BusinessDayConvention.FOLLOWING,
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        rateTimes = filledList(dates.size() - 1, 0.0);
        paymentTimes = filledList(rateTimes.size() - 1, 0.0);
        accruals = filledList(rateTimes.size() - 1, 0.0);
        dayCounter = new SimpleDayCounter();
        for (int i = 1; i < dates.size(); ++i)
            rateTimes.set(i - 1, dayCounter.yearFraction(todaysDate, dates.dates().get(i)));
        for (int i = 0; i < paymentTimes.size(); ++i)
            paymentTimes.set(i, rateTimes.get(i + 1));
        for (int i = 1; i < rateTimes.size(); ++i)
            accruals.set(i - 1, rateTimes.get(i) - rateTimes.get(i - 1));

        // Rates & displacement
        todaysForwards = filledList(paymentTimes.size(), 0.0);
        displacement = 0.0;
        meanForward = 0.0;

        for (int i = 0; i < todaysForwards.size(); ++i) {
            todaysForwards.set(i, 0.03 + 0.0010 * i);
            meanForward += todaysForwards.get(i);
        }
        meanForward /= todaysForwards.size();

        // Discounts
        todaysDiscounts = filledList(rateTimes.size(), 0.0);
        todaysDiscounts.set(0, 0.95);
        for (int i = 1; i < rateTimes.size(); ++i)
            todaysDiscounts.set(i, todaysDiscounts.get(i - 1)
                    / (1.0 + todaysForwards.get(i - 1) * accruals.get(i - 1)));

        // Coterminal swap rates & annuities
        int N = todaysForwards.size();
        todaysCoterminalSwapRates = filledList(N, 0.0);
        coterminalAnnuity = filledList(N, 0.0);
        double floatingLeg = 0.0;
        for (int i = 1; i <= N; ++i) {
            if (i == 1) {
                coterminalAnnuity.set(N - 1, accruals.get(N - 1) * todaysDiscounts.get(N));
            } else {
                coterminalAnnuity.set(N - i, coterminalAnnuity.get(N - i + 1)
                        + accruals.get(N - i) * todaysDiscounts.get(N - i + 1));
            }
            floatingLeg = todaysDiscounts.get(N - i) - todaysDiscounts.get(N);
            todaysCoterminalSwapRates.set(N - i, floatingLeg / coterminalAnnuity.get(N - i));
        }

        // Cap/Floor Volatilities
        double[] mktVols = {
                0.15541283,
                0.18719678,
                0.20890740,
                0.22318179,
                0.23212717,
                0.23731450,
                0.23988649,
                0.24066384,
                0.24023111,
                0.23900189,
                0.23726699,
                0.23522952,
                0.23303022,
                0.23076564,
                0.22850101,
                0.22627951,
                0.22412881,
                0.22206569,
                0.22009939
        };

        a = -0.0597;
        b = 0.1677;
        c = 0.5403;
        d = 0.1710;
        volatilities = filledList(todaysForwards.size(), 0.0);
        blackVols = filledList(todaysForwards.size(), 0.0);
        normalVols = filledList(todaysForwards.size(), 0.0);
        for (int i = 0; i < Math.min(mktVols.length, todaysForwards.size()); i++) {
            volatilities.set(i, todaysForwards.get(i) * mktVols[i]
                    / (todaysForwards.get(i) + displacement));
            blackVols.set(i, mktVols[i]);
            normalVols.set(i, mktVols[i] * todaysForwards.get(i));
        }

        // Swaption volatility quick fix
        swaptionsVolatilities = new ArrayList<>(volatilities);

        // Cap/Floor Correlation
        longTermCorrelation = 0.5;
        beta = 0.2;
        measureOffset_ = 5;

        // Monte Carlo
        seed_ = 42;

        paths_ = 32767; //262144-1; //; // 2^15-1
        trainingPaths_ = 8191; // 2^13-1
    }

    private SequenceStatisticsInc simulate(final MarketModelEvolver evolver,
                                          final MarketModelMultiProduct product) {
        int initialNumeraire = evolver.numeraires().get(0);
        double initialNumeraireValue = todaysDiscounts.get(initialNumeraire);

        AccountingEngine engine = new AccountingEngine(evolver, product,
                initialNumeraireValue);
        SequenceStatisticsInc stats = new SequenceStatisticsInc(product.numberOfProducts());
        engine.multiplePathValues(stats, paths_);
        return stats;
    }

    private enum MarketModelType {
        ExponentialCorrelationFlatVolatility,
        ExponentialCorrelationAbcdVolatility/*,
        CalibratedMM*/
    }

    private static String marketModelTypeToString(MarketModelType type) {
        switch (type) {
            case ExponentialCorrelationFlatVolatility:
                return "Exp. Corr. Flat Vol.";
            case ExponentialCorrelationAbcdVolatility:
                return "Exp. Corr. Abcd Vol.";
                //case CalibratedMM:
                //    return "CalibratedMarketModel";
            default:
                throw new IllegalArgumentException("unknown MarketModelEvolver type");
        }
    }

    private MarketModel makeMarketModel(boolean logNormal,
                                        final EvolutionDescription evolution,
                                        int numberOfFactors,
                                        MarketModelType marketModelType) {
        return makeMarketModel(logNormal, evolution, numberOfFactors,
                marketModelType, 0.0, 0.0);
    }

    private MarketModel makeMarketModel(boolean logNormal,
                                        final EvolutionDescription evolution,
                                        int numberOfFactors,
                                        MarketModelType marketModelType,
                                        double forwardBump,
                                        double volBump) {
        List<Double> fixingTimes = new ArrayList<>(evolution.rateTimes());
        fixingTimes.remove(fixingTimes.size() - 1);
        // Official also constructs LmExtLinearExponentialVolModel and
        // LmLinearExponentialCorrelationModel here, but they are only used by
        // the commented-out CalibratedMM branch; they are omitted.

        List<Double> bumpedForwards = filledList(todaysForwards.size(), 0.0);
        for (int i = 0; i < todaysForwards.size(); ++i)
            bumpedForwards.set(i, todaysForwards.get(i) + forwardBump);

        List<Double> bumpedVols = filledList(volatilities.size(), 0.0);
        if (logNormal) {
            for (int i = 0; i < volatilities.size(); ++i)
                bumpedVols.set(i, volatilities.get(i) + volBump);
        } else {
            for (int i = 0; i < normalVols.size(); ++i)
                bumpedVols.set(i, normalVols.get(i) + volBump);
        }

        Matrix correlations = ExponentialCorrelations.exponentialCorrelations(
                evolution.rateTimes(), longTermCorrelation, beta);
        PiecewiseConstantCorrelation corr = new TimeHomogeneousForwardCorrelation(
                correlations, evolution.rateTimes());
        List<Double> displacements = filledList(bumpedForwards.size(), displacement);
        switch (marketModelType) {
            case ExponentialCorrelationFlatVolatility:
                return new FlatVol(bumpedVols,
                        corr,
                        evolution,
                        numberOfFactors,
                        bumpedForwards,
                        displacements);
            case ExponentialCorrelationAbcdVolatility:
                return new AbcdVol(0.0, 0.0, 1.0, 1.0,
                        bumpedVols,
                        corr,
                        evolution,
                        numberOfFactors,
                        bumpedForwards,
                        displacements);
                //case CalibratedMM:
                //    return ext::shared_ptr<MarketModel>(new
                //        CalibratedMarketModel(volModel, corrModel,
                //                              evolution,
                //                              numberOfFactors,
                //                              bumpedForwards,
                //                              displacement));
            default:
                throw new IllegalArgumentException("unknown MarketModel type");
        }
    }

    private enum MeasureType {
        ProductSuggested, Terminal,
        MoneyMarket, MoneyMarketPlus
    }

    private static String measureTypeToString(MeasureType type) {
        switch (type) {
            case ProductSuggested:
                return "ProductSuggested measure";
            case Terminal:
                return "Terminal measure";
            case MoneyMarket:
                return "Money Market measure";
            case MoneyMarketPlus:
                return "Money Market Plus measure";
            default:
                throw new IllegalArgumentException("unknown measure type");
        }
    }

    private List<Integer> makeMeasure(final MarketModelMultiProduct product,
                                      MeasureType measureType) {
        List<Integer> result;
        final EvolutionDescription evolution = product.evolution();
        switch (measureType) {
            case ProductSuggested:
                result = product.suggestedNumeraires();
                break;
            case Terminal:
                result = EvolutionDescription.terminalMeasure(evolution);
                if (!EvolutionDescription.isInTerminalMeasure(evolution, result)) {
                    fail("\nfailure in verifying Terminal measure:\n"
                            + toStream(result));
                }
                break;
            case MoneyMarket:
                result = EvolutionDescription.moneyMarketMeasure(evolution);
                if (!EvolutionDescription.isInMoneyMarketMeasure(evolution, result)) {
                    fail("\nfailure in verifying MoneyMarket measure:\n"
                            + toStream(result));
                }
                break;
            case MoneyMarketPlus:
                result = EvolutionDescription.moneyMarketPlusMeasure(evolution, measureOffset_);
                if (!EvolutionDescription.isInMoneyMarketPlusMeasure(evolution, result,
                        measureOffset_)) {
                    fail("\nfailure in verifying MoneyMarketPlus("
                            + measureOffset_ + ") measure:\n" + toStream(result));
                }
                break;
            default:
                throw new IllegalArgumentException("unknown measure type");
        }
        EvolutionDescription.checkCompatibility(evolution, result);
        if (printReport_) {
            System.out.println("    " + measureTypeToString(measureType) + ": "
                    + toStream(result));
        }
        return result;
    }

    private enum EvolverType {
        Ipc, Balland, Pc, NormalPc
    }

    private static String evolverTypeToString(EvolverType type) {
        switch (type) {
            case Ipc:
                return "iterative predictor corrector";
            case Balland:
                return "Balland predictor corrector";
            case Pc:
                return "predictor corrector";
            case NormalPc:
                return "predictor corrector for normal case";
            default:
                throw new IllegalArgumentException("unknown MarketModelEvolver type");
        }
    }

    private MarketModelEvolver makeMarketModelEvolver(
            final MarketModel marketModel,
            final List<Integer> numeraires,
            final BrownianGeneratorFactory generatorFactory,
            EvolverType evolverType) {
        return makeMarketModelEvolver(marketModel, numeraires,
                generatorFactory, evolverType, 0);
    }

    private MarketModelEvolver makeMarketModelEvolver(
            final MarketModel marketModel,
            final List<Integer> numeraires,
            final BrownianGeneratorFactory generatorFactory,
            EvolverType evolverType,
            int initialStep) {
        switch (evolverType) {
            case Ipc:
                return new LogNormalFwdRateIpc(marketModel, generatorFactory,
                        numeraires, initialStep);
            case Balland:
                return new LogNormalFwdRateBalland(marketModel, generatorFactory,
                        numeraires, initialStep);
            case Pc:
                return new LogNormalFwdRatePc(marketModel, generatorFactory,
                        numeraires, initialStep);
            case NormalPc:
                return new NormalFwdRatePc(marketModel, generatorFactory,
                        numeraires, initialStep);
            default:
                throw new IllegalArgumentException("unknown MarketModelEvolver type");
        }
    }

    private void checkMultiProductCompositeResults(
            final SequenceStatisticsInc stats,
            final List<SubProductExpectedValues> subProductExpectedValues,
            final String config) {

        List<Double> results = stats.mean();
        List<Double> errors = stats.errorEstimate();

        // size check
        int nbOfResults = 0;
        for (SubProductExpectedValues subProductExpectedValue : subProductExpectedValues)
            nbOfResults += subProductExpectedValue.values.size();

        if (nbOfResults != results.size())
            fail("mismatch between the size of the result and the "
                    + "number of results");
        int currentResultIndex = 0;

        List<Double> stdDevs = new ArrayList<>();
        for (SubProductExpectedValues subProductExpectedValue : subProductExpectedValues) {
            double minError = MathUtils.QL_MAX_REAL;
            double maxError = MathUtils.QL_MIN_REAL;
            double errorThreshold = subProductExpectedValue.errorThreshold;
            for (double value : subProductExpectedValue.values) {
                double stdDev =
                        (results.get(currentResultIndex) - value) / errors.get(currentResultIndex);
                stdDevs.add(stdDev);
                maxError = Math.max(maxError, stdDev);
                minError = Math.min(minError, stdDev);
                ++currentResultIndex;
            }
            boolean isBiased = minError > 0.0 || maxError < 0.0;
            if (printReport_
                    || (subProductExpectedValue.testBias && isBiased)
                    || Math.max(-minError, maxError) > errorThreshold) {
                StringBuilder message = new StringBuilder();
                message.append(config).append("\n");
                currentResultIndex = 0;
                for (int j = 0; j < subProductExpectedValue.values.size(); ++j) {
                    message.append(Utilities.ordinal(j + 1))
                            .append(" ").append(subProductExpectedValue.description)
                            .append(": ").append(rate(results.get(currentResultIndex)))
                            .append("\t").append(rate(subProductExpectedValue.values.get(j)))
                            .append("\t").append(rate(errors.get(currentResultIndex)))
                            .append("; discrepancy = ")
                            .append(stdDevs.get(currentResultIndex))
                            .append("\n");
                    ++currentResultIndex;
                }
                message.append("test failed");
                fail(message.toString());
            }
        }
    }

    private void checkForwardsAndOptionlets(
            final SequenceStatisticsInc stats,
            final List<Double> forwardStrikes,
            final List<StrikedTypePayoff> displacedPayoffs,
            final String config) {

        List<Double> results = stats.mean();
        List<Double> errors = stats.errorEstimate();

        int N = todaysForwards.size();
        double[] expectedForwards = new double[N];
        double[] expectedCaplets = new double[N];
        double[] forwardStdDevs = new double[N];
        double[] capletStdDev = new double[N];
        double minError = MathUtils.QL_MAX_REAL;
        double maxError = MathUtils.QL_MIN_REAL;
        // forwards check
        for (int i = 0; i < N; ++i) {
            expectedForwards[i] = (todaysForwards.get(i) - forwardStrikes.get(i))
                    * accruals.get(i) * todaysDiscounts.get(i + 1);
            forwardStdDevs[i] = (results.get(i) - expectedForwards[i]) / errors.get(i);
            if (forwardStdDevs[i] > maxError)
                maxError = forwardStdDevs[i];
            else if (forwardStdDevs[i] < minError)
                minError = forwardStdDevs[i];
            double expiry = rateTimes.get(i);
            expectedCaplets[i] =
                    new BlackCalculator(displacedPayoffs.get(i),
                            todaysForwards.get(i) + displacement,
                            volatilities.get(i) * Math.sqrt(expiry),
                            todaysDiscounts.get(i + 1) * accruals.get(i)).value();
            capletStdDev[i] = (results.get(i + N) - expectedCaplets[i]) / errors.get(i + N);
            if (capletStdDev[i] > maxError)
                maxError = capletStdDev[i];
            else if (capletStdDev[i] < minError)
                minError = capletStdDev[i];
        }

        double errorThreshold = 2.50;
        if (printReport_ || minError > 0.0 || maxError < 0.0
                || minError < -errorThreshold || maxError > errorThreshold) {
            StringBuilder message = new StringBuilder();
            message.append(config).append("\n");
            for (int i = 0; i < N; ++i) {
                message.append(Utilities.ordinal(i + 1)).append(" forward: ")
                        .append(rate(results.get(i)))
                        .append("\t").append(rate(expectedForwards[i]))
                        .append("\t").append(rate(errors.get(i)))
                        .append("; discrepancy = ")
                        .append(forwardStdDevs[i])
                        .append("\n");
            }
            for (int i = 0; i < N; ++i) {
                message.append(Utilities.ordinal(i + 1)).append("\t")
                        .append(rate(results.get(i + N)))
                        .append(" +- ").append(rate(errors.get(i + N)))
                        .append("\t").append(rate(expectedCaplets[i]))
                        .append("\t").append(rate(errors.get(i + N)))
                        .append("; discrepancy = ")
                        .append((results.get(i + N) - expectedCaplets[i])
                                / (errors.get(i + N) == 0.0 ? 1.0 : errors.get(i + N)))
                        .append("\n");
            }
            message.append("test failed");
            fail(message.toString());
        }
    }

    private void checkNormalForwardsAndOptionlets(
            final SequenceStatisticsInc stats,
            final List<Double> forwardStrikes,
            final List<PlainVanillaPayoff> displacedPayoffs,
            final String config) {

        List<Double> results = stats.mean();
        List<Double> errors = stats.errorEstimate();

        int N = todaysForwards.size();
        double[] expectedForwards = new double[N];
        double[] expectedCaplets = new double[N];
        double[] forwardStdDevs = new double[N];
        double[] capletStdDev = new double[N];
        double minError = MathUtils.QL_MAX_REAL;
        double maxError = MathUtils.QL_MIN_REAL;
        // forwards check
        for (int i = 0; i < N; ++i) {
            expectedForwards[i] = (todaysForwards.get(i) - forwardStrikes.get(i))
                    * accruals.get(i) * todaysDiscounts.get(i + 1);
            forwardStdDevs[i] = (results.get(i) - expectedForwards[i]) / errors.get(i);
            if (forwardStdDevs[i] > maxError)
                maxError = forwardStdDevs[i];
            else if (forwardStdDevs[i] < minError)
                minError = forwardStdDevs[i];
            double expiry = rateTimes.get(i);
            expectedCaplets[i] =
                    BlackFormula.bachelierBlackFormula(displacedPayoffs.get(i).optionType(),
                            displacedPayoffs.get(i).strike(),
                            todaysForwards.get(i) + displacement,
                            normalVols.get(i) * Math.sqrt(expiry),
                            todaysDiscounts.get(i + 1) * accruals.get(i));
            capletStdDev[i] = (results.get(i + N) - expectedCaplets[i]) / errors.get(i + N);
            if (capletStdDev[i] > maxError)
                maxError = capletStdDev[i];
            else if (capletStdDev[i] < minError)
                minError = capletStdDev[i];
        }

        double errorThreshold = 2.50;
        if (minError > 0.0 || maxError < 0.0
                || minError < -errorThreshold || maxError > errorThreshold) {
            StringBuilder message = new StringBuilder();
            message.append(config).append("\n");
            for (int i = 0; i < N; ++i) {
                message.append(Utilities.ordinal(i + 1)).append(" forward: ")
                        .append(rate(results.get(i)))
                        .append(" +- ").append(rate(errors.get(i)))
                        .append("; expected: ").append(rate(expectedForwards[i]))
                        .append("; discrepancy = ")
                        .append(forwardStdDevs[i])
                        .append(" standard errors\n");
            }
            for (int i = 0; i < N; ++i) {
                message.append(Utilities.ordinal(i + 1)).append(" caplet: ")
                        .append(rate(results.get(i + N)))
                        .append(" +- ").append(rate(errors.get(i + N)))
                        .append("; expected: ").append(rate(expectedCaplets[i]))
                        .append("; discrepancy = ")
                        .append((results.get(i + N) - expectedCaplets[i])
                                / (errors.get(i + N) == 0.0 ? 1.0 : errors.get(i + N)))
                        .append(" standard errors\n");
            }
            message.append("test failed");
            fail(message.toString());
        }
    }

    @Test
    void testOneStepForwardsAndOptionlets() {

        setup();

        List<Double> forwardStrikes = filledList(todaysForwards.size(), 0.0);
        List<Payoff> optionletPayoffs = new ArrayList<>();
        List<StrikedTypePayoff> displacedPayoffs = new ArrayList<>();
        for (int i = 0; i < todaysForwards.size(); ++i) {
            forwardStrikes.set(i, todaysForwards.get(i) + 0.01);
            optionletPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i)));
            displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i) + displacement));
        }

        OneStepForwards forwards = new OneStepForwards(rateTimes, accruals,
                paymentTimes, forwardStrikes);
        OneStepOptionlets optionlets = new OneStepOptionlets(rateTimes, accruals,
                paymentTimes, optionletPayoffs);

        MultiProductComposite product = new MultiProductComposite();
        product.add(forwards);
        product.add(optionlets);
        product.finalize();

        EvolutionDescription evolution = product.evolution();

        MarketModelType[] marketModels = {
                // CalibratedMM,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        for (MarketModelType j : marketModels) {

            // one step must be always full factors
            int[] testedFactors = {todaysForwards.size()};
            for (int factors : testedFactors) {
                // for one step product ProductSuggested is equal to Terminal
                // for one step product MoneyMarketPlus is equal to Terminal
                MeasureType[] measures = {MeasureType.MoneyMarket, MeasureType.Terminal};
                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(product, measure);

                    boolean logNormal = true;
                    MarketModel marketModel =
                            makeMarketModel(logNormal, evolution, factors, j);

                    EvolverType[] evolvers = {EvolverType.Pc, EvolverType.Balland, EvolverType.Ipc};
                    int stop = EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; i++) {

                        for (int n = 0; n < 1; n++) {
                            MTBrownianGeneratorFactory generatorFactory =
                                    new MTBrownianGeneratorFactory(seed_);
                            // SobolBrownianGeneratorFactory generatorFactory(
                            //    SobolBrownianGenerator::Diagonal, seed_);

                            MarketModelEvolver evolver = makeMarketModelEvolver(
                                    marketModel, numeraires, generatorFactory, evolvers[i]);
                            String config = configString(j, factors, measure, evolvers[i]);
                            if (printReport_)
                                System.out.println("    " + config);

                            SequenceStatisticsInc stats = simulate(evolver, product);
                            checkForwardsAndOptionlets(stats, forwardStrikes,
                                    displacedPayoffs, config);
                        }
                    }
                }
            }
        }
    }

    @Test
    void testOneStepNormalForwardsAndOptionlets() {

        setup();

        List<Double> forwardStrikes = filledList(todaysForwards.size(), 0.0);
        List<Payoff> optionletPayoffs = new ArrayList<>();
        List<PlainVanillaPayoff> displacedPayoffs = new ArrayList<>();
        for (int i = 0; i < todaysForwards.size(); ++i) {
            forwardStrikes.set(i, todaysForwards.get(i) + 0.01);
            optionletPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i)));
            displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i) + displacement));
        }

        OneStepForwards forwards = new OneStepForwards(rateTimes, accruals,
                paymentTimes, forwardStrikes);
        OneStepOptionlets optionlets = new OneStepOptionlets(rateTimes, accruals,
                paymentTimes, optionletPayoffs);

        MultiProductComposite product = new MultiProductComposite();
        product.add(forwards);
        product.add(optionlets);
        product.finalize();

        EvolutionDescription evolution = product.evolution();

        MarketModelType[] marketModels = {
                // CalibratedMM,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        for (MarketModelType j : marketModels) {

            // one step must be always full factors
            int[] testedFactors = {todaysForwards.size()};
            for (int factors : testedFactors) {
                // for one step product ProductSuggested is equal to Terminal
                // for one step product MoneyMarketPlus is equal to Terminal
                MeasureType[] measures = {MeasureType.MoneyMarket, MeasureType.Terminal};
                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(product, measure);

                    boolean logNormal = false;
                    MarketModel marketModel =
                            makeMarketModel(logNormal, evolution, factors, j);

                    EvolverType[] evolvers = {EvolverType.NormalPc};
                    int stop = EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; i++) {

                        for (int n = 0; n < 1; n++) {
                            MTBrownianGeneratorFactory generatorFactory =
                                    new MTBrownianGeneratorFactory(seed_);
                            // SobolBrownianGeneratorFactory generatorFactory(
                            //    SobolBrownianGenerator::Diagonal, seed_);

                            MarketModelEvolver evolver = makeMarketModelEvolver(
                                    marketModel, numeraires, generatorFactory, evolvers[i]);
                            String config = configString(j, factors, measure, evolvers[i]);
                            if (printReport_)
                                System.out.println("    " + config);

                            SequenceStatisticsInc stats = simulate(evolver, product);
                            checkNormalForwardsAndOptionlets(stats, forwardStrikes,
                                    displacedPayoffs, config);
                        }
                    }
                }
            }
        }
    }

    @Test
    void testInverseFloater() {

        setup();

        List<Double> fixedStrikes = filledList(accruals.size(), 0.1);
        List<Double> fixedMultipliers = filledList(accruals.size(), 2.0);
        List<Double> floatingSpreads = filledList(accruals.size(), 0.002);
        List<Double> fixedAccruals = new ArrayList<>(accruals);
        List<Double> floatingAccruals = new ArrayList<>(accruals);

        boolean payer = true;

        MultiStepInverseFloater product = new MultiStepInverseFloater(rateTimes,
                fixedAccruals,
                floatingAccruals,
                fixedStrikes,
                fixedMultipliers,
                floatingSpreads,
                paymentTimes,
                payer);

        MarketModelPathwiseInverseFloater productPathwise =
                new MarketModelPathwiseInverseFloater(rateTimes,
                        fixedAccruals,
                        floatingAccruals,
                        fixedStrikes,
                        fixedMultipliers,
                        floatingSpreads,
                        paymentTimes,
                        payer);

        MultiProductPathwiseWrapper productWrapped =
                new MultiProductPathwiseWrapper(productPathwise);

        MultiProductComposite productComposite = new MultiProductComposite();
        productComposite.add(product);
        productComposite.add(productWrapped);
        productComposite.finalize();

        EvolutionDescription evolution = productComposite.evolution();

        MarketModelType[] marketModels = {
                // CalibratedMM,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        for (MarketModelType j : marketModels) {

            int[] testedFactors = {Math.min(todaysForwards.size(), 3)};
            for (int factors : testedFactors) {
                MeasureType[] measures = {MeasureType.MoneyMarket};
                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(product, measure);

                    boolean logNormal = false;
                    MarketModel marketModel =
                            makeMarketModel(logNormal, evolution, factors, j);

                    EvolverType[] evolvers = {EvolverType.Pc};

                    for (EvolverType i : evolvers) {

                        MTBrownianGeneratorFactory generatorFactory =
                                new MTBrownianGeneratorFactory(seed_);
                        // SobolBrownianGeneratorFactory generatorFactory(
                        //    SobolBrownianGenerator::Diagonal, seed_);

                        MarketModelEvolver evolver = makeMarketModelEvolver(
                                marketModel, numeraires, generatorFactory, i);
                        String config = configString(j, factors, measure, i);
                        if (printReport_)
                            System.out.println("    " + config);

                        SequenceStatisticsInc stats = simulate(evolver, productComposite);

                        double[] modelVolatilities = new double[accruals.size()];
                        for (int k = 0; k < accruals.size(); ++k)
                            modelVolatilities[k] =
                                    Math.sqrt(marketModel.totalCovariance(k).get(k, k));

                        double truePrice = 0.0;

                        for (int k = 0; k < accruals.size(); ++k) {
                            double floatingCouponPV = floatingAccruals.get(k)
                                    * (todaysForwards.get(k) + floatingSpreads.get(k))
                                    * todaysDiscounts.get(k + 1);
                            double inverseCouponPV =
                                    2 * fixedAccruals.get(k) * todaysDiscounts.get(k + 1)
                                            * BlackFormula.blackFormula(Option.Type.Put,
                                            fixedStrikes.get(k) / 2.0,
                                            todaysForwards.get(k),
                                            modelVolatilities[k], 1.0, 0.0);

                            truePrice += floatingCouponPV - inverseCouponPV;
                        }

                        double priceError = stats.mean().get(0) - truePrice;
                        double priceSD = stats.errorEstimate().get(0);

                        double errorInSds = priceError / priceSD;
                        if (Math.abs(errorInSds) > 4.0)
                            fail("Inverse floater product has price error equal to "
                                    + errorInSds + " sds . Price " + truePrice
                                    + " MC price " + stats.mean().get(0) + " \n");

                        double numericalTolerance = 1E-12;

                        if (Math.abs(stats.mean().get(0) - stats.mean().get(1))
                                > numericalTolerance)
                            fail("Inverse floater and wrapper pathwise inverse floater "
                                    + "do not agree:" + stats.mean().get(0) + "  "
                                    + stats.mean().get(1));

                    } // evolvers
                }     // measures
            }         // factors
        }
    }

    private void testMultiProductComposite(
            final MarketModelMultiProduct product,
            final List<SubProductExpectedValues> subProductExpectedValues) {

        setup();

        final EvolutionDescription evolution = product.evolution();

        MarketModelType[] marketModels = {
                // CalibratedMM,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        for (MarketModelType j : marketModels) {

            int[] testedFactors = {4, 8, todaysForwards.size()};
            for (int factors : testedFactors) {
                // Composite's ProductSuggested is the Terminal one
                MeasureType[] measures = {// ProductSuggested,
                        MeasureType.Terminal, MeasureType.MoneyMarketPlus,
                        MeasureType.MoneyMarket};
                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(product, measure);

                    boolean logNormal = true;
                    MarketModel marketModel =
                            makeMarketModel(logNormal, evolution, factors, j);

                    EvolverType[] evolvers = {EvolverType.Pc, EvolverType.Balland, EvolverType.Ipc};
                    int stop =
                            EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; i++) {

                        for (int n = 0; n < 1; n++) {
                            // MTBrownianGeneratorFactory
                            // generatorFactory(seed_);
                            SobolBrownianGeneratorFactory generatorFactory =
                                    new SobolBrownianGeneratorFactory(
                                            SobolBrownianGeneratorBase.Ordering.Diagonal,
                                            seed_);

                            MarketModelEvolver evolver = makeMarketModelEvolver(
                                    marketModel, numeraires, generatorFactory, evolvers[i]);
                            String config = configString(j, factors, measure, evolvers[i]);
                            if (printReport_)
                                System.out.println("    " + config);

                            SequenceStatisticsInc stats = simulate(evolver, product);
                            checkMultiProductCompositeResults(stats,
                                    subProductExpectedValues, config);
                        }
                    }
                }
            }
        }
    }

    private void addForwards(MultiProductComposite product,
                             List<SubProductExpectedValues> subProductExpectedValues) {

        // create forwards and add them to the product...
        List<Double> forwardStrikes = filledList(todaysForwards.size(), 0.0);

        for (int i = 0; i < todaysForwards.size(); ++i)
            forwardStrikes.set(i, todaysForwards.get(i) + 0.01);

        MultiStepForwards forwards = new MultiStepForwards(rateTimes, accruals,
                paymentTimes, forwardStrikes);
        product.add(forwards);

        // computing and storing expected values
        SubProductExpectedValues expected = new SubProductExpectedValues("Forward");
        expected.errorThreshold = 2.50;
        subProductExpectedValues.add(expected);
        for (int i = 0; i < todaysForwards.size(); ++i) {
            expected.values.add((todaysForwards.get(i) - forwardStrikes.get(i))
                    * accruals.get(i) * todaysDiscounts.get(i + 1));
        }
    }

    private void addOptionLets(MultiProductComposite product,
                               List<SubProductExpectedValues> subProductExpectedValues) {

        // create the products...
        List<Payoff> optionletPayoffs = new ArrayList<>();
        List<StrikedTypePayoff> displacedPayoffs = new ArrayList<>();

        for (int i = 0; i < todaysForwards.size(); ++i) {
            optionletPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i)));
            //CashOrNothingPayoff(Option::Call, todaysForwards[i], 0.01));
            displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i) + displacement));
            //CashOrNothingPayoff(Option::Call, todaysForwards[i]+displacement, 0.01));
        }

        MultiStepOptionlets optionlets = new MultiStepOptionlets(rateTimes, accruals,
                paymentTimes, optionletPayoffs);
        product.add(optionlets);

        // computing and storing expected values
        SubProductExpectedValues expected = new SubProductExpectedValues("Caplet");
        expected.errorThreshold = 2.50;
        subProductExpectedValues.add(expected);
        for (int i = 0; i < todaysForwards.size(); ++i) {
            expected.values.add(new BlackCalculator(displacedPayoffs.get(i),
                    todaysForwards.get(i) + displacement,
                    volatilities.get(i) * Math.sqrt(rateTimes.get(i)),
                    todaysDiscounts.get(i + 1) * accruals.get(i)).value());
        }
    }

    private void addCoinitialSwaps(MultiProductComposite product,
                                   List<SubProductExpectedValues> subProductExpectedValues) {

        // create the products...
        double fixedRate = 0.04;
        MultiStepCoinitialSwaps multiStepCoinitialSwaps =
                new MultiStepCoinitialSwaps(rateTimes, accruals, accruals,
                        paymentTimes, fixedRate);
        product.add(multiStepCoinitialSwaps);
        // computing and storing expected values
        SubProductExpectedValues expected =
                new SubProductExpectedValues("coinitial swap");
        expected.testBias = false;
        expected.errorThreshold = 2.32;
        subProductExpectedValues.add(expected);
        double coinitialSwapValue = 0;
        for (int i = 0; i < todaysForwards.size(); ++i) {
            coinitialSwapValue += (todaysForwards.get(i) - fixedRate)
                    * accruals.get(i) * todaysDiscounts.get(i + 1);
            expected.values.add(coinitialSwapValue);
        }
    }

    private void addCoterminalSwapsAndSwaptions(
            MultiProductComposite product,
            List<SubProductExpectedValues> subProductExpectedValues) {

        double fixedRate = 0.04;
        MultiStepCoterminalSwaps swaps = new MultiStepCoterminalSwaps(rateTimes,
                accruals, accruals, paymentTimes, fixedRate);

        List<StrikedTypePayoff> payoffs = new ArrayList<>();
        for (int i = 0; i < todaysForwards.size(); ++i)
            payoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i)));

        MultiStepCoterminalSwaptions swaptions = new MultiStepCoterminalSwaptions(rateTimes,
                rateTimes, payoffs);
        product.add(swaps);
        product.add(swaptions);

        SubProductExpectedValues expectedSwaps =
                new SubProductExpectedValues("coterminal swap");
        expectedSwaps.testBias = false;
        expectedSwaps.errorThreshold = 2.32;
        subProductExpectedValues.add(expectedSwaps);
        LMMCurveState curveState = new LMMCurveState(rateTimes);  // not the best way to detect errors in LMMCurveState...
        curveState.setOnForwardRates(todaysForwards);
        List<Double> atmRates = curveState.coterminalSwapRates();
        for (int i = 0; i < todaysForwards.size(); ++i) {
            double expectedNPV = curveState.coterminalSwapAnnuity(0, i)
                    * (atmRates.get(i) - fixedRate) * todaysDiscounts.get(0);
            expectedSwaps.values.add(expectedNPV);
        }
        // we clone the prooduct to be able to finalize it and call evolution function member on it
        MultiProductComposite productClone =
                (MultiProductComposite) product.copy();
        productClone.finalize();
        SubProductExpectedValues expectedSwaptions =
                new SubProductExpectedValues("coterminal swaption");
        expectedSwaptions.testBias = false;
        expectedSwaptions.errorThreshold = 2.32;
        subProductExpectedValues.add(expectedSwaptions);
        final double displacement = 0;
        Matrix jacobian =
                SwapForwardMappings.coterminalSwapZedMatrix(curveState, displacement);
        boolean logNormal = true;

        EvolutionDescription evolution = productClone.evolution();
        int factors = todaysForwards.size();
        MarketModelType marketModelType = MarketModelType.ExponentialCorrelationFlatVolatility;
        MarketModel marketModel =
                makeMarketModel(logNormal, evolution, factors, marketModelType);
        for (int i = 0; i < todaysForwards.size(); ++i) {
            final Matrix forwardsCovariance = marketModel.totalCovariance(i);
            Matrix cotSwapsCovariance =
                    jacobian.multipy(forwardsCovariance).multipy(jacobian.transpose());
            StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i) + displacement);

            double expectedSwaption =
                    new BlackCalculator(payoff,
                            todaysCoterminalSwapRates.get(i) + displacement,
                            Math.sqrt(cotSwapsCovariance.get(i, i)),
                            curveState.coterminalSwapAnnuity(0, i)
                                    * todaysDiscounts.get(0)).value();
            expectedSwaptions.values.add(expectedSwaption);
        }
    }

    @Test
    void testAllMultiStepProducts() {

        setup();

        MultiProductComposite product = new MultiProductComposite();
        List<SubProductExpectedValues> subProductExpectedValues = new ArrayList<>();
        addForwards(product, subProductExpectedValues);
        addOptionLets(product, subProductExpectedValues);
        addCoinitialSwaps(product, subProductExpectedValues);
        addCoterminalSwapsAndSwaptions(product, subProductExpectedValues);
        product.finalize();
        testMultiProductComposite(product, subProductExpectedValues);
    }

    @Test
    void testPeriodAdapter() {

        setup();
        LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(todaysForwards);

        int period = 2;
        int offset = 0;

        LMMCurveState bigRateCS =
                ForwardForwardMappings.RestrictCurveState(cs, period, offset);

        List<Double> swaptionPaymentTimes = new ArrayList<>(bigRateCS.rateTimes());
        swaptionPaymentTimes.remove(swaptionPaymentTimes.size() - 1);

        List<Double> capletPaymentTimes = new ArrayList<>(swaptionPaymentTimes);

        int numberBigRates = bigRateCS.numberOfRates();

        List<StrikedTypePayoff> optionletPayoffs = new ArrayList<>();
        List<StrikedTypePayoff> swaptionPayoffs = new ArrayList<>();
        List<StrikedTypePayoff> displacedOptionletPayoffs = new ArrayList<>();
        List<StrikedTypePayoff> displacedSwaptionPayoffs = new ArrayList<>();

        for (int i = 0; i < numberBigRates; ++i) {
            optionletPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    bigRateCS.forwardRate(i)));
            swaptionPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    bigRateCS.coterminalSwapRate(i)));
            displacedOptionletPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    bigRateCS.forwardRate(i) + displacement));
            displacedSwaptionPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    bigRateCS.coterminalSwapRate(i) + displacement));
        }

        MultiStepPeriodCapletSwaptions theProduct =
                new MultiStepPeriodCapletSwaptions(rateTimes,
                        capletPaymentTimes,
                        swaptionPaymentTimes,
                        optionletPayoffs,
                        swaptionPayoffs,
                        period,
                        offset);

        final EvolutionDescription evolution = theProduct.evolution();

        boolean logNormal = true;
        int factors = 5;

        MarketModel originalModel =
                makeMarketModel(logNormal,
                        evolution,
                        factors,
                        MarketModelType.ExponentialCorrelationAbcdVolatility);

        List<Double> newDisplacements = new ArrayList<>();

        MarketModel adaptedforwardModel = new FwdPeriodAdapter(originalModel,
                period,
                offset,
                newDisplacements);

        MarketModel adaptedSwapModel = new FwdToCotSwapAdapter(adaptedforwardModel);

        Matrix finalForwardCovariances = adaptedforwardModel.totalCovariance(
                adaptedforwardModel.numberOfSteps() - 1);
        Matrix finalSwapCovariances = adaptedSwapModel.totalCovariance(
                adaptedSwapModel.numberOfSteps() - 1);

        double[] adaptedForwardSds = new double[adaptedforwardModel.numberOfRates()];
        double[] adaptedSwapSds = new double[adaptedSwapModel.numberOfRates()];
        double[] approxCapletPrices = new double[adaptedforwardModel.numberOfRates()];
        double[] approxSwaptionPrices = new double[adaptedSwapModel.numberOfRates()];

        for (int j = 0; j < adaptedSwapModel.numberOfRates(); ++j) {
            adaptedForwardSds[j] = Math.sqrt(finalForwardCovariances.get(j, j));
            adaptedSwapSds[j] = Math.sqrt(finalSwapCovariances.get(j, j));

            double capletAnnuity = todaysDiscounts.get(0) * bigRateCS.discountRatio(j + 1, 0)
                    * bigRateCS.rateTaus().get(j);

            approxCapletPrices[j] = new BlackCalculator(displacedOptionletPayoffs.get(j),
                    bigRateCS.forwardRate(j) + displacement,
                    adaptedForwardSds[j],
                    capletAnnuity).value();

            double swaptionAnnuity = todaysDiscounts.get(0)
                    * bigRateCS.coterminalSwapAnnuity(0, j);

            approxSwaptionPrices[j] = new BlackCalculator(displacedSwaptionPayoffs.get(j),
                    bigRateCS.coterminalSwapRate(j) + displacement,
                    adaptedSwapSds[j],
                    swaptionAnnuity).value();
        }
        SobolBrownianGeneratorFactory generatorFactory =
                new SobolBrownianGeneratorFactory(
                        SobolBrownianGeneratorBase.Ordering.Diagonal, seed_);

        MarketModelEvolver evolver = makeMarketModelEvolver(originalModel,
                theProduct.suggestedNumeraires(),
                generatorFactory,
                EvolverType.Pc);

        SequenceStatisticsInc stats = simulate(evolver, theProduct);

        List<Double> results = stats.mean();
        List<Double> errors = stats.errorEstimate();

        double[] capletErrorsInSds = new double[numberBigRates];
        double[] swaptionErrorsInSds = new double[numberBigRates];

        if (2 * numberBigRates != results.size())
            fail("mismatch between the size of the result and the "
                    + "number of results");

        for (int i = 0; i < numberBigRates; ++i) {
            capletErrorsInSds[i] = (results.get(i) - approxCapletPrices[i]) / errors.get(i);
            swaptionErrorsInSds[i] = (results.get(i + numberBigRates)
                    - approxSwaptionPrices[i]) / errors.get(i + numberBigRates);
        }

        double capletTolerance = 4;
        double swaptionTolerance = 4;

        for (int i = 0; i < numberBigRates; ++i) {
            if (Math.abs(capletErrorsInSds[i]) > capletTolerance) {
                fail(Utilities.ordinal(i + 1) + "caplet , approx price "
                        + approxCapletPrices[i]
                        + ", \t simulation price " + results.get(i)
                        + ", \t error in sds " + capletErrorsInSds[i]);
            }
        }
        for (int i = 0; i < numberBigRates; ++i) {
            if (Math.abs(swaptionErrorsInSds[i]) > swaptionTolerance) {
                fail(Utilities.ordinal(i + 1) + "swaption, approx price "
                        + approxSwaptionPrices[i]
                        + ", \t simulation price " + results.get(i + numberBigRates)
                        + ", \t error in sds " + swaptionErrorsInSds[i]);
            }
        }
    }

    @Test
    void testAbcdVolatilityIntegration() {

        setup();

        double a = -0.0597;
        double b = 0.1677;
        double c = 0.5403;
        double d = 0.1710;

        final int N = 10;
        final double precision = 1e-04;

        AbcdFunction instVol = new AbcdFunction(a, b, c, d);
        SegmentIntegral SI = new SegmentIntegral(20000);
        for (int i = 0; i < N; i++) {
            double T1 = 0.5 * (1 + i);     // expiry of forward 1: after T1 AbcdVol = 0
            for (int k = 0; k < N - i; k++) {
                double T2 = 0.5 * (1 + k); // expiry of forward 2: after T2 AbcdVol = 0
                //Integration
                for (int j = 0; j < N; j++) {
                    double xMin = 0.5 * j;
                    for (int l = 0; l < N - j; l++) {
                        double xMax = xMin + 0.5 * l;
                        AbcdSquared abcd2 = new AbcdSquared(a, b, c, d, T1, T2);
                        double numerical = SI.value(abcd2, xMin, xMax);
                        double analytical = instVol.covariance(xMin, xMax, T1, T2);
                        if (Math.abs(analytical - numerical) > precision) {
                            fail("     T1=" + T1 + "," +
                                    "T2=" + T2 + ",\t\t" +
                                    "xMin=" + xMin + "," +
                                    "xMax=" + xMax + ",\t\t" +
                                    "analytical: " + analytical + ",\t" +
                                    "numerical:   " + numerical);
                        }
                        if (T1 == T2) {
                            double variance = instVol.variance(xMin, xMax, T1);
                            if (Math.abs(analytical - variance) > 1e-14) {
                                fail("     T1=" + T1 + "," +
                                        "T2=" + T2 + ",\t\t" +
                                        "xMin=" + xMin + "," +
                                        "xMax=" + xMax + ",\t\t" +
                                        "variance: " + variance + ",\t" +
                                        "analytical: " + analytical);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void testAbcdVolatilityCompare() {

        setup();

        /*
        Given the instantaneous volatilities related to forward expiring at
        rateTimes[i1] and at rateTimes[i2], the methods:
        - LmExtLinearExponentialVolModel::integratedVariance(i1,i2,T)
        - Abcd::covariance(T)
        return the same result only if T < min(rateTimes[i1],rateTimes[i2]).
        */

        // Parameters following Rebonato / Parameters following Brigo-Mercurio
        // used in Abcd class              used in LmExtLinearExponentialVolModel
        double a = 0.0597;                      // --> d
        double b = 0.1677;                      // --> a
        double c = 0.5403;                      // --> b
        double d = 0.1710;                      // --> c

        LmExtLinearExponentialVolModel lmAbcd =
                new LmExtLinearExponentialVolModel(rateTimes, b, c, d, a);
        AbcdFunction abcd = new AbcdFunction(a, b, c, d);
        Array x = new Array(rateTimes.size(), 0.0);
        for (int i1 = 0; i1 < rateTimes.size(); i1++) {
            for (int i2 = 0; i2 < rateTimes.size(); i2++) {
                double T = 0.;
                do {
                    double lmCovariance = lmAbcd.integratedVariance(i1, i2, T, x);
                    double abcdCovariance =
                            abcd.covariance(0, T, rateTimes.get(i1), rateTimes.get(i2));
                    if (Math.abs(lmCovariance - abcdCovariance) > 1e-10) {
                        fail(" T1=" + rateTimes.get(i1) + "," +
                                "T2=" + rateTimes.get(i2) + ",\t\t" +
                                "xMin=" + 0 + "," +
                                "xMax=" + T + ",\t\t" +
                                "abcd: " + abcdCovariance + ",\t" +
                                "lm: " + lmCovariance);
                    }
                    T += 0.5;
                } while (T < Math.min(rateTimes.get(i1), rateTimes.get(i2)));
            }
        }
    }

    @Test
    void testAbcdVolatilityFit() {

        setup();

        AbcdCalibration instVol = new AbcdCalibration(
                new ArrayList<>(rateTimes.subList(0, rateTimes.size() - 1)),
                blackVols,
                -0.06, 0.17, 0.54, 0.17,
                false, false, false, false,
                false, null, null);
        double a0 = instVol.a();
        double b0 = instVol.b();
        double c0 = instVol.c();
        double d0 = instVol.d();
        double error0 = instVol.error();

        instVol.compute();

        EndCriteria.Type ec = instVol.endCriteria();
        double a1 = instVol.a();
        double b1 = instVol.b();
        double c1 = instVol.c();
        double d1 = instVol.d();
        double error1 = instVol.error();

        if (error1 >= error0)
            fail("Parameters:" +
                    "\na:     " + a0 + " ---> " + a1 +
                    "\nb:     " + b0 + " ---> " + b1 +
                    "\nc:     " + c0 + " ---> " + c1 +
                    "\nd:     " + d0 + " ---> " + d1 +
                    "\nerror: " + error0 + " ---> " + error1);

        AbcdFunction abcd = new AbcdFunction(a1, b1, c1, d1);
        List<Double> k = instVol.k(
                new ArrayList<>(rateTimes.subList(0, rateTimes.size() - 1)),
                blackVols);
        double tol = 3.0e-4;
        for (int i = 0; i < blackVols.size(); i++) {
            if (Math.abs(k.get(i) - 1.0) > tol) {
                double modelVol =
                        abcd.volatility(0.0, rateTimes.get(i), rateTimes.get(i));
                fail("\n EndCriteria = " + ec +
                        "\n Fixing Time = " + rateTimes.get(i) +
                        "\n MktVol      = " + rate(blackVols.get(i)) +
                        "\n ModVol      = " + rate(modelVol) +
                        "\n k           = " + k.get(i) +
                        "\n error       = " + Math.abs(k.get(i) - 1.0) +
                        "\n tol         = " + tol);
            }
        }
    }

    @Test
    void testDriftCalculator() {

        // Test full factor drift equivalence between compute() and
        // computeReduced()

        setup();

        double tolerance = 1.0e-16;
        int factors = todaysForwards.size();
        List<Double> evolutionTimes = new ArrayList<>(
                rateTimes.subList(0, rateTimes.size() - 1));
        EvolutionDescription evolution = new EvolutionDescription(rateTimes, evolutionTimes);
        final List<Double> rateTaus = evolution.rateTaus();
        List<Integer> numeraires = EvolutionDescription.moneyMarketPlusMeasure(evolution,
                measureOffset_);
        final List<Integer> alive = evolution.firstAliveRate();
        int numberOfSteps = evolutionTimes.size();
        List<Double> drifts = filledList(numberOfSteps, 0.0);
        List<Double> driftsReduced = filledList(numberOfSteps, 0.0);
        MarketModelType[] marketModels = {
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        for (MarketModelType k : marketModels) { // loop over market models
            boolean logNormal = true;
            MarketModel marketModel = makeMarketModel(logNormal, evolution, factors, k);
            List<Double> displacements = marketModel.displacements();
            for (int j = 0; j < numberOfSteps; ++j) {     // loop over steps
                final Matrix A = marketModel.pseudoRoot(j);
                //System.out.println(Utilities.ordinal(j+1) + " pseudoroot:\n" + A);
                int inf = Math.max(0, alive.get(j));
                for (int h = inf; h < numeraires.size(); ++h) {     // loop over numeraires
                    LMMDriftCalculator driftcalculator = new LMMDriftCalculator(
                            A, displacements, rateTaus,
                            numeraires.get(h), alive.get(j));
                    driftcalculator.computePlain(todaysForwards, drifts);
                    driftcalculator.computeReduced(todaysForwards,
                            driftsReduced);
                    for (int i = 0; i < drifts.size(); ++i) {
                        double error = Math.abs(driftsReduced.get(i) - drifts.get(i));
                        if (error > tolerance)
                            fail("MarketModel: " + marketModelTypeToString(k) + ", "
                                    + Utilities.ordinal(j + 1) + " step, "
                                    + ", " + Utilities.ordinal(h + 1) + " numeraire, "
                                    + ", " + Utilities.ordinal(i + 1) + " drift, "
                                    + "\ndrift        =" + drifts.get(i)
                                    + "\ndriftReduced =" + driftsReduced.get(i)
                                    + "\n       error =" + error
                                    + "\n   tolerance =" + tolerance);
                    }
                }
            }
        }
    }

    @Test
    void testIsInSubset() {

        // Performance test for isInSubset function (temporary)

        setup();

        int dim = 100;
        List<Double> set = new ArrayList<>();
        List<Double> subset = new ArrayList<>();
        for (int i = 0; i < dim; i++) set.add(i * 1.0);
        for (int i = 0; i < dim; i++) subset.add(dim + i * 1.0);
        boolean[] result = Utilities.isInSubset(set, subset);
        if (printReport_) {
            for (int i = 0; i < dim; i++) {
                System.out.println(Utilities.ordinal(i + 1) + ":" +
                        " set[" + i + "] =  " + set.get(i) +
                        ", subset[" + i + "] =  " + subset.get(i) +
                        ", result[" + i + "] =  " + result[i]);
            }
        }
    }

    @Test
    void testAbcdDegenerateCases() {

        AbcdFunction f1 = new AbcdFunction(0.0, 0.0, 1.0E-15, 1.0);
        AbcdFunction f2 = new AbcdFunction(1.0, 0.0, 1.0E-50, 0.0);

        double cov1 = f1.covariance(0.0, 1.0, 1.0, 1.0);
        if (Math.abs(cov1 - 1.0) > 1E-14
                || Double.isNaN(cov1) || Double.isInfinite(cov1))
            fail("(a,b,c,d)=(0,0,0,1): true covariance should be 1.0, "
                    + "error is " + Math.abs(cov1 - 1.0));

        double cov2 = f2.covariance(0.0, 1.0, 1.0, 1.0);
        if (Math.abs(cov2 - 1.0) > 1E-14
                || Double.isNaN(cov2) || Double.isInfinite(cov2))
            fail("(a,b,c,d)=(1,0,0,0): true covariance should be 1.0, "
                    + "error is " + Math.abs(cov2 - 1.0));
    }

    @Test
    void testCovariance() {

        final int n = 10;

        List<Double> rateTimes = new ArrayList<>();
        List<Double> evolTimes1 = new ArrayList<>();
        List<Double> evolTimes2 = new ArrayList<>();
        List<Double> evolTimes3 = new ArrayList<>();
        List<Double> evolTimes4 = new ArrayList<>();
        List<List<Double>> evolTimes = new ArrayList<>();

        for (int i = 1; i <= n; i++) rateTimes.add((double) i);
        evolTimes1.add((double) (n - 1));
        for (int i = 1; i <= n - 1; i++) evolTimes2.add((double) i);
        for (int i = 1; i <= 2 * n - 2; i++) evolTimes3.add(0.5 * i);
        evolTimes4.add(0.3);
        evolTimes4.add(1.3);
        evolTimes4.add(2.0);
        evolTimes4.add(4.5);
        evolTimes4.add(8.2);

        evolTimes.add(evolTimes1);
        evolTimes.add(evolTimes2);
        evolTimes.add(evolTimes3);
        evolTimes.add(evolTimes4);

        List<String> evolNames = new ArrayList<>();
        evolNames.add("one evolution time");
        evolNames.add("evolution times on rate fixings");
        evolNames.add("evolution times on rate fixings and midpoints between fixings");
        evolNames.add("irregular evolution times");

        List<Double> ks = filledList(n - 1, 1.0);
        List<Double> displ = filledList(n - 1, 0.0);
        List<Double> rates = filledList(n - 1, 0.0);
        List<Double> vols = filledList(n - 1, 1.0);

        Matrix c = ExponentialCorrelations.exponentialCorrelations(rateTimes, 0.5, 0.2, 1.0, 0.0);
        PiecewiseConstantCorrelation corr = new TimeHomogeneousForwardCorrelation(c, rateTimes);

        List<String> modelNames = new ArrayList<>();
        modelNames.add("FlatVol");
        modelNames.add("AbcdVol");

        for (int k = 0; k < modelNames.size(); k++) {
            for (int l = 0; l < evolNames.size(); l++) {
                EvolutionDescription evolution = new EvolutionDescription(rateTimes, evolTimes.get(l));
                MarketModel model;
                switch (k) {
                    case 0:
                        model = new FlatVol(vols, corr, evolution, n - 1, rates, displ);
                        break;
                    case 1:
                        model = new AbcdVol(1.0, 0.0, 1.0E-50, 0.0, ks,
                                corr, evolution, n - 1, rates, displ);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown model " + modelNames.get(k));
                }
                if (model != null) {
                    for (int i = 0; i < evolTimes.get(l).size(); i++) {
                        Matrix cov = model.covariance(i);
                        double dt = evolTimes.get(l).get(i)
                                - (i > 0 ? evolTimes.get(l).get(i - 1) : 0.0);
                        for (int x = 0; x < n - 1; x++) {
                            for (int y = 0; y < n - 1; y++) {
                                if (Math.min(rateTimes.get(x), rateTimes.get(y))
                                        >= evolTimes.get(l).get(i)
                                        && Math.abs(cov.get(x, y) - c.get(x, y) * dt) > 1.0E-14)
                                    fail("Model " + modelNames.get(k)
                                            + " with " + evolNames.get(l)
                                            + ": covariance matrix in step " + i
                                            + ": true value at (" + x + "," + y
                                            + ") is " + c.get(x, y) * dt
                                            + " actual value is " + cov.get(x, y));
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkCallableSwap(final SequenceStatisticsInc stats,
                                   final String config) {
        List<Double> results = stats.mean();
        List<Double> errors = stats.errorEstimate();
        double payerNPV = results.get(0);
        double receiverNPV = results.get(1);
        double bermudanNPV = results.get(2);
        double callableNPV = results.get(3);
        double tolerance = 1.1e-15;
        double swapError = Math.abs(receiverNPV + payerNPV);
        double callableError = Math.abs(receiverNPV + bermudanNPV - callableNPV);
        if (swapError > tolerance || bermudanNPV < 0.0 ||
                callableNPV < receiverNPV || callableError > tolerance)
            System.out.println(config);
        if (swapError > tolerance)
            fail("agreement between payer and receiver swap failed:"
                    + "\n    payer swap:    " + payerNPV
                    + "\n    receiver swap: " + receiverNPV
                    + "\n    error:         " + swapError
                    + "\n    tolerance:     " + tolerance);
        if (bermudanNPV < 0.0)
            fail("negative bermudan option value:"
                    + "\n    bermudan:          " + bermudanNPV);
        if (callableNPV < receiverNPV)
            fail("callable receiver less valuable than plain receiver:"
                    + "\n    receiver swap:     " + receiverNPV
                    + "\n    callable:          " + callableNPV);
        if (callableError > tolerance)
            fail("agreement between receiver+bermudan and callable failed:"
                    + "\n    receiver swap:     " + receiverNPV
                    + "\n    bermudan:          " + bermudanNPV
                    + "\n    receiver+bermudan: " + (receiverNPV + bermudanNPV)
                    + "\n    callable:          " + callableNPV
                    + "\n    error:             " + callableError
                    + "\n    tolerance:         " + tolerance);
        if (printReport_) {
            System.out.println(
                    "    payer swap:        " + rate(payerNPV) + " +/- " + rate(errors.get(0))
                            + "\n    receiver swap:     " + rate(receiverNPV) + " +/- " + rate(errors.get(1))
                            + "\n    bermudan:          " + rate(bermudanNPV) + " +/- " + rate(errors.get(2))
                            + "\n    receiver+bermudan: " + rate(receiverNPV + bermudanNPV)
                            + "\n    callable:          " + rate(callableNPV) + " +/- " + rate(errors.get(3)));
        }
    }

    @Test
    void testCallableSwapNaif() {
        System.out.println("Pricing callable swap with naif exercise strategy in a LIBOR market model...");
        setup();
        double fixedRate = 0.04;
        MultiStepSwap payerSwap = new MultiStepSwap(rateTimes, accruals, accruals,
                paymentTimes, fixedRate, true);
        MultiStepSwap receiverSwap = new MultiStepSwap(rateTimes, accruals, accruals,
                paymentTimes, fixedRate, false);
        List<Double> exerciseTimes = new ArrayList<>(rateTimes);
        exerciseTimes.remove(exerciseTimes.size() - 1);
        //std::vector<Rate> exerciseTimes;
        //for (Size i=2; i<rateTimes.size()-1; i+=2)
        //    exerciseTimes.push_back(rateTimes[i]);
        List<Double> swapTriggers = filledList(exerciseTimes.size(), fixedRate);
        SwapRateTrigger naifStrategy = new SwapRateTrigger(rateTimes, swapTriggers,
                exerciseTimes);
        // Official unused LS locals (collectedData, basisCoefficients,
        // NothingExerciseValue control, SwapBasisSystem basisSystem) are omitted:
        // the Naif body does not use them.
        NothingExerciseValue nullRebate = new NothingExerciseValue(rateTimes);
        CallSpecifiedMultiProduct dummyProduct =
                new CallSpecifiedMultiProduct(receiverSwap, naifStrategy,
                        new ExerciseAdapter(nullRebate));
        EvolutionDescription evolution = dummyProduct.evolution();
        MarketModelType[] marketModels = {
                // CalibratedMM,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        for (MarketModelType j : marketModels) {
            int[] testedFactors = {4, // 8,
                    todaysForwards.size()};
            for (int factors : testedFactors) {
                // Composite's ProductSuggested is the Terminal one
                MeasureType[] measures = {
                        // ProductSuggested,
                        MeasureType.MoneyMarketPlus
                        // MoneyMarket,
                        // Terminal
                };
                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(dummyProduct, measure);
                    boolean logNormal = true;
                    MarketModel marketModel =
                            makeMarketModel(logNormal, evolution, factors, j);
                    EvolverType[] evolvers = {EvolverType.Pc, EvolverType.Balland,
                            EvolverType.Ipc};
                    MarketModelEvolver evolver;
                    int stop = EvolutionDescription.isInTerminalMeasure(evolution,
                            numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; i++) {
                        for (int n = 0; n < 1; n++) {
                            // MTBrownianGeneratorFactory generatorFactory(seed_);
                            SobolBrownianGeneratorFactory generatorFactory =
                                    new SobolBrownianGeneratorFactory(
                                            SobolBrownianGeneratorBase.Ordering.Diagonal,
                                            seed_);
                            evolver = makeMarketModelEvolver(marketModel, numeraires,
                                    generatorFactory, evolvers[i]);
                            String config = configString(j, factors, measure,
                                    evolvers[i]);
                            if (printReport_)
                                System.out.println("    " + config);
                            // use the naif strategy
                            // 2. bermudan swaption to enter into the payer swap
                            CallSpecifiedMultiProduct bermudanProduct =
                                    new CallSpecifiedMultiProduct(
                                            new MultiStepNothing(evolution),
                                            naifStrategy, payerSwap);
                            // 3. callable receiver swap
                            CallSpecifiedMultiProduct callableProduct =
                                    new CallSpecifiedMultiProduct(receiverSwap,
                                            naifStrategy,
                                            new ExerciseAdapter(nullRebate));
                            // lower bound: evolve all 4 products togheter
                            MultiProductComposite allProducts =
                                    new MultiProductComposite();
                            allProducts.add(payerSwap);
                            allProducts.add(receiverSwap);
                            allProducts.add(bermudanProduct);
                            allProducts.add(callableProduct);
                            allProducts.finalize();
                            SequenceStatisticsInc stats = simulate(evolver,
                                    allProducts);
                            checkCallableSwap(stats, config);
                            // upper bound
                            // MTBrownianGeneratorFactory uFactory(seed_+142);
                            SobolBrownianGeneratorFactory uFactory =
                                    new SobolBrownianGeneratorFactory(
                                            SobolBrownianGeneratorBase.Ordering.Diagonal,
                                            seed_ + 142);
                            evolver = makeMarketModelEvolver(marketModel, numeraires,
                                    uFactory, evolvers[i]);
                            List<MarketModelEvolver> innerEvolvers = new ArrayList<>();
                            boolean[] isExerciseTime = Utilities.isInSubset(
                                    evolution.evolutionTimes(),
                                    naifStrategy.exerciseTimes());
                            for (int s = 0; s < isExerciseTime.length; ++s) {
                                if (isExerciseTime[s]) {
                                    MTBrownianGeneratorFactory iFactory =
                                            new MTBrownianGeneratorFactory(seed_ + s);
                                    MarketModelEvolver e = makeMarketModelEvolver(
                                            marketModel, numeraires, iFactory,
                                            evolvers[i], s);
                                    innerEvolvers.add(e);
                                }
                            }
                            int initialNumeraire = evolver.numeraires().get(0);
                            double initialNumeraireValue =
                                    todaysDiscounts.get(initialNumeraire);
                            UpperBoundEngine uEngine = new UpperBoundEngine(
                                    evolver, innerEvolvers, receiverSwap, nullRebate,
                                    receiverSwap, nullRebate, naifStrategy,
                                    initialNumeraireValue);
                            GenericRiskStatistics uStats = new GenericRiskStatistics();
                            uEngine.multiplePathValues(uStats, 255, 256);
                            double delta = uStats.mean();
                            double deltaError = uStats.errorEstimate();
                            if (printReport_)
                                System.out.println("    upper bound delta: "
                                        + rate(delta) + " +- " + rate(deltaError));
                        }
                    }
                }
            }
        }
    }

    @Test
    void testCallableSwapLS() {
        System.out.println("Pricing callable swap with Longstaff-Schwartz exercise "
                + "strategy in a LIBOR market model...");
        setup();

        double fixedRate = 0.04;

        // 0. a payer swap
        MultiStepSwap payerSwap = new MultiStepSwap(rateTimes, accruals, accruals,
                paymentTimes, fixedRate, true);

        // 1. the equivalent receiver swap
        MultiStepSwap receiverSwap = new MultiStepSwap(rateTimes, accruals, accruals,
                paymentTimes, fixedRate, false);

        // exercise schedule
        List<Double> exerciseTimes = new ArrayList<>(rateTimes);
        exerciseTimes.remove(exerciseTimes.size() - 1);
        //std::vector<Rate> exerciseTimes;
        //for (Size i=2; i<rateTimes.size()-1; i+=2)
        //    exerciseTimes.push_back(rateTimes[i]);

        // naif exercise strategy
        List<Double> swapTriggers = filledList(exerciseTimes.size(), fixedRate);
        SwapRateTrigger naifStrategy = new SwapRateTrigger(rateTimes, swapTriggers,
                exerciseTimes);

        // Longstaff-Schwartz exercise strategy
        List<List<NodeData>> collectedData = new ArrayList<>();
        List<List<Double>> basisCoefficients = new ArrayList<>();
        NothingExerciseValue control = new NothingExerciseValue(rateTimes);
        SwapBasisSystem basisSystem = new SwapBasisSystem(rateTimes, exerciseTimes);
        NothingExerciseValue nullRebate = new NothingExerciseValue(rateTimes);

        CallSpecifiedMultiProduct dummyProduct =
                new CallSpecifiedMultiProduct(receiverSwap, naifStrategy,
                        new ExerciseAdapter(nullRebate));

        EvolutionDescription evolution = dummyProduct.evolution();

        MarketModelType[] marketModels = {
                // CalibratedMM,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        for (MarketModelType j : marketModels) {

            int[] testedFactors = {4, // 8,
                    todaysForwards.size()};
            for (int factors : testedFactors) {
                // Composite's ProductSuggested is the Terminal one
                MeasureType[] measures = {
                        // ProductSuggested,
                        // MoneyMarketPlus,
                        MeasureType.MoneyMarket
                        // Terminal
                };
                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(dummyProduct, measure);

                    boolean logNormal = true;
                    MarketModel marketModel =
                            makeMarketModel(logNormal, evolution, factors, j);

                    EvolverType[] evolvers = {EvolverType.Pc, EvolverType.Balland,
                            EvolverType.Ipc};
                    MarketModelEvolver evolver;
                    int stop = EvolutionDescription.isInTerminalMeasure(evolution,
                            numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; i++) {

                        for (int n = 0; n < 1; n++) {
                            // MTBrownianGeneratorFactory generatorFactory(seed_);
                            SobolBrownianGeneratorFactory generatorFactory =
                                    new SobolBrownianGeneratorFactory(
                                            SobolBrownianGeneratorBase.Ordering.Diagonal,
                                            seed_);

                            evolver = makeMarketModelEvolver(marketModel, numeraires,
                                    generatorFactory, evolvers[i]);
                            String config = configString(j, factors, measure,
                                    evolvers[i]);
                            if (printReport_)
                                System.out.println("    " + config);

                            // calculate the exercise strategy
                            CollectNodeData.collectNodeData(evolver, receiverSwap,
                                    basisSystem, nullRebate, control,
                                    trainingPaths_, collectedData);
                            GenericLongstaffSchwartzRegression
                                    .genericLongstaffSchwartzRegression(
                                            collectedData, basisCoefficients);
                            LongstaffSchwartzExerciseStrategy exerciseStrategy =
                                    new LongstaffSchwartzExerciseStrategy(
                                            basisSystem, basisCoefficients, evolution,
                                            numeraires, nullRebate, control);

                            // 2. bermudan swaption to enter into the payer swap
                            CallSpecifiedMultiProduct bermudanProduct =
                                    new CallSpecifiedMultiProduct(
                                            new MultiStepNothing(evolution),
                                            exerciseStrategy, payerSwap);

                            // 3. callable receiver swap
                            CallSpecifiedMultiProduct callableProduct =
                                    new CallSpecifiedMultiProduct(receiverSwap,
                                            exerciseStrategy,
                                            new ExerciseAdapter(nullRebate));

                            // lower bound: evolve all 4 products togheter
                            MultiProductComposite allProducts =
                                    new MultiProductComposite();
                            allProducts.add(payerSwap);
                            allProducts.add(receiverSwap);
                            allProducts.add(bermudanProduct);
                            allProducts.add(callableProduct);
                            allProducts.finalize();

                            SequenceStatisticsInc stats = simulate(evolver,
                                    allProducts);
                            checkCallableSwap(stats, config);

                            // upper bound

                            // MTBrownianGeneratorFactory uFactory(seed_+142);
                            SobolBrownianGeneratorFactory uFactory =
                                    new SobolBrownianGeneratorFactory(
                                            SobolBrownianGeneratorBase.Ordering.Diagonal,
                                            seed_ + 142);
                            evolver = makeMarketModelEvolver(marketModel, numeraires,
                                    uFactory, evolvers[i]);

                            List<MarketModelEvolver> innerEvolvers = new ArrayList<>();

                            boolean[] isExerciseTime = Utilities.isInSubset(
                                    evolution.evolutionTimes(),
                                    exerciseStrategy.exerciseTimes());
                            for (int s = 0; s < isExerciseTime.length; ++s) {
                                if (isExerciseTime[s]) {
                                    MTBrownianGeneratorFactory iFactory =
                                            new MTBrownianGeneratorFactory(seed_ + s);
                                    MarketModelEvolver e = makeMarketModelEvolver(
                                            marketModel, numeraires, iFactory,
                                            evolvers[i], s);
                                    innerEvolvers.add(e);
                                }
                            }

                            int initialNumeraire = evolver.numeraires().get(0);
                            double initialNumeraireValue =
                                    todaysDiscounts.get(initialNumeraire);

                            UpperBoundEngine uEngine = new UpperBoundEngine(
                                    evolver, innerEvolvers, receiverSwap, nullRebate,
                                    receiverSwap, nullRebate, exerciseStrategy,
                                    initialNumeraireValue);
                            GenericRiskStatistics uStats = new GenericRiskStatistics();
                            uEngine.multiplePathValues(uStats, 255, 256);
                            double delta = uStats.mean();
                            double deltaError = uStats.errorEstimate();
                            if (printReport_)
                                System.out.println("    upper bound delta: "
                                        + rate(delta) + " +- " + rate(deltaError));
                        }
                    }
                }
            }
        }
    }

    @Test
    void testCallableSwapAnderson() {
        // Official BOOST_AUTO_TEST_CASE_TEMPLATE over the six `slices`:
        // Flat x {4, 8, 0}, Abcd x {4, 8, 0}, in this order, where 0 means
        // todaysForwards.size() (full factors).
        MarketModelType[] marketModelTypes = {
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        int[] testedFactorCodes = {4, 8, 0, 4, 8, 0};

        for (int slice = 0; slice < testedFactorCodes.length; ++slice) {
            setup();

            MarketModelType marketModelType = marketModelTypes[slice];
            int testedFactor = testedFactorCodes[slice] != 0
                    ? testedFactorCodes[slice]
                    : todaysForwards.size();

            System.out.println("Pricing callable swap with Anderson exercise "
                    + "strategy in a LIBOR market model for test factor "
                    + testedFactor + " and model type "
                    + marketModelTypeToString(marketModelType)
                    + "...");

            setup();

            double fixedRate = 0.04;

            // 0. a payer swap
            MultiStepSwap payerSwap = new MultiStepSwap(rateTimes, accruals, accruals,
                    paymentTimes, fixedRate, true);

            // 1. the equivalent receiver swap
            MultiStepSwap receiverSwap = new MultiStepSwap(rateTimes, accruals, accruals,
                    paymentTimes, fixedRate, false);

            // exercise schedule
            List<Double> exerciseTimes = new ArrayList<>(rateTimes);
            exerciseTimes.remove(exerciseTimes.size() - 1);
            //std::vector<Rate> exerciseTimes;
            //for (Size i=2; i<rateTimes.size()-1; i+=2)
            //    exerciseTimes.push_back(rateTimes[i]);

            // naif exercise strategy
            List<Double> swapTriggers = filledList(exerciseTimes.size(), fixedRate);
            SwapRateTrigger naifStrategy = new SwapRateTrigger(rateTimes, swapTriggers,
                    exerciseTimes);

            // Anderson exercise strategy
            List<List<NodeData>> collectedData = new ArrayList<>();
            List<List<Double>> parameters = new ArrayList<>();
            NothingExerciseValue control = new NothingExerciseValue(rateTimes);
            NothingExerciseValue nullRebate = new NothingExerciseValue(rateTimes);
            TriggeredSwapExercise parametricForm = new TriggeredSwapExercise(
                    rateTimes, exerciseTimes,
                    filledList(exerciseTimes.size(), fixedRate));

            CallSpecifiedMultiProduct dummyProduct =
                    new CallSpecifiedMultiProduct(receiverSwap, naifStrategy,
                            new ExerciseAdapter(nullRebate));

            EvolutionDescription evolution = dummyProduct.evolution();

            int factors = testedFactor;

            // Composite's ProductSuggested is the Terminal one
            MeasureType[] measures = {
                    // ProductSuggested,
                    // MoneyMarketPlus,
                    // MoneyMarket,
                    MeasureType.Terminal
            };
            for (MeasureType measure : measures) {
                List<Integer> numeraires = makeMeasure(dummyProduct, measure);
                boolean logNormal = true;
                MarketModel marketModel =
                        makeMarketModel(logNormal, evolution, factors,
                                marketModelType);
                EvolverType[] evolvers = {EvolverType.Pc, EvolverType.Balland,
                        EvolverType.Ipc};
                MarketModelEvolver evolver;
                int stop = EvolutionDescription.isInTerminalMeasure(evolution,
                        numeraires) ? 0 : 1;
                for (int i = 0; i < evolvers.length - stop; i++) {
                    for (int n = 0; n < 1; n++) {
                        //MTBrownianGeneratorFactory generatorFactory(seed_);
                        SobolBrownianGeneratorFactory generatorFactory =
                                new SobolBrownianGeneratorFactory(
                                        SobolBrownianGeneratorBase.Ordering.Diagonal,
                                        seed_);
                        evolver = makeMarketModelEvolver(marketModel, numeraires,
                                generatorFactory, evolvers[i]);
                        String config = configString(marketModelType, factors,
                                measure, evolvers[i]);
                        if (printReport_)
                            System.out.println("    " + config);
                        // 1. calculate the exercise strategy
                        CollectNodeData.collectNodeData(evolver, receiverSwap,
                                parametricForm, nullRebate, control,
                                trainingPaths_, collectedData);
                        Simplex om = new Simplex(0.01);
                        EndCriteria ec = new EndCriteria(1000, 100, 1e-8, 1e-16,
                                1e-8);
                        int initialNumeraire = evolver.numeraires().get(0);
                        double initialNumeraireValue =
                                todaysDiscounts.get(initialNumeraire);
                        double firstPassValue = GenericEarlyExerciseOptimization
                                .genericEarlyExerciseOptimization(
                                        collectedData, parametricForm, parameters,
                                        ec, om)
                                * initialNumeraireValue;
                        if (printReport_)
                            System.out.println("    initial estimate:  "
                                    + rate(firstPassValue));
                        ParametricExerciseAdapter exerciseStrategy =
                                new ParametricExerciseAdapter(parametricForm,
                                        parameters);

                        // 2. bermudan swaption to enter into the payer swap
                        CallSpecifiedMultiProduct bermudanProduct =
                                new CallSpecifiedMultiProduct(
                                        new MultiStepNothing(evolution),
                                        exerciseStrategy, payerSwap);

                        // 3. callable receiver swap
                        CallSpecifiedMultiProduct callableProduct =
                                new CallSpecifiedMultiProduct(receiverSwap,
                                        exerciseStrategy,
                                        new ExerciseAdapter(nullRebate));
                        // lower bound: evolve all 4 products togheter
                        MultiProductComposite allProducts =
                                new MultiProductComposite();
                        allProducts.add(payerSwap);
                        allProducts.add(receiverSwap);
                        allProducts.add(bermudanProduct);
                        allProducts.add(callableProduct);
                        allProducts.finalize();
                        SequenceStatisticsInc stats = simulate(evolver,
                                allProducts);
                        checkCallableSwap(stats, config);

                        // upper bound
                        //MTBrownianGeneratorFactory uFactory(seed_+142);
                        SobolBrownianGeneratorFactory uFactory =
                                new SobolBrownianGeneratorFactory(
                                        SobolBrownianGeneratorBase.Ordering.Diagonal,
                                        seed_ + 142);
                        evolver = makeMarketModelEvolver(marketModel, numeraires,
                                uFactory, evolvers[i]);
                        List<MarketModelEvolver> innerEvolvers = new ArrayList<>();
                        boolean[] isExerciseTime = Utilities.isInSubset(
                                evolution.evolutionTimes(),
                                exerciseStrategy.exerciseTimes());
                        for (int s = 0; s < isExerciseTime.length; ++s) {
                            if (isExerciseTime[s]) {
                                MTBrownianGeneratorFactory iFactory =
                                        new MTBrownianGeneratorFactory(seed_ + s);
                                MarketModelEvolver e = makeMarketModelEvolver(
                                        marketModel, numeraires, iFactory,
                                        evolvers[i], s);
                                innerEvolvers.add(e);
                            }
                        }
                        UpperBoundEngine uEngine = new UpperBoundEngine(
                                evolver, innerEvolvers, receiverSwap, nullRebate,
                                receiverSwap, nullRebate, exerciseStrategy,
                                initialNumeraireValue);
                        GenericRiskStatistics uStats = new GenericRiskStatistics();
                        uEngine.multiplePathValues(uStats, 255, 256);
                        double delta = uStats.mean();
                        double deltaError = uStats.errorEstimate();
                        if (printReport_)
                            System.out.println("    upper bound delta: "
                                    + rate(delta) + " +- " + rate(deltaError));
                    }
                }
            }
        }
    }

    private String configString(MarketModelType j, int factors,
                                MeasureType measure, EvolverType evolver) {
        return marketModelTypeToString(j) + ", " + factors
                + (factors > 1
                ? (factors == todaysForwards.size()
                ? " (full) factors, "
                : " factors, ")
                : " factor,")
                + measureTypeToString(measure) + ", "
                + evolverTypeToString(evolver) + ", "
                + "MT BGF";
    }

    // Official io::percent / io::rate formatting (both print value * 100 with
    // the stream's default fixed precision of 6 digits, followed by " %").
    private static String rate(double value) {
        return String.format(Locale.ROOT, "%.6f %%", value * 100.0);
    }

    // Official test-suite utilities.hpp operator<< for vector_streamer.
    private static String toStream(List<Integer> values) {
        StringBuilder result = new StringBuilder("{ ");
        for (int i = 0; i < values.size(); ++i) {
            if (i > 0)
                result.append(", ");
            result.append(values.get(i));
        }
        return result.append(" }").toString();
    }

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }
}
