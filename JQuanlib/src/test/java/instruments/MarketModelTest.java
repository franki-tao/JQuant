package instruments;

import jquant.Handle;
import jquant.Option;
import jquant.Payoff;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.legacy.libormarketmodels.LmExtLinearExponentialVolModel;
import jquant.math.Array;
import jquant.math.MathUtils;
import jquant.math.Matrix;
import jquant.math.integrals.FourierIntegration;
import jquant.math.integrals.SegmentIntegral;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.Simplex;
import jquant.math.statistics.GenericRiskStatistics;
import jquant.methods.montecarlo.GenericEarlyExerciseOptimization;
import jquant.methods.montecarlo.GenericLongstaffSchwartzRegression;
import jquant.methods.montecarlo.NodeData;
import jquant.models.equity.HestonModel;
import jquant.models.marketmodels.AccountingEngine;
import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.BrownianGeneratorFactory;
import jquant.models.marketmodels.ConstrainedEvolver;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.ForwardForwardMappings;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.MarketModelEvolver;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.MarketModelPathwiseMultiProduct;
import jquant.models.marketmodels.PathwiseAccountingEngine;
import jquant.models.marketmodels.PathwiseVegasAccountingEngine;
import jquant.models.marketmodels.PathwiseVegasOuterAccountingEngine;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.ProxyGreekEngine;
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
import jquant.models.marketmodels.evolvers.LogNormalFwdRateEuler;
import jquant.models.marketmodels.evolvers.LogNormalFwdRateEulerConstrained;
import jquant.models.marketmodels.evolvers.LogNormalFwdRateIpc;
import jquant.models.marketmodels.evolvers.LogNormalFwdRatePc;
import jquant.models.marketmodels.evolvers.MarketModelVolProcess;
import jquant.models.marketmodels.evolvers.NormalFwdRatePc;
import jquant.models.marketmodels.evolvers.SVDDFwdRatePc;
import jquant.models.marketmodels.evolvers.volprocesses.SquareRootAndersen;
import jquant.models.marketmodels.models.AbcdVol;
import jquant.models.marketmodels.models.FlatVol;
import jquant.models.marketmodels.models.FwdPeriodAdapter;
import jquant.models.marketmodels.models.FwdToCotSwapAdapter;
import jquant.models.marketmodels.models.PseudoRootFacade;
import jquant.models.marketmodels.pathwisegreeks.CapPseudoDerivative;
import jquant.models.marketmodels.pathwisegreeks.OrthogonalizedBumpFinder;
import jquant.models.marketmodels.pathwisegreeks.RatePseudoRootJacobian;
import jquant.models.marketmodels.pathwisegreeks.RatePseudoRootJacobianAllElements;
import jquant.models.marketmodels.pathwisegreeks.RatePseudoRootJacobianNumerical;
import jquant.models.marketmodels.pathwisegreeks.SwaptionPseudoDerivative;
import jquant.models.marketmodels.pathwisegreeks.VegaBumpCollection;
import jquant.models.marketmodels.pathwisegreeks.VolatilityBumpInstrumentJacobian;
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
import jquant.models.marketmodels.products.pathwise.MarketModelPathwiseCoterminalSwaptionsDeflated;
import jquant.models.marketmodels.products.pathwise.MarketModelPathwiseCoterminalSwaptionsNumericalDeflated;
import jquant.models.marketmodels.products.pathwise.MarketModelPathwiseInverseFloater;
import jquant.models.marketmodels.products.pathwise.MarketModelPathwiseMultiCaplet;
import jquant.models.marketmodels.products.pathwise.MarketModelPathwiseMultiDeflatedCap;
import jquant.models.marketmodels.products.pathwise.MarketModelPathwiseMultiDeflatedCaplet;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.AnalyticHestonEngine;
import jquant.processes.HestonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.AbcdCalibration;
import jquant.termstructures.volatility.AbcdFunction;
import jquant.termstructures.volatility.AbcdSquared;
import jquant.termstructures.yield.FlatForward;
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

    @Test
    void testGreeks() {
        System.out.println("Testing caplet greeks in a lognormal forward rate "
                + "market model using partial proxy simulation...");

        setup();

        List<Payoff> payoffs = new ArrayList<>();
        List<StrikedTypePayoff> displacedPayoffs = new ArrayList<>();
        for (int i = 0; i < todaysForwards.size(); ++i) {
            payoffs.add(new
                    //PlainVanillaPayoff(Option.Type.Call, todaysForwards.get(i)));
                    CashOrNothingPayoff(Option.Type.Call, todaysForwards.get(i), 0.01));
            displacedPayoffs.add(new
                    //PlainVanillaPayoff(Option.Type.Call, todaysForwards.get(i)+displacement));
                    CashOrNothingPayoff(Option.Type.Call,
                            todaysForwards.get(i) + displacement, 0.01));
        }

        MultiStepOptionlets product = new MultiStepOptionlets(rateTimes, accruals,
                paymentTimes, payoffs);

        EvolutionDescription evolution = product.evolution();

        MarketModelType[] marketModels = {
                // CalibratedMM,
                // ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};
        for (MarketModelType j : marketModels) {

            int[] testedFactors = {4, 8, todaysForwards.size()};
            for (int factors : testedFactors) {
                MeasureType[] measures = {
                        // MoneyMarketPlus,
                        MeasureType.MoneyMarket
                        // Terminal
                };
                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(product, measure);

                    for (int n = 0; n < 1; n++) {
                        // MTBrownianGeneratorFactory generatorFactory(seed_);
                        SobolBrownianGeneratorFactory generatorFactory =
                                new SobolBrownianGeneratorFactory(
                                        SobolBrownianGeneratorBase.Ordering.Diagonal,
                                        seed_);

                        boolean logNormal = true;
                        MarketModel marketModel =
                                makeMarketModel(logNormal, evolution, factors, j);
                        MarketModelEvolver evolver = new LogNormalFwdRateEuler(
                                marketModel, generatorFactory, numeraires);
                        SequenceStatisticsInc stats =
                                new SequenceStatisticsInc(product.numberOfProducts());

                        List<Integer> startIndexOfConstraint = new ArrayList<>();
                        List<Integer> endIndexOfConstraint = new ArrayList<>();

                        for (int i = 0; i < evolution.evolutionTimes().size(); ++i) {
                            startIndexOfConstraint.add(i);
                            endIndexOfConstraint.add(i + 1);
                        }

                        List<List<ConstrainedEvolver>> constrainedEvolvers =
                                new ArrayList<>();
                        List<List<List<Double>>> diffWeights = new ArrayList<>();
                        List<List<SequenceStatisticsInc>> greekStats =
                                new ArrayList<>();

                        List<ConstrainedEvolver> deltaGammaEvolvers = new ArrayList<>();
                        List<List<Double>> deltaGammaWeights = new ArrayList<>();
                        deltaGammaWeights.add(filledList(3, 0.0));
                        deltaGammaWeights.add(filledList(3, 0.0));
                        List<SequenceStatisticsInc> deltaGammaStats =
                                new ArrayList<>();
                        deltaGammaStats.add(
                                new SequenceStatisticsInc(product.numberOfProducts()));
                        deltaGammaStats.add(
                                new SequenceStatisticsInc(product.numberOfProducts()));

                        double forwardBump = 1.0e-6;
                        marketModel = makeMarketModel(logNormal, evolution, factors,
                                j, -forwardBump, 0.0);
                        deltaGammaEvolvers.add(
                                new LogNormalFwdRateEulerConstrained(marketModel,
                                        generatorFactory, numeraires));
                        deltaGammaEvolvers.get(deltaGammaEvolvers.size() - 1)
                                .setConstraintType(startIndexOfConstraint,
                                        endIndexOfConstraint);
                        marketModel = makeMarketModel(logNormal, evolution, factors,
                                j, forwardBump, 0.0);
                        deltaGammaEvolvers.add(
                                new LogNormalFwdRateEulerConstrained(marketModel,
                                        generatorFactory, numeraires));
                        deltaGammaEvolvers.get(deltaGammaEvolvers.size() - 1)
                                .setConstraintType(startIndexOfConstraint,
                                        endIndexOfConstraint);

                        deltaGammaWeights.get(0).set(0, 0.0);
                        deltaGammaWeights.get(0).set(1, -1.0 / (2.0 * forwardBump));
                        deltaGammaWeights.get(0).set(2, 1.0 / (2.0 * forwardBump));

                        deltaGammaWeights.get(1).set(0,
                                -2.0 / (forwardBump * forwardBump));
                        deltaGammaWeights.get(1).set(1,
                                1.0 / (forwardBump * forwardBump));
                        deltaGammaWeights.get(1).set(2,
                                1.0 / (forwardBump * forwardBump));

                        List<ConstrainedEvolver> vegaEvolvers = new ArrayList<>();
                        List<List<Double>> vegaWeights = new ArrayList<>();
                        vegaWeights.add(filledList(3, 0.0));
                        List<SequenceStatisticsInc> vegaStats = new ArrayList<>();
                        vegaStats.add(
                                new SequenceStatisticsInc(product.numberOfProducts()));

                        double volBump = 1.0e-4;
                        marketModel = makeMarketModel(logNormal, evolution, factors,
                                j, 0.0, -volBump);
                        vegaEvolvers.add(new LogNormalFwdRateEulerConstrained(
                                marketModel, generatorFactory, numeraires));
                        vegaEvolvers.get(vegaEvolvers.size() - 1)
                                .setConstraintType(startIndexOfConstraint,
                                        endIndexOfConstraint);
                        marketModel = makeMarketModel(logNormal, evolution, factors,
                                j, 0.0, volBump);
                        vegaEvolvers.add(new LogNormalFwdRateEulerConstrained(
                                marketModel, generatorFactory, numeraires));
                        vegaEvolvers.get(vegaEvolvers.size() - 1)
                                .setConstraintType(startIndexOfConstraint,
                                        endIndexOfConstraint);

                        vegaWeights.get(0).set(0, 0.0);
                        vegaWeights.get(0).set(1, -1.0 / (2.0 * volBump));
                        vegaWeights.get(0).set(2, 1.0 / (2.0 * volBump));

                        constrainedEvolvers.add(deltaGammaEvolvers);
                        diffWeights.add(deltaGammaWeights);
                        greekStats.add(deltaGammaStats);

                        constrainedEvolvers.add(vegaEvolvers);
                        diffWeights.add(vegaWeights);
                        greekStats.add(vegaStats);

                        String config = marketModelTypeToString(j) + ", " + factors
                                + (factors > 1
                                ? (factors == todaysForwards.size()
                                ? " (full) factors, "
                                : " factors, ")
                                : " factor,")
                                + measureTypeToString(measure) + ", "
                                + "MT BGF";
                        if (printReport_)
                            System.out.println("    " + config);

                        int initialNumeraire = evolver.numeraires().get(0);
                        double initialNumeraireValue =
                                todaysDiscounts.get(initialNumeraire);

                        ProxyGreekEngine engine = new ProxyGreekEngine(evolver,
                                constrainedEvolvers, diffWeights,
                                startIndexOfConstraint, endIndexOfConstraint,
                                product, initialNumeraireValue);

                        engine.multiplePathValues(stats, greekStats, paths_);

                        List<Double> values = stats.mean();
                        List<Double> errors = stats.errorEstimate();
                        List<Double> deltas = greekStats.get(0).get(0).mean();
                        List<Double> deltaErrors =
                                greekStats.get(0).get(0).errorEstimate();
                        List<Double> gammas = greekStats.get(0).get(1).mean();
                        List<Double> gammaErrors =
                                greekStats.get(0).get(1).errorEstimate();
                        List<Double> vegas = greekStats.get(1).get(0).mean();
                        List<Double> vegaErrors =
                                greekStats.get(1).get(0).errorEstimate();

                        List<Double> discPlus = filledList(
                                todaysForwards.size() + 1,
                                todaysDiscounts.get(0));
                        List<Double> discMinus = filledList(
                                todaysForwards.size() + 1,
                                todaysDiscounts.get(0));
                        List<Double> fwdPlus = filledList(
                                todaysForwards.size(), 0.0);
                        List<Double> fwdMinus = filledList(
                                todaysForwards.size(), 0.0);
                        List<Double> pricePlus = filledList(
                                todaysForwards.size(), 0.0);
                        List<Double> price0 = filledList(
                                todaysForwards.size(), 0.0);
                        List<Double> priceMinus = filledList(
                                todaysForwards.size(), 0.0);
                        for (int i = 0; i < todaysForwards.size(); ++i) {
                            double tau = rateTimes.get(i + 1) - rateTimes.get(i);
                            fwdPlus.set(i, todaysForwards.get(i) + forwardBump);
                            fwdMinus.set(i, todaysForwards.get(i) - forwardBump);
                            discPlus.set(i + 1, discPlus.get(i)
                                    / (1.0 + fwdPlus.get(i) * tau));
                            discMinus.set(i + 1, discMinus.get(i)
                                    / (1.0 + fwdMinus.get(i) * tau));
                            pricePlus.set(i, new BlackCalculator(
                                    displacedPayoffs.get(i), fwdPlus.get(i),
                                    volatilities.get(i) * Math.sqrt(rateTimes.get(i)),
                                    discPlus.get(i + 1) * tau)
                                    .value());
                            price0.set(i, new BlackCalculator(
                                    displacedPayoffs.get(i), todaysForwards.get(i),
                                    volatilities.get(i) * Math.sqrt(rateTimes.get(i)),
                                    todaysDiscounts.get(i + 1) * tau)
                                    .value());
                            priceMinus.set(i, new BlackCalculator(
                                    displacedPayoffs.get(i), fwdMinus.get(i),
                                    volatilities.get(i) * Math.sqrt(rateTimes.get(i)),
                                    discMinus.get(i + 1) * tau)
                                    .value());
                        }

                        for (int i = 0; i < product.numberOfProducts(); ++i) {
                            double numDelta = (pricePlus.get(i) - priceMinus.get(i))
                                    / (2.0 * forwardBump);
                            double numGamma = (pricePlus.get(i)
                                    - 2 * price0.get(i) + priceMinus.get(i))
                                    / (forwardBump * forwardBump);
                            if (printReport_) {
                                System.out.println(Utilities.ordinal(i + 1)
                                        + " caplet: "
                                        + "value = " + price0.get(i) + ", "
                                        + "delta = " + numDelta + ", "
                                        + "gamma = " + numGamma);
                                System.out.println(Utilities.ordinal(i + 1)
                                        + " caplet: "
                                        + "value = " + values.get(i) + " +- "
                                        + errors.get(i) + " ("
                                        + (values.get(i) - price0.get(i))
                                        / errors.get(i) + " s.e.), "
                                        + "delta = " + deltas.get(i) + " +- "
                                        + deltaErrors.get(i) + " ("
                                        + (deltas.get(i) - numDelta)
                                        / deltaErrors.get(i) + " s.e.), "
                                        + "gamma = " + gammas.get(i) + " +- "
                                        + gammaErrors.get(i) + " ("
                                        + (gammas.get(i) - numGamma)
                                        / gammaErrors.get(i) + " s.e.), "
                                        + "vega = " + vegas.get(i) + " +- "
                                        + vegaErrors.get(i));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void testPathwiseGreeks() {
        System.out.println("Testing caplet deltas in a lognormal forward rate "
                + "market model using pathwise method...");

        setup();

        List<Payoff> payoffs = new ArrayList<>();
        List<StrikedTypePayoff> displacedPayoffs = new ArrayList<>();
        for (int i = 0; i < todaysForwards.size(); ++i) {
            payoffs.add(new
                    PlainVanillaPayoff(Option.Type.Call, todaysForwards.get(i)));
            //CashOrNothingPayoff(Option.Type.Call, todaysForwards.get(i), 0.01));
            displacedPayoffs.add(new
                    PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i) + displacement));
            //CashOrNothingPayoff(Option.Type.Call, todaysForwards.get(i)+displacement, 0.01));
        }

        for (int whichProduct = 0; whichProduct < 2; ++whichProduct) {
            MarketModelPathwiseMultiDeflatedCaplet product1 =
                    new MarketModelPathwiseMultiDeflatedCaplet(rateTimes, accruals,
                            paymentTimes, todaysForwards);

            MarketModelPathwiseMultiCaplet product2 =
                    new MarketModelPathwiseMultiCaplet(rateTimes, accruals,
                            paymentTimes, todaysForwards);

            MarketModelPathwiseMultiProduct product;

            if (whichProduct == 0)
                product = product2.copy();
            else
                product = product1.copy();

            MultiStepOptionlets productDummy = new MultiStepOptionlets(rateTimes,
                    accruals, paymentTimes, payoffs);

            EvolutionDescription evolution = product.evolution();

            MarketModelType[] marketModels = {
                    // CalibratedMM,
                    // ExponentialCorrelationFlatVolatility,
                    MarketModelType.ExponentialCorrelationAbcdVolatility};

            for (MarketModelType j : marketModels) {

                int[] testedFactors = {
                        2
                        //, 4, 8, todaysForwards.size()
                };

                for (int factors : testedFactors) {
                    MeasureType[] measures = {MeasureType.MoneyMarket};

                    for (MeasureType measure : measures) {
                        List<Integer> numeraires = makeMeasure(productDummy, measure);

                        for (int n = 0; n < 1; n++) {
                            MTBrownianGeneratorFactory generatorFactory =
                                    new MTBrownianGeneratorFactory(seed_);

                            boolean logNormal = true;
                            MarketModel marketModel =
                                    makeMarketModel(logNormal, evolution, factors, j);

                            LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(
                                    marketModel, generatorFactory, numeraires);
                            SequenceStatisticsInc stats = new SequenceStatisticsInc(
                                    product.numberOfProducts()
                                            * (todaysForwards.size() + 1));

                            double forwardBump = 1.0e-6;

                            String config = marketModelTypeToString(j) + ", " + factors
                                    + (factors > 1
                                    ? (factors == todaysForwards.size()
                                    ? " (full) factors, "
                                    : " factors, ")
                                    : " factor,")
                                    + measureTypeToString(measure) + ", "
                                    + "MT BGF";
                            if (printReport_)
                                System.out.println("    " + config);

                            int initialNumeraire = evolver.numeraires().get(0);
                            double initialNumeraireValue =
                                    todaysDiscounts.get(initialNumeraire);

                            {
                                PathwiseAccountingEngine accountingengine =
                                        new PathwiseAccountingEngine(
                                                new LogNormalFwdRateEuler(
                                                        evolver), // method relies heavily on LMM Euler
                                                product,
                                                marketModel, // we need pseudo-roots and displacements
                                                initialNumeraireValue);

                                accountingengine.multiplePathValues(stats, paths_);
                            }

                            List<Double> valuesAndDeltas = stats.mean();
                            List<Double> errors = stats.errorEstimate();

                            List<Double> prices = filledList(
                                    product.numberOfProducts(), 0.0);
                            List<Double> priceErrors = filledList(
                                    product.numberOfProducts(), 0.0);

                            Matrix deltas = new Matrix(product.numberOfProducts(),
                                    todaysForwards.size(), 0.0);
                            Matrix deltasErrors = new Matrix(
                                    product.numberOfProducts(),
                                    todaysForwards.size(), 0.0);
                            List<Double> modelPrices = filledList(
                                    product.numberOfProducts(), 0.0);

                            for (int i = 0; i < product.numberOfProducts(); ++i) {
                                prices.set(i, valuesAndDeltas.get(i));

                                priceErrors.set(i, errors.get(i));

                                modelPrices.set(i, new BlackCalculator(
                                        displacedPayoffs.get(i), todaysForwards.get(i),
                                        volatilities.get(i) * Math.sqrt(rateTimes.get(i)),
                                        todaysDiscounts.get(i + 1)
                                                * (rateTimes.get(i + 1) - rateTimes.get(i)))
                                        .value());

                                for (int jj = 0; jj < todaysForwards.size(); ++jj) {
                                    deltas.set(i, jj,
                                            valuesAndDeltas.get((i + 1)
                                                    * product.numberOfProducts() + jj));
                                    deltasErrors.set(i, jj,
                                            errors.get((i + 1)
                                                    * product.numberOfProducts() + jj));
                                }
                            }

                            Matrix modelDeltas = new Matrix(
                                    product.numberOfProducts(),
                                    todaysForwards.size(), 0.0);

                            List<Double> discPlus = filledList(
                                    todaysForwards.size() + 1,
                                    todaysDiscounts.get(0));
                            List<Double> discMinus = filledList(
                                    todaysForwards.size() + 1,
                                    todaysDiscounts.get(0));
                            List<Double> fwdPlus = filledList(
                                    todaysForwards.size(), 0.0);
                            List<Double> fwdMinus = filledList(
                                    todaysForwards.size(), 0.0);

                            for (int i = 0; i < todaysForwards.size(); ++i) {
                                for (int jj = 0; jj < todaysForwards.size(); ++jj) {
                                    if (i != jj) {
                                        fwdPlus.set(jj, todaysForwards.get(jj));
                                        fwdMinus.set(jj, todaysForwards.get(jj));

                                    } else {
                                        fwdPlus.set(jj,
                                                todaysForwards.get(jj) + forwardBump);
                                        fwdMinus.set(jj,
                                                todaysForwards.get(jj) - forwardBump);
                                    }

                                    double tau = rateTimes.get(jj + 1)
                                            - rateTimes.get(jj);
                                    discPlus.set(jj + 1, discPlus.get(jj)
                                            / (1.0 + fwdPlus.get(jj) * tau));
                                    discMinus.set(jj + 1, discMinus.get(jj)
                                            / (1.0 + fwdMinus.get(jj) * tau));
                                }

                                for (int k = 0; k < product.numberOfProducts(); ++k) {
                                    double tau = rateTimes.get(k + 1)
                                            - rateTimes.get(k);
                                    double priceUp = new BlackCalculator(
                                            displacedPayoffs.get(k), fwdPlus.get(k),
                                            volatilities.get(k)
                                                    * Math.sqrt(rateTimes.get(k)),
                                            discPlus.get(k + 1) * tau)
                                            .value();
                                    double priceDown = new BlackCalculator(
                                            displacedPayoffs.get(k), fwdMinus.get(k),
                                            volatilities.get(k)
                                                    * Math.sqrt(rateTimes.get(k)),
                                            discMinus.get(k + 1) * tau)
                                            .value();

                                    modelDeltas.set(k, i,
                                            (priceUp - priceDown) / (2 * forwardBump));
                                }
                            }

                            int numberErrors = 0;

                            for (int i = 0; i < product.numberOfProducts(); ++i) {

                                double thisPrice = prices.get(i);
                                double thisModelPrice = modelPrices.get(i);
                                double priceErrorInSds =
                                        ((thisPrice - thisModelPrice) / priceErrors.get(i));

                                double errorTheshold = 3.5;

                                if (Math.abs(priceErrorInSds) > errorTheshold) {
                                    System.out.println("Caplet "
                                            + i + " price " + prices.get(i)
                                            + " model price "
                                            + modelPrices.get(i)
                                            + "   Standard error: " + priceErrors.get(i)
                                            + " errors in sds: " + priceErrorInSds);

                                    ++numberErrors;
                                }

                                double threshold = 1e-10;

                                for (int jj = 0; jj < todaysForwards.size(); ++jj) {
                                    double delta = deltas.get(i, jj);
                                    double modelDelta = modelDeltas.get(i, jj);

                                    double deltaErrorInSds = 100;

                                    if (deltasErrors.get(i, jj) > 0.0)
                                        deltaErrorInSds =
                                                ((delta - modelDelta)
                                                        / deltasErrors.get(i, jj));
                                    else if (Math.abs(modelDelta - delta)
                                            < threshold) // to cope with zero over zero
                                        deltaErrorInSds = 0.0;

                                    if (Math.abs(deltaErrorInSds) > errorTheshold) {

                                        System.out.println("Caplet "
                                                + i + " delta " + jj + "has value "
                                                + deltas.get(i, jj) + " model value "
                                                + modelDeltas.get(i, jj)
                                                + "   Standard error: "
                                                + deltasErrors.get(i, jj)
                                                + " errors in sds: " + deltaErrorInSds);

                                        ++numberErrors;
                                    }
                                }
                            }

                            if (numberErrors > 0)
                                fail("Pathwise greeks test has " + numberErrors + "\n");
                        }
                    }
                }
            }
        }
    }

    @Test
    void testPathwiseVegas() {
        System.out.println(
                "Testing pathwise vegas in a lognormal forward rate market model...");

        setup();

        List<Payoff> payoffs = new ArrayList<>();
        List<StrikedTypePayoff> displacedPayoffs = new ArrayList<>();
        for (int i = 0; i < todaysForwards.size(); ++i) {
            payoffs.add(new
                    PlainVanillaPayoff(Option.Type.Call, todaysForwards.get(i)));
            //CashOrNothingPayoff(Option.Type.Call, todaysForwards.get(i), 0.01));
            displacedPayoffs.add(new
                    PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i) + displacement));
            //CashOrNothingPayoff(Option.Type.Call, todaysForwards.get(i)+displacement, 0.01));
        }

        MultiStepOptionlets product = new MultiStepOptionlets(rateTimes, accruals,
                paymentTimes, payoffs);

        MarketModelPathwiseMultiCaplet caplets = new MarketModelPathwiseMultiCaplet(
                rateTimes, accruals, paymentTimes, todaysForwards);

        MarketModelPathwiseMultiDeflatedCaplet capletsDeflated =
                new MarketModelPathwiseMultiDeflatedCaplet(rateTimes, accruals,
                        paymentTimes, todaysForwards);

        LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(todaysForwards);

        EvolutionDescription evolution = product.evolution();
        int steps = evolution.numberOfSteps();
        int numberRates = evolution.numberOfRates();

        double bumpSizeNumericalDifferentiation = 1E-6;
        double vegaBumpSize = 1e-2;
        int pathsToDo = 10; // for the numerical differentiation test we are
                            // requiring equality on each path so this is
                            // actually quite strict
        int pathsToDoSimulation = paths_;
        int bumpIncrement = 1 + evolution.numberOfSteps() / 3;
        double numericalBumpSizeForSwaptionPseudo = 1E-7;

        double multiplier = 50; // how many times the bump size squared, the numerical differentation is allowed to differ by
        // printReport_ = true;
        double maxError = 0.0;
        int numberSwaptionPseudoFailures = 0;
        int numberCapPseudoFailures = 0;
        int numberCapImpVolFailures = 0;
        int numberCapVolPseudoFailures = 0;
        double swaptionPseudoTolerance = 1e-8;
        double impVolTolerance = 1e-5;
        double capStrike = meanForward;
        double initialNumeraireValue = 0.95;

        // Official BOOST_ERROR records a failure but continues the test case.
        // Accumulate the official messages and fail at the end unless a later
        // BOOST_FAIL (fail()) aborts first.
        List<String> boostErrors = new ArrayList<>();

        MarketModelType[] marketModels = {
                // CalibratedMM,
                // ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility
        };

        /////////////////////////////////// test derivative of swaption implied
        // vol with respect to pseudo-root elements

        for (MarketModelType j : marketModels) {

            int[] testedFactors = {Math.min(3, todaysForwards.size())
                    //    todaysForwards.size()
                    //, 4, 8,
            };

            for (int factors : testedFactors) {
                boolean logNormal = true;

                MarketModel marketModel =
                        makeMarketModel(logNormal, evolution, factors, j);

                int startIndex = Math.min(1, evolution.numberOfRates() - 2);
                int endIndex = evolution.numberOfRates() - 1;

                SwaptionPseudoDerivative derivative = new SwaptionPseudoDerivative(
                        marketModel,
                        startIndex,
                        endIndex);

                List<Matrix> pseudoRoots = new ArrayList<>();
                for (int k = 0; k < marketModel.numberOfSteps(); ++k)
                    pseudoRoots.add(deepCopyMatrix(marketModel.pseudoRoot(k)));

                // test that the derivative of swaption implied vols to the
                // pseudo-root elements are correct, finite differencing versus
                // analytic value

                for (int step = 0; step < evolution.numberOfSteps(); ++step) {
                    for (int l = 0; l < evolution.numberOfRates(); ++l)
                        for (int f = 0; f < factors; ++f) {

                            // change one pseudo root element in the calibration
                            // by adding a bump to it

                            pseudoRoots.get(step).set(l, f,
                                    pseudoRoots.get(step).get(l, f)
                                            + numericalBumpSizeForSwaptionPseudo);

                            // create new market model with the pseudo root bumped

                            PseudoRootFacade bumpedUp = new PseudoRootFacade(
                                    pseudoRoots, rateTimes, marketModel.initialRates(),
                                    marketModel.displacements());

                            // compute the implied vol of the swaption with the
                            // bumped pseudo roots

                            double upImpVol =
                                    SwapForwardMappings.swaptionImpliedVolatility(
                                            bumpedUp, startIndex, endIndex);

                            // undo the bump

                            pseudoRoots.get(step).set(l, f,
                                    pseudoRoots.get(step).get(l, f)
                                            - numericalBumpSizeForSwaptionPseudo);

                            // bump down

                            pseudoRoots.get(step).set(l, f,
                                    pseudoRoots.get(step).get(l, f)
                                            - numericalBumpSizeForSwaptionPseudo);

                            // create facade for the bumped down pseudo roots

                            PseudoRootFacade bumpedDown = new PseudoRootFacade(
                                    pseudoRoots, rateTimes, marketModel.initialRates(),
                                    marketModel.displacements());

                            // compute the implied vol of the swaption with the
                            // bumped down pseudo roots

                            double downImpVol =
                                    SwapForwardMappings.swaptionImpliedVolatility(
                                            bumpedDown, startIndex, endIndex);

                            // undo bumping

                            pseudoRoots.get(step).set(l, f,
                                    pseudoRoots.get(step).get(l, f)
                                            + numericalBumpSizeForSwaptionPseudo);

                            // use symmetric finite differencing to compute the
                            // change in the swaptions implied vol for changes
                            // in this pseudo-root element

                            double volDeriv = (upImpVol - downImpVol)
                                    / (2.0 * numericalBumpSizeForSwaptionPseudo);

                            double modelVal =
                                    derivative.volatilityDerivative(step).get(l, f);

                            double error = volDeriv - modelVal;

                            if (Math.abs(error) > swaptionPseudoTolerance)
                                ++numberSwaptionPseudoFailures;

                        }

                }

                if (numberSwaptionPseudoFailures > 0) {
                    String message = "swaption pseudo test failed "
                            + numberSwaptionPseudoFailures + " times";
                    System.out.println(message);
                    boostErrors.add(message);
                }
            }
        }

        /////////////////////////////////////

        for (MarketModelType j : marketModels) {

            int[] testedFactors = {Math.min(3, todaysForwards.size())
                    //    todaysForwards.size()
                    //, 4, 8,
            };

            for (int factors : testedFactors) {
                boolean logNormal = true;

                MarketModel marketModel =
                        makeMarketModel(logNormal, evolution, factors, j);

                for (int startIndex = 1; startIndex < evolution.numberOfRates() - 1;
                     ++startIndex)
                    for (int endIndex = startIndex + 1;
                         endIndex < evolution.numberOfRates(); ++endIndex) {

                        CapPseudoDerivative derivative = new CapPseudoDerivative(
                                marketModel,
                                capStrike,
                                startIndex,
                                endIndex, initialNumeraireValue);

                        List<Matrix> pseudoRoots = new ArrayList<>();
                        for (int k = 0; k < marketModel.numberOfSteps(); ++k)
                            pseudoRoots.add(deepCopyMatrix(marketModel.pseudoRoot(k)));

                        // test cap price derivatives with respect to
                        // pseudo-root elements

                        for (int step = 0; step < evolution.numberOfSteps(); ++step) {
                            for (int l = 0; l < evolution.numberOfRates(); ++l)
                                for (int f = 0; f < factors; ++f) {

                                    // similar to swaption pseudo derivative
                                    // test but with prices not implied vols

                                    pseudoRoots.get(step).set(l, f,
                                            pseudoRoots.get(step).get(l, f)
                                                    + numericalBumpSizeForSwaptionPseudo);

                                    PseudoRootFacade bumpedUp = new PseudoRootFacade(
                                            pseudoRoots, rateTimes,
                                            marketModel.initialRates(),
                                            marketModel.displacements());

                                    // get total covariances of rates with
                                    // bumped up pseudo-roots, we really only
                                    // need the variances
                                    Matrix totalCovUp = bumpedUp.totalCovariance(
                                            marketModel.numberOfSteps() - 1);

                                    pseudoRoots.get(step).set(l, f,
                                            pseudoRoots.get(step).get(l, f)
                                                    - numericalBumpSizeForSwaptionPseudo);

                                    pseudoRoots.get(step).set(l, f,
                                            pseudoRoots.get(step).get(l, f)
                                                    - numericalBumpSizeForSwaptionPseudo);

                                    PseudoRootFacade bumpedDown = new PseudoRootFacade(
                                            pseudoRoots, rateTimes,
                                            marketModel.initialRates(),
                                            marketModel.displacements());

                                    // get total covariances of rates with
                                    // bumped down pseudo-roots, we really only
                                    // need the variances
                                    Matrix totalCovDown = bumpedDown.totalCovariance(
                                            marketModel.numberOfSteps() - 1);

                                    pseudoRoots.get(step).set(l, f,
                                            pseudoRoots.get(step).get(l, f)
                                                    + numericalBumpSizeForSwaptionPseudo);

                                    // we have to loop through all the caplets
                                    // underlying the cap to get the price

                                    double priceDeriv = 0.0;
                                    for (int k = startIndex; k < endIndex; ++k) {
                                        double upSd = Math.sqrt(totalCovUp.get(k, k));
                                        double downSd = Math.sqrt(totalCovDown.get(k, k));

                                        double annuity = todaysDiscounts.get(k + 1)
                                                * marketModel.evolution().rateTaus().get(k);
                                        double forward = todaysForwards.get(k);

                                        double upPrice = BlackFormula.blackFormula(
                                                Option.Type.Call,
                                                capStrike,
                                                forward,
                                                upSd,
                                                annuity,
                                                marketModel.displacements().get(k));

                                        double downPrice = BlackFormula.blackFormula(
                                                Option.Type.Call,
                                                capStrike,
                                                forward,
                                                downSd,
                                                annuity,
                                                marketModel.displacements().get(k));

                                        priceDeriv += (upPrice - downPrice)
                                                / (2.0 * numericalBumpSizeForSwaptionPseudo);

                                    }

                                    double modelVal =
                                            derivative.priceDerivative(step).get(l, f);

                                    double error = priceDeriv - modelVal;

                                    if (Math.abs(error) > swaptionPseudoTolerance)
                                        ++numberCapPseudoFailures;

                                }

                        }

                        // test the implied vol of the cap, each underlying
                        // caplet has a different implied vol and the cap's is
                        // different again

                        double impVol = derivative.impliedVolatility();

                        Matrix totalCov = marketModel.totalCovariance(
                                evolution.numberOfSteps() - 1);
                        double priceConstVol = 0.0;
                        double priceVarVol = 0.0;

                        for (int m = startIndex; m < endIndex; ++m) {
                            double annuity = todaysDiscounts.get(m + 1)
                                    * marketModel.evolution().rateTaus().get(m);
                            double expiry = rateTimes.get(m);
                            double forward = todaysForwards.get(m);

                            priceConstVol += BlackFormula.blackFormula(
                                    Option.Type.Call,
                                    capStrike,
                                    forward,
                                    impVol * Math.sqrt(expiry),
                                    annuity,
                                    marketModel.displacements().get(m));

                            priceVarVol += BlackFormula.blackFormula(
                                    Option.Type.Call,
                                    capStrike,
                                    forward,
                                    Math.sqrt(totalCov.get(m, m)),
                                    annuity,
                                    marketModel.displacements().get(m));

                        }

                        if (Math.abs(priceVarVol - priceConstVol) > impVolTolerance)
                            ++numberCapImpVolFailures;

                    }

                if (numberCapPseudoFailures > 0) {
                    String message = "cap pseudo test failed for prices "
                            + numberCapPseudoFailures + " times";
                    System.out.println(message);
                    boostErrors.add(message);
                }

                if (numberCapImpVolFailures > 0) {
                    String message = "cap pseudo test failed for implied vols "
                            + numberCapImpVolFailures + " times";
                    System.out.println(message);
                    boostErrors.add(message);
                }
            }

            // we have tested the price derivative and the implied vol
            // function, now the derivative of the cap implied vols with
            // respect to pseudo-root elements

            // since we have already tested the imp vol function we use it here

            for (int factors : testedFactors) {
                boolean logNormal = true;

                MarketModel marketModel =
                        makeMarketModel(logNormal, evolution, factors, j);

                for (int startIndex = 1; startIndex < evolution.numberOfRates() - 1;
                     ++startIndex)
                    for (int endIndex = startIndex + 1;
                         endIndex < evolution.numberOfRates(); ++endIndex) {

                        CapPseudoDerivative derivative = new CapPseudoDerivative(
                                marketModel,
                                capStrike,
                                startIndex,
                                endIndex, initialNumeraireValue);

                        List<Matrix> pseudoRoots = new ArrayList<>();
                        for (int k = 0; k < marketModel.numberOfSteps(); ++k)
                            pseudoRoots.add(deepCopyMatrix(marketModel.pseudoRoot(k)));

                        for (int step = 0; step < evolution.numberOfSteps(); ++step) {
                            for (int l = 0; l < evolution.numberOfRates(); ++l)
                                for (int f = 0; f < factors; ++f) {
                                    pseudoRoots.get(step).set(l, f,
                                            pseudoRoots.get(step).get(l, f)
                                                    + numericalBumpSizeForSwaptionPseudo);

                                    PseudoRootFacade bumpedUp = new PseudoRootFacade(
                                            pseudoRoots, rateTimes,
                                            marketModel.initialRates(),
                                            marketModel.displacements());

                                    // Official wraps the facade in another
                                    // PseudoRootFacade clone; passing the
                                    // freshly constructed facade is equivalent.
                                    CapPseudoDerivative upDerivative =
                                            new CapPseudoDerivative(bumpedUp,
                                                    capStrike,
                                                    startIndex,
                                                    endIndex, initialNumeraireValue);

                                    double volUp = upDerivative.impliedVolatility();

                                    pseudoRoots.get(step).set(l, f,
                                            pseudoRoots.get(step).get(l, f)
                                                    - numericalBumpSizeForSwaptionPseudo);

                                    pseudoRoots.get(step).set(l, f,
                                            pseudoRoots.get(step).get(l, f)
                                                    - numericalBumpSizeForSwaptionPseudo);

                                    PseudoRootFacade bumpedDown = new PseudoRootFacade(
                                            pseudoRoots, rateTimes,
                                            marketModel.initialRates(),
                                            marketModel.displacements());

                                    CapPseudoDerivative downDerivative =
                                            new CapPseudoDerivative(bumpedDown,
                                                    capStrike,
                                                    startIndex,
                                                    endIndex, initialNumeraireValue);

                                    double volDown = downDerivative.impliedVolatility();

                                    pseudoRoots.get(step).set(l, f,
                                            pseudoRoots.get(step).get(l, f)
                                                    + numericalBumpSizeForSwaptionPseudo);

                                    double volDeriv = (volUp - volDown)
                                            / (2.0 * numericalBumpSizeForSwaptionPseudo);

                                    double modelVal =
                                            derivative.volatilityDerivative(step).get(l, f);

                                    double error = volDeriv - modelVal;

                                    if (Math.abs(error) > impVolTolerance * 10)
                                        ++numberCapVolPseudoFailures;

                                }

                        }

                    }

                if (numberCapVolPseudoFailures > 0) {
                    String message = "cap pseudo test failed for implied vols "
                            + numberCapVolPseudoFailures + " times";
                    System.out.println(message);
                    boostErrors.add(message);
                }
            }
        }

        /////////////////////////////////////

        for (int j = 0; j < marketModels.length; j++) {

            int[] testedFactors = {
                    Math.min(1, todaysForwards.size())
                    //    todaysForwards.size()
                    //, 4, 8,

            };

            for (int factors : testedFactors) {
                int factorsToTest =
                        Math.min(2, factors); // doing all possible vegas is combinatorially explosive

                MeasureType[] measures = {
                        MeasureType.MoneyMarket
                };

                List<Matrix> pseudoBumps = new ArrayList<>();
                List<Matrix> pseudoBumpsDown = new ArrayList<>();

                for (int k = 0; k < evolution.numberOfRates(); ++k) {
                    for (int f = 0; f < factors; ++f) {
                        Matrix modelBump = new Matrix(evolution.numberOfRates(),
                                factors, 0.0);
                        modelBump.set(k, f, bumpSizeNumericalDifferentiation);
                        pseudoBumps.add(deepCopyMatrix(modelBump));
                        modelBump.set(k, f, -bumpSizeNumericalDifferentiation);
                        pseudoBumpsDown.add(deepCopyMatrix(modelBump));
                    }
                }

                List<List<Matrix>> vegaBumps = new ArrayList<>();

                Matrix modelBump = new Matrix(evolution.numberOfRates(), factors, 0.0);

                for (int l = 0; l < evolution.numberOfSteps(); ++l) {
                    List<Matrix> stepBumps = new ArrayList<>();
                    vegaBumps.add(stepBumps);
                    for (int k = 0; k < evolution.numberOfRates(); k = k + bumpIncrement) {
                        for (int f = 0; f < factorsToTest; ++f) {

                            for (int m = 0; m < evolution.numberOfSteps(); ++m) {
                                if (l == m && k >= l)
                                    modelBump.set(k, f, vegaBumpSize);

                                stepBumps.add(deepCopyMatrix(modelBump));

                                modelBump.set(k, f, 0.0);
                            }
                        }
                    }

                }

                for (MeasureType measure : measures) {

                    List<Integer> numeraires = makeMeasure(product, measure);

                    List<RatePseudoRootJacobian> testees = new ArrayList<>();
                    List<RatePseudoRootJacobianAllElements> testees2 = new ArrayList<>();

                    List<RatePseudoRootJacobianNumerical> testers = new ArrayList<>();
                    List<RatePseudoRootJacobianNumerical> testersDown = new ArrayList<>();

                    MTBrownianGeneratorFactory generatorFactory =
                            new MTBrownianGeneratorFactory(seed_);

                    boolean logNormal = true;
                    MarketModel marketModel =
                            makeMarketModel(logNormal, evolution, factors, marketModels[j]);

                    for (int l = 0; l < evolution.numberOfSteps(); ++l) {
                        final Matrix pseudoRoot = marketModel.pseudoRoot(l);
                        testees.add(new RatePseudoRootJacobian(pseudoRoot,
                                evolution.firstAliveRate().get(l), numeraires.get(l),
                                evolution.rateTaus(), pseudoBumps,
                                marketModel.displacements()));

                        testees2.add(new RatePseudoRootJacobianAllElements(pseudoRoot,
                                evolution.firstAliveRate().get(l), numeraires.get(l),
                                evolution.rateTaus(),
                                marketModel.displacements()));

                        testers.add(new RatePseudoRootJacobianNumerical(pseudoRoot,
                                evolution.firstAliveRate().get(l), numeraires.get(l),
                                evolution.rateTaus(), pseudoBumps,
                                marketModel.displacements()));
                        testersDown.add(new RatePseudoRootJacobianNumerical(pseudoRoot,
                                evolution.firstAliveRate().get(l),
                                numeraires.get(l), evolution.rateTaus(), pseudoBumpsDown,
                                marketModel.displacements()));
                    }

                    BrownianGenerator generator = generatorFactory.create(factors,
                            steps);
                    LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(marketModel,
                            generatorFactory,
                            numeraires);

                    List<Double> oldRates = filledList(evolution.numberOfRates(), 0.0);
                    List<Double> newRates = filledList(evolution.numberOfRates(), 0.0);
                    List<Double> gaussians = filledList(factors, 0.0);

                    List<Integer> numberCashFlowsThisStep = new ArrayList<>();
                    for (int i = 0; i < product.numberOfProducts(); ++i)
                        numberCashFlowsThisStep.add(0);

                    List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated =
                            new ArrayList<>();
                    for (int i = 0; i < product.numberOfProducts(); ++i) {
                        List<MarketModelMultiProduct.CashFlow> flows = new ArrayList<>();
                        for (int k = 0;
                             k < product.maxNumberOfCashFlowsPerProductPerStep(); ++k)
                            flows.add(new MarketModelMultiProduct.CashFlow());
                        cashFlowsGenerated.add(flows);
                    }

                    Matrix B = new Matrix(pseudoBumps.size(),
                            evolution.numberOfRates(), 0.0);
                    Matrix B2 = new Matrix(pseudoBumps.size(),
                            evolution.numberOfRates(), 0.0);
                    Matrix B3 = new Matrix(pseudoBumps.size(),
                            evolution.numberOfRates(), 0.0);
                    Matrix B4 = new Matrix(pseudoBumps.size(),
                            evolution.numberOfRates(), 0.0);

                    List<Matrix> globalB = new ArrayList<>();
                    {
                        Matrix modelB = new Matrix(evolution.numberOfRates(), factors,
                                0.0);
                        for (int i = 0; i < steps; ++i)
                            globalB.add(deepCopyMatrix(modelB));
                    }

                    List<Double> oneStepDFs = filledList(
                            evolution.numberOfRates() + 1, 0.0);
                    oneStepDFs.set(0, 1.0);

                    int numberFailures = 0;
                    int numberFailures2 = 0;

                    for (int l = 0; l < pathsToDo; ++l) {
                        evolver.startNewPath();
                        product.reset();
                        generator.nextPath();

                        boolean done;
                        newRates = new ArrayList<>(marketModel.initialRates());
                        int currentStep = 0;

                        do {
                            oldRates = new ArrayList<>(newRates);

                            evolver.advanceStep();
                            done = product.nextTimeStep(evolver.currentState(),
                                    numberCashFlowsThisStep,
                                    cashFlowsGenerated);

                            newRates = new ArrayList<>(
                                    evolver.currentState().forwardRates());

                            for (int i = 1; i <= evolution.numberOfRates(); ++i)
                                oneStepDFs.set(i,
                                        1.0 / (1 + oldRates.get(i - 1)
                                                * evolution.rateTaus().get(i - 1)));

                            generator.nextStep(gaussians);

                            testees.get(currentStep).getBumps(oldRates, oneStepDFs,
                                    newRates, gaussians, B);
                            testees2.get(currentStep).getBumps(oldRates, oneStepDFs,
                                    newRates, gaussians, globalB);

                            testers.get(currentStep).getBumps(oldRates, oneStepDFs,
                                    newRates, gaussians, B2);
                            testersDown.get(currentStep).getBumps(oldRates, oneStepDFs,
                                    newRates, gaussians, B3);

                            // now do make out put of allElements class into same form

                            for (int i1 = 0; i1 < pseudoBumps.size(); ++i1) {
                                int j1 = 0;

                                for (; j1 < evolution.firstAliveRate().get(i1); ++j1) {
                                    B4.set(i1, j1, 0.0);
                                }
                                for (; j1 < numberRates; ++j1) {
                                    double sum = 0.0;

                                    for (int k1 = evolution.firstAliveRate().get(i1);
                                         k1 < numberRates; ++k1)
                                        for (int f1 = 0; f1 < factors; ++f1)
                                            sum += pseudoBumps.get(i1).get(k1, f1)
                                                    * globalB.get(j1).get(k1, f1);

                                    B4.set(i1, j1, sum);

                                }
                            }

                            for (int jj = 0; jj < B.rows(); ++jj)
                                for (int kk = 0; kk < B.cols(); ++kk) {
                                    double analytic = B.get(jj, kk)
                                            / bumpSizeNumericalDifferentiation;
                                    double analytic2 = B4.get(jj, kk)
                                            / bumpSizeNumericalDifferentiation;
                                    double numerical = (B2.get(jj, kk) - B3.get(jj, kk))
                                            / (2 * bumpSizeNumericalDifferentiation);
                                    double errorSize = (analytic - numerical)
                                            / (bumpSizeNumericalDifferentiation
                                            * bumpSizeNumericalDifferentiation);
                                    double errorSize2 = (analytic2 - numerical)
                                            / (bumpSizeNumericalDifferentiation
                                            * bumpSizeNumericalDifferentiation);

                                    maxError = Math.max(maxError,
                                            Math.abs(errorSize));

                                    if (Math.abs(errorSize) > multiplier) {
                                        ++numberFailures;
                                        if (printReport_)
                                            System.out.println("path " + l + " step "
                                                    + currentStep + " j " + jj
                                                    + " k " + kk + " B "
                                                    + B.get(jj, kk) + "  B2 "
                                                    + B2.get(jj, kk));

                                    }

                                    if (Math.abs(errorSize2) > multiplier) {
                                        ++numberFailures2;
                                        if (printReport_)
                                            System.out.println("path " + l + " step "
                                                    + currentStep + " j " + jj
                                                    + " k " + kk + " B4 "
                                                    + B4.get(jj, kk) + "  B2 "
                                                    + B2.get(jj, kk));

                                    }

                                }
                            ++currentStep;
                        } while (!done);

                    }

                    if (numberFailures > 0)
                        fail("Pathwise rate pseudoroot jacobian test fails : "
                                + numberFailures + "\n");

                    if (numberFailures2 > 0)
                        fail("Pathwise rate pseudoroot jacobian all elements test fails : "
                                + numberFailures2 + "\n");
                } // end of k loop over measures

                // the quick test done now do a simulation test for the vegas for caplets

                int numberDeflatedErrors = 0;
                int numberUndeflatedErrors = 0;
                double biggestError = 0.0;

                for (int deflate = 0; deflate < 2; ++deflate) {
                    MarketModelPathwiseMultiProduct productToUse;

                    if (deflate == 0)
                        productToUse = caplets.copy();
                    else
                        productToUse = capletsDeflated.copy();

                    for (MeasureType measure : measures) {

                        List<Integer> numeraires = makeMeasure(product, measure);

                        MTBrownianGeneratorFactory generatorFactory =
                                new MTBrownianGeneratorFactory(seed_);

                        boolean logNormal = true;
                        MarketModel marketModel =
                                makeMarketModel(logNormal, evolution, factors,
                                        marketModels[j]);

                        LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(
                                marketModel,
                                generatorFactory,
                                numeraires);

                        //      SequenceStatistics stats(product.numberOfProducts()*(todaysForwards.size()+1+vegaBumps[0].size()));

                        String config = marketModelTypeToString(marketModels[j])
                                + ", " + factors
                                + (factors > 1
                                ? (factors == todaysForwards.size()
                                ? " (full) factors, "
                                : " factors, ")
                                : " factor,")
                                + measureTypeToString(measure) + ", "
                                + "MT BGF";
                        if (printReport_)
                            System.out.println("    " + config);

                        List<Double> values = new ArrayList<>();

                        List<Double> errors = new ArrayList<>();

                        {

                            PathwiseVegasAccountingEngine accountingengine =
                                    new PathwiseVegasAccountingEngine(
                                            new LogNormalFwdRateEuler(
                                                    evolver), // method relies heavily on LMM Euler
                                            productToUse,
                                            marketModel, // we need pseudo-roots and displacements
                                            vegaBumps,
                                            initialNumeraireValue);

                            accountingengine.multiplePathValues(values, errors,
                                    pathsToDoSimulation);
                        }

                        // we have computed the vegas now we have to test them
                        // against the analytic values

                        // extract into easier format

                        Matrix vegasMatrix = new Matrix(caplets.numberOfProducts(),
                                vegaBumps.get(0).size(), 0.0);
                        Matrix standardErrors = new Matrix(
                                caplets.numberOfProducts(),
                                vegaBumps.get(0).size(), 0.0);
                        Matrix deltasMatrix = new Matrix(caplets.numberOfProducts(),
                                numberRates, 0.0);
                        Matrix deltasErrors = new Matrix(
                                caplets.numberOfProducts(), numberRates, 0.0);
                        List<Double> prices = filledList(caplets.numberOfProducts(),
                                0.0);
                        List<Double> priceErrors = filledList(
                                caplets.numberOfProducts(), 0.0);

                        int entriesPerProduct = 1 + numberRates
                                + vegaBumps.get(0).size();

                        for (int i = 0; i < caplets.numberOfProducts(); ++i) {
                            prices.set(i, values.get(i * entriesPerProduct));
                            priceErrors.set(i, errors.get(i * entriesPerProduct));

                            for (int jj = 0; jj < vegaBumps.get(0).size(); ++jj) {
                                vegasMatrix.set(i, jj,
                                        values.get(i * entriesPerProduct
                                                + numberRates + 1 + jj));
                                standardErrors.set(i, jj,
                                        errors.get(i * entriesPerProduct
                                                + numberRates + 1 + jj));
                            }
                            for (int jj = 0; jj < numberRates; ++jj) {
                                deltasMatrix.set(i, jj,
                                        values.get(i * entriesPerProduct + 1 + jj));
                                deltasErrors.set(i, jj,
                                        errors.get(i * entriesPerProduct + 1 + jj));
                            }
                        }

                        // first get the terminal vols

                        Matrix totalCovariance = marketModel.totalCovariance(
                                marketModel.numberOfSteps() - 1);

                        List<Double> truePrices = filledList(
                                caplets.numberOfProducts(), 0.0);

                        for (int r = 0; r < truePrices.size(); ++r) {
                            truePrices.set(r, new BlackCalculator(
                                    displacedPayoffs.get(r), todaysForwards.get(r),
                                    Math.sqrt(totalCovariance.get(r, r)),
                                    todaysDiscounts.get(r + 1)
                                            * (rateTimes.get(r + 1) - rateTimes.get(r)))
                                    .value());
                        }

                        for (int b = 0; b < vegaBumps.get(0).size(); ++b) {

                            List<Double> bumpedPrices = filledList(
                                    truePrices.size(), 0.0);
                            List<Double> variances = filledList(
                                    truePrices.size(), 0.0);
                            List<Double> vegas = filledList(truePrices.size(), 0.0);

                            for (int step = 0; step < marketModel.numberOfSteps();
                                 ++step) {
                                Matrix pseudoRoot = deepCopyMatrix(
                                        marketModel.pseudoRoot(step));
                                for (int rate = 0; rate < numberRates; ++rate)
                                    for (int f = 0;
                                         f < marketModel.numberOfFactors(); ++f)
                                        pseudoRoot.addEq(rate, f,
                                                vegaBumps.get(step).get(b).get(rate, f));

                                for (int rate = step; rate < marketModel.numberOfRates();
                                     ++rate) {
                                    double variance = 0.0;
                                    for (int f = 0;
                                         f < marketModel.numberOfFactors(); ++f)
                                        variance += pseudoRoot.get(rate, f)
                                                * pseudoRoot.get(rate, f);

                                    variances.set(rate,
                                            variances.get(rate) + variance);
                                }
                            }

                            for (int r = 0; r < truePrices.size(); ++r) {

                                bumpedPrices.set(r, new BlackCalculator(
                                        displacedPayoffs.get(r),
                                        todaysForwards.get(r),
                                        Math.sqrt(variances.get(r)),
                                        todaysDiscounts.get(r + 1)
                                                * (rateTimes.get(r + 1)
                                                - rateTimes.get(r)))
                                        .value());

                                vegas.set(r,
                                        bumpedPrices.get(r) - truePrices.get(r));

                            }

                            for (int s = 0; s < truePrices.size(); ++s) {
                                double mcVega = vegasMatrix.get(s, b);
                                double analyticVega = vegas.get(s);
                                double thisError = mcVega - analyticVega;
                                double thisSE = standardErrors.get(s, b);

                                if (Math.abs(thisError) > 0.0) {
                                    double errorInSEs = thisError / thisSE;
                                    biggestError = Math.max(Math.abs(errorInSEs),
                                            biggestError);

                                    if (Math.abs(errorInSEs) > 4.5) {
                                        if (deflate == 0)
                                            ++numberUndeflatedErrors;
                                        else
                                            ++numberDeflatedErrors;
                                    }
                                }

                            }

                        }

                        // for deltas and prices the pathwise vega engine
                        // should agree precisely with the pathwiseaccounting
                        // engine so lets see if it does

                        MarketModelPathwiseMultiProduct productToUse2;

                        if (deflate == 0)
                            productToUse2 = caplets.copy();
                        else
                            productToUse2 = capletsDeflated.copy();

                        SequenceStatisticsInc stats = new SequenceStatisticsInc(
                                productToUse2.numberOfProducts()
                                        * (todaysForwards.size() + 1));
                        {
                            PathwiseAccountingEngine accountingengine =
                                    new PathwiseAccountingEngine(
                                            new LogNormalFwdRateEuler(
                                                    evolver), // method relies heavily on LMM Euler
                                            productToUse2,
                                            marketModel, // we need pseudo-roots and displacements
                                            initialNumeraireValue);

                            accountingengine.multiplePathValues(stats,
                                    pathsToDoSimulation);
                        }

                        List<Double> valuesAndDeltas2 = stats.mean();
                        List<Double> errors2 = stats.errorEstimate();

                        List<Double> prices2 = filledList(
                                productToUse2.numberOfProducts(), 0.0);
                        List<Double> priceErrors2 = filledList(
                                productToUse2.numberOfProducts(), 0.0);

                        Matrix deltas2 = new Matrix(
                                productToUse2.numberOfProducts(),
                                todaysForwards.size(), 0.0);
                        Matrix deltasErrors2 = new Matrix(
                                productToUse2.numberOfProducts(),
                                todaysForwards.size(), 0.0);
                        List<Double> modelPrices2 = filledList(
                                productToUse2.numberOfProducts(), 0.0);

                        for (int i = 0; i < productToUse2.numberOfProducts(); ++i) {
                            prices2.set(i, valuesAndDeltas2.get(i));
                            priceErrors2.set(i, errors2.get(i));

                            for (int jj = 0; jj < todaysForwards.size(); ++jj) {
                                deltas2.set(i, jj, valuesAndDeltas2.get(
                                        (i + 1) * productToUse2.numberOfProducts()
                                                + jj));
                                deltasErrors2.set(i, jj, errors2.get(
                                        (i + 1) * productToUse2.numberOfProducts()
                                                + jj));
                            }
                        }

                        for (int i = 0; i < productToUse2.numberOfProducts(); ++i) {

                            double priceDiff = prices2.get(i) - prices.get(i);

                            if (Math.abs(priceDiff) > 5 * priceErrors2.get(i)) // two sets of standard error
                                fail("pathwise accounting engine and pathwise vegas accounting engine not in perfect agreement for price.\n product "
                                        + i + ",  vega computed price: "
                                        + prices.get(j) + " previous price "
                                        + prices2.get(j) + ", deflate " + deflate
                                        + "\n");

                            for (int jj = 0; jj < todaysForwards.size(); ++jj) {
                                double error = deltas2.get(i, jj)
                                        - deltasMatrix.get(i, jj);
                                if (Math.abs(error) > 5 * deltasErrors2.get(i, jj)) // two sets of standard error
                                    fail("pathwise accounting engine and pathwise vegas accounting engine not in perfect agreement for dealts.\n product "
                                            + i + ", rate " + jj
                                            + " vega computed delta: "
                                            + deltasMatrix.get(i, jj)
                                            + " previous delta "
                                            + deltas2.get(i, jj) + "\n");
                            }
                        }
                    } // end of k loop over measures
                } // end of loop over deflation

                if (numberDeflatedErrors + numberUndeflatedErrors > 0)
                    fail("Model pathwise vega test for caplets fails : "
                            + numberDeflatedErrors + " deflated errors and "
                            + numberUndeflatedErrors
                            + " undeflated errors , biggest error in SEs is "
                            + biggestError + "\n");

                {
                    //  now do a simulation test for the vegas for caps

                    List<VolatilityBumpInstrumentJacobian.Cap> caps =
                            new ArrayList<>();

                    double capStrikeCaps = todaysForwards.get(0);

                    for (int i = 0; i + 2 < numberRates; i = i + 3) {
                        VolatilityBumpInstrumentJacobian.Cap nextCap =
                                new VolatilityBumpInstrumentJacobian.Cap();
                        //            nextCap.startIndex_ = i;
                        //            nextCap.endIndex_ = i+3;
                        //             nextCap.strike_ = capStrike;
                        //             caps.push_back(nextCap);

                        //        nextCap.startIndex_ = i+1;
                        //      nextCap.endIndex_ = i+3;
                        //    nextCap.strike_ = capStrike;
                        //  caps.push_back(nextCap);

                        nextCap.startIndex_ = i + 2;
                        nextCap.endIndex_ = i + 3;
                        nextCap.strike_ = capStrikeCaps;
                        caps.add(nextCap);

                    }

                    List<int[]> startsAndEnds = new ArrayList<>(caps.size());

                    for (int r = 0; r < caps.size(); ++r) {
                        startsAndEnds.add(new int[]{
                                caps.get(r).startIndex_, caps.get(r).endIndex_});
                    }

                    MarketModelPathwiseMultiDeflatedCap capsDeflated =
                            new MarketModelPathwiseMultiDeflatedCap(
                                    rateTimes,
                                    accruals,
                                    paymentTimes,
                                    capStrikeCaps,
                                    startsAndEnds);

                    for (MeasureType measure : measures) {

                        List<Integer> numeraires = makeMeasure(product, measure);

                        MTBrownianGeneratorFactory generatorFactory =
                                new MTBrownianGeneratorFactory(seed_);
                        MTBrownianGeneratorFactory generatorFactory2 =
                                new MTBrownianGeneratorFactory(seed_);

                        boolean logNormal = true;
                        MarketModel marketModel =
                                makeMarketModel(logNormal, evolution, factors,
                                        marketModels[j]);

                        LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(
                                marketModel,
                                generatorFactory,
                                numeraires);

                        LogNormalFwdRateEuler evolver2 = new LogNormalFwdRateEuler(
                                marketModel,
                                generatorFactory2,
                                numeraires);

                        //      SequenceStatistics stats(product.numberOfProducts()*(todaysForwards.size()+1+vegaBumps[0].size()));

                        String config = marketModelTypeToString(marketModels[j])
                                + ", " + factors
                                + (factors > 1
                                ? (factors == todaysForwards.size()
                                ? " (full) factors, "
                                : " factors, ")
                                : " factor,")
                                + measureTypeToString(measure) + ", "
                                + "MT BGF";
                        if (printReport_)
                            System.out.println("    " + config);

                        List<Double> values = new ArrayList<>();
                        List<Double> errors = new ArrayList<>();

                        List<Double> values2 = new ArrayList<>();
                        List<Double> errors2 = new ArrayList<>();

                        {

                            PathwiseVegasOuterAccountingEngine accountingengine =
                                    new PathwiseVegasOuterAccountingEngine(
                                            new LogNormalFwdRateEuler(
                                                    evolver2), // method relies heavily on LMM Euler
                                            capsDeflated,
                                            marketModel, // we need pseudo-roots and displacements
                                            vegaBumps,
                                            initialNumeraireValue);

                            accountingengine.multiplePathValues(values2, errors2,
                                    pathsToDoSimulation);
                        }

                        {

                            PathwiseVegasAccountingEngine accountingengine =
                                    new PathwiseVegasAccountingEngine(
                                            new LogNormalFwdRateEuler(
                                                    evolver), // method relies heavily on LMM Euler
                                            capsDeflated,
                                            marketModel, // we need pseudo-roots and displacements
                                            vegaBumps,
                                            initialNumeraireValue);

                            accountingengine.multiplePathValues(values, errors,
                                    pathsToDoSimulation);
                        }

                        // first test to see that the two implementation give the same results

                        {
                            double tol = 1E-8;

                            int numberMeanFailures = 0;

                            for (int i = 0; i < values.size(); ++i)
                                if (Math.abs(values.get(i) - values2.get(i)) > tol)
                                    ++numberMeanFailures;

                            if (numberMeanFailures > 0)
                                fail("Comparison of Pathwise vegas accounting engine and PathwiseVegasOuterAccountingEngine yields discrepancies:"
                                        + numberMeanFailures
                                        + "  out of "
                                        + values.size());

                        }


                        // we have computed the vegas now we have to test them
                        // against the analytic values

                        // extract into easier format

                        Matrix vegasMatrix = new Matrix(
                                capsDeflated.numberOfProducts(),
                                vegaBumps.get(0).size(), 0.0);
                        Matrix standardErrors = new Matrix(
                                capsDeflated.numberOfProducts(),
                                vegaBumps.get(0).size(), 0.0);
                        int entriesPerProduct = 1 + numberRates
                                + vegaBumps.get(0).size();

                        for (int i = 0; i < capsDeflated.numberOfProducts(); ++i)
                            for (int jj = 0; jj < vegaBumps.get(0).size(); ++jj) {
                                vegasMatrix.set(i, jj,
                                        values.get(i * entriesPerProduct
                                                + numberRates + 1 + jj));
                                standardErrors.set(i, jj,
                                        errors.get(i * entriesPerProduct
                                                + numberRates + 1 + jj));
                            }

                        // first get the terminal vols

                        Matrix totalCovariance = marketModel.totalCovariance(
                                marketModel.numberOfSteps() - 1);

                        List<Double> trueCapletPrices = filledList(numberRates, 0.0);
                        StrikedTypePayoff dispayoff = new
                                PlainVanillaPayoff(Option.Type.Call,
                                capStrikeCaps + displacement);

                        for (int r = 0; r < trueCapletPrices.size(); ++r)
                            trueCapletPrices.set(r, new BlackCalculator(dispayoff,
                                    todaysForwards.get(r),
                                    Math.sqrt(totalCovariance.get(r, r)),
                                    todaysDiscounts.get(r + 1)
                                            * (rateTimes.get(r + 1)
                                            - rateTimes.get(r))).value());

                        List<Double> trueCapPrices = filledList(
                                capsDeflated.numberOfProducts(), 0.0);
                        List<Double> vegaCaps = filledList(
                                capsDeflated.numberOfProducts(), 0.0);

                        for (int s = 0; s < capsDeflated.numberOfProducts(); ++s) {

                            trueCapPrices.set(s, 0.0);

                            for (int t = caps.get(s).startIndex_;
                                 t < caps.get(s).endIndex_; ++t)
                                trueCapPrices.set(s,
                                        trueCapPrices.get(s)
                                                + trueCapletPrices.get(t));
                        }

                        int numberErrors = 0;

                        for (int b = 0; b < vegaBumps.get(0).size(); ++b) {

                            List<Double> bumpedCapletPrices = filledList(
                                    trueCapletPrices.size(), 0.0);
                            //                  std::vector<Real> bumpedCapPrices(trueCapPrices.size());

                            List<Double> variances = filledList(
                                    trueCapletPrices.size(), 0.0);
                            List<Double> vegasCaplets = filledList(
                                    trueCapletPrices.size(), 0.0);

                            for (int step = 0; step < marketModel.numberOfSteps();
                                 ++step) {
                                Matrix pseudoRoot = deepCopyMatrix(
                                        marketModel.pseudoRoot(step));
                                for (int rate = 0; rate < numberRates; ++rate)
                                    for (int f = 0;
                                         f < marketModel.numberOfFactors(); ++f)
                                        pseudoRoot.addEq(rate, f,
                                                vegaBumps.get(step).get(b).get(rate, f));

                                for (int rate = step;
                                     rate < marketModel.numberOfRates(); ++rate) {
                                    double variance = 0.0;
                                    for (int f = 0;
                                         f < marketModel.numberOfFactors(); ++f)
                                        variance += pseudoRoot.get(rate, f)
                                                * pseudoRoot.get(rate, f);

                                    variances.set(rate,
                                            variances.get(rate) + variance);
                                }
                            }

                            for (int r = 0; r < trueCapletPrices.size(); ++r) {
                                bumpedCapletPrices.set(r, new BlackCalculator(
                                        dispayoff, todaysForwards.get(r),
                                        Math.sqrt(variances.get(r)),
                                        todaysDiscounts.get(r + 1)
                                                * (rateTimes.get(r + 1)
                                                - rateTimes.get(r))).value());

                                vegasCaplets.set(r,
                                        bumpedCapletPrices.get(r)
                                                - trueCapletPrices.get(r));
                            }

                            for (int s = 0; s < capsDeflated.numberOfProducts(); ++s) {
                                vegaCaps.set(s, 0.0);

                                for (int t = caps.get(s).startIndex_;
                                     t < caps.get(s).endIndex_; ++t)
                                    vegaCaps.set(s,
                                            vegaCaps.get(s) + vegasCaplets.get(t));
                            }

                            for (int s = 0; s < capsDeflated.numberOfProducts(); ++s) {
                                double mcVega = vegasMatrix.get(s, b);
                                double analyticVega = vegaCaps.get(s);
                                double thisError = mcVega - analyticVega;
                                double thisSE = standardErrors.get(s, b);

                                if (Math.abs(thisError) > 0.0) {
                                    double errorInSEs = Math.abs(thisError / thisSE);

                                    if (errorInSEs > 4.0)
                                        ++numberErrors;
                                }

                            }

                        }

                        if (numberErrors > 0)
                            fail("caps Pathwise vega test fails : " + numberErrors
                                    + "\n");

                    } // end of k loop over measures
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    void testPathwiseMarketVegas() {

        System.out.println("Testing pathwise market vegas in a lognormal "
                + "forward rate market model...");

        setup();

        // specify collection of caps and swaptions and then see if their vegas
        // are correct
        // starting by doing a set of co-terminal swaptions
        LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(todaysForwards);

        List<Payoff> payoffs = new ArrayList<>();
        List<StrikedTypePayoff> displacedPayoffs = new ArrayList<>();
        for (int i = 0; i < todaysForwards.size(); ++i) {
            payoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    cs.coterminalSwapRate(i)));

            displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    cs.coterminalSwapRate(i) + displacement));
        }

        MultiStepOptionlets dummyProduct = new MultiStepOptionlets(rateTimes,
                accruals, paymentTimes, payoffs);

        double bumpSizeNumericalDifferentiation = 1E-6;

        MarketModelPathwiseCoterminalSwaptionsDeflated swaptionsDeflated =
                new MarketModelPathwiseCoterminalSwaptionsDeflated(
                        rateTimes, cs.coterminalSwapRates());
        MarketModelPathwiseCoterminalSwaptionsNumericalDeflated
                swaptionsDeflated2 =
                new MarketModelPathwiseCoterminalSwaptionsNumericalDeflated(
                        rateTimes, cs.coterminalSwapRates(),
                        bumpSizeNumericalDifferentiation);

        EvolutionDescription evolution = dummyProduct.evolution();
        int steps = evolution.numberOfSteps();
        int numberRates = evolution.numberOfRates();

        int pathsToDo = 10; // for the numerical differentiation test we are
                            // requiring equality on each path so this is
                            // actually quite strict
        int pathsToDoSimulation = paths_;

        double multiplier = 50; // how many times the bump size squared, the numerical differentation is allowed to differ by
        double tolerance = 1E-6;

        // printReport_ = true;
        double initialNumeraireValue = 0.95;

        boolean allowFactorwiseBumping = true;
        List<VolatilityBumpInstrumentJacobian.Cap> caps = new ArrayList<>();

        double capStrike = todaysForwards.get(0);

        for (int i = 0; i + 2 < numberRates; i = i + 3) {
            VolatilityBumpInstrumentJacobian.Cap nextCap =
                    new VolatilityBumpInstrumentJacobian.Cap();
            nextCap.startIndex_ = i;
            nextCap.endIndex_ = i + 3;
            nextCap.strike_ = capStrike;
            caps.add(nextCap);
        }
        List<int[]> startsAndEnds = new ArrayList<>();

        for (int j = 0; j < caps.size(); ++j) {
            startsAndEnds.add(new int[]{caps.get(j).startIndex_,
                    caps.get(j).endIndex_});
        }

        MarketModelPathwiseMultiDeflatedCap capsDeflated =
                new MarketModelPathwiseMultiDeflatedCap(
                        rateTimes,
                        accruals,
                        paymentTimes,
                        capStrike,
                        startsAndEnds);

        List<VolatilityBumpInstrumentJacobian.Swaption> swaptions =
                new ArrayList<>();

        for (int i = 0; i < numberRates; ++i) {
            VolatilityBumpInstrumentJacobian.Swaption nextSwaption =
                    new VolatilityBumpInstrumentJacobian.Swaption();
            nextSwaption.startIndex_ = i;
            nextSwaption.endIndex_ = numberRates;
            swaptions.add(nextSwaption);
        }

        MarketModelType[] marketModels = {
                // CalibratedMM,
                // ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility
        };
        ///////////////////////////////////
        ///////////////////////////////////
        // test analytically first, it's faster!

        for (MarketModelType j : marketModels) {

            int[] testedFactors = {Math.min(1, todaysForwards.size())
                    //    todaysForwards.size()
                    //, 4, 8,
            };

            for (int factors : testedFactors) {
                boolean logNormal = true;

                MarketModel marketModel =
                        makeMarketModel(logNormal, evolution, factors, j);

                // we need to work out our bumps

                VegaBumpCollection possibleBumps = new VegaBumpCollection(
                        marketModel,
                        allowFactorwiseBumping);

                OrthogonalizedBumpFinder bumpFinder =
                        new OrthogonalizedBumpFinder(possibleBumps,
                                swaptions,
                                caps,
                                multiplier, // if vector length grows by more than this discard
                                tolerance); // if vector projection before scaling less than this discard
                List<List<Matrix>> theBumps = new ArrayList<>();

                bumpFinder.GetVegaBumps(theBumps);

                // the bumps is now the bumps required to get a one percent implied vol in each instrumnet
                // indexed by step, instrument, pseudo-root matrix
                // if we dot product with swaption derivatives, we should get a 1% change in imp vol on the diagonal
                // and zero off it
                {
                    Matrix swaptionVegasMatrix = new Matrix(
                            swaptionsDeflated.numberOfProducts(),
                            theBumps.get(0).size(), 0.0);

                    for (int i = 0;
                         i < swaptionsDeflated.numberOfProducts(); ++i) {
                        SwaptionPseudoDerivative thisPseudoDerivative =
                                new SwaptionPseudoDerivative(marketModel,
                                        swaptions.get(i).startIndex_,
                                        swaptions.get(i).endIndex_);

                        for (int jj = 0; jj < theBumps.get(0).size(); ++jj) {
                            swaptionVegasMatrix.set(i, jj, 0.0);

                            for (int k = 0; k < steps; ++k)
                                for (int l = 0; l < numberRates; ++l)
                                    for (int m = 0; m < factors; ++m)
                                        swaptionVegasMatrix.set(i, jj,
                                                swaptionVegasMatrix.get(i, jj)
                                                        + theBumps.get(k).get(jj)
                                                        .get(l, m)
                                                        * thisPseudoDerivative
                                                        .volatilityDerivative(k)
                                                        .get(l, m));
                        }
                    }

                    int numberDiagonalFailures = 0;
                    int offDiagonalFailures = 0;

                    for (int i = 0; i < swaptions.size(); ++i) {
                        for (int jj = 0; jj < theBumps.get(0).size(); ++jj) {
                            if (i == jj) {
                                double thisError =
                                        swaptionVegasMatrix.get(i, i) - 0.01;

                                if (Math.abs(thisError) > 1e-8)
                                    ++numberDiagonalFailures;
                            } else {
                                double thisError =
                                        swaptionVegasMatrix.get(i, jj);
                                if (Math.abs(thisError) > 1e-8)
                                    ++offDiagonalFailures;
                            }
                        }
                    }

                    if (numberDiagonalFailures + offDiagonalFailures > 0)
                        fail("Pathwise market vega analytic test fails for  "
                                + "swaptions : " + offDiagonalFailures
                                + " off diagonal failures \n "
                                + " and " + numberDiagonalFailures
                                + " on the diagonal.");
                }
                // now do the caps

                Matrix capsVegasMatrix = new Matrix(caps.size(),
                        theBumps.get(0).size(), 0.0);

                for (int i = 0; i < caps.size(); ++i) {
                    CapPseudoDerivative thisPseudoDerivative =
                            new CapPseudoDerivative(marketModel,
                                    caps.get(i).strike_,
                                    caps.get(i).startIndex_,
                                    caps.get(i).endIndex_,
                                    initialNumeraireValue);

                    for (int jj = 0; jj < theBumps.get(0).size(); ++jj) {
                        capsVegasMatrix.set(i, jj, 0.0);

                        for (int k = 0; k < steps; ++k)
                            for (int l = 0; l < numberRates; ++l)
                                for (int m = 0; m < factors; ++m)
                                    capsVegasMatrix.set(i, jj,
                                            capsVegasMatrix.get(i, jj)
                                                    + theBumps.get(k).get(jj)
                                                    .get(l, m)
                                                    * thisPseudoDerivative
                                                    .volatilityDerivative(k)
                                                    .get(l, m));
                    }
                }

                int numberDiagonalFailures = 0;
                int offDiagonalFailures = 0;

                for (int i = 0; i < caps.size(); ++i) {
                    for (int jj = 0; jj < theBumps.get(0).size(); ++jj) {
                        if (i + swaptions.size() == jj) {
                            double thisError =
                                    capsVegasMatrix.get(i, jj) - 0.01;

                            if (Math.abs(thisError) > 1e-8)
                                ++numberDiagonalFailures;
                        } else {
                            double thisError = capsVegasMatrix.get(i, jj);
                            if (Math.abs(thisError) > 1e-8)
                                ++offDiagonalFailures;
                        }
                    }
                }

                if (numberDiagonalFailures + offDiagonalFailures > 0)
                    fail("Pathwise market vega analytic test fails for caps : "
                            + offDiagonalFailures
                            + " off diagonal failures \n "
                            + " and " + numberDiagonalFailures
                            + " on the diagonal.");

            } // end of  for (Size m=0; m<std::size(testedFactors); ++m)
        }     // end of   for (Size j=0; j<std::size(marketModels); j++)
        ///////////////////////////////////////////////////////////////////////
        // test numerically differentiated swaptions against analytically done
        // ones
        // we require equality on very path so we don't need many paths

        List<Integer> numberCashFlowsThisStep1 = new ArrayList<>();
        for (int i = 0; i < swaptionsDeflated.numberOfProducts(); ++i)
            numberCashFlowsThisStep1.add(0);

        List<List<MarketModelPathwiseMultiProduct.CashFlow>>
                cashFlowsGenerated1 = new ArrayList<>();

        for (int i = 0; i < swaptionsDeflated.numberOfProducts(); ++i) {
            List<MarketModelPathwiseMultiProduct.CashFlow> innerCashFlows =
                    new ArrayList<>();
            for (int j = 0;
                 j < swaptionsDeflated.maxNumberOfCashFlowsPerProductPerStep();
                 ++j) {
                MarketModelPathwiseMultiProduct.CashFlow cashFlow =
                        new MarketModelPathwiseMultiProduct.CashFlow();
                cashFlow.timeIndex = 0;
                cashFlow.amount = filledList(numberRates + 1, 0.0);
                innerCashFlows.add(cashFlow);
            }
            cashFlowsGenerated1.add(innerCashFlows);
        }

        List<Integer> numberCashFlowsThisStep2 =
                new ArrayList<>(numberCashFlowsThisStep1);
        // Official copies the nested vectors, resized amount arrays included.
        // Each CashFlow.amount must be copied too or the two products would
        // write into the same amounts.
        List<List<MarketModelPathwiseMultiProduct.CashFlow>>
                cashFlowsGenerated2 = new ArrayList<>();
        for (List<MarketModelPathwiseMultiProduct.CashFlow> inner
                : cashFlowsGenerated1) {
            List<MarketModelPathwiseMultiProduct.CashFlow> innerCopy =
                    new ArrayList<>();
            for (MarketModelPathwiseMultiProduct.CashFlow cashFlow : inner) {
                MarketModelPathwiseMultiProduct.CashFlow copy =
                        new MarketModelPathwiseMultiProduct.CashFlow();
                copy.timeIndex = cashFlow.timeIndex;
                copy.amount = new ArrayList<>(cashFlow.amount);
                innerCopy.add(copy);
            }
            cashFlowsGenerated2.add(innerCopy);
        }

        for (MarketModelType j : marketModels) {

            int[] testedFactors = {Math.min(1, todaysForwards.size())
                    //    todaysForwards.size()
                    //, 4, 8,
            };

            for (int factors : testedFactors) {
                MTBrownianGeneratorFactory generatorFactory =
                        new MTBrownianGeneratorFactory(seed_);

                boolean logNormal = true;

                MarketModel marketModel =
                        makeMarketModel(logNormal, evolution, factors, j);

                LogNormalFwdRateEuler evolver1 = new LogNormalFwdRateEuler(
                        marketModel,
                        generatorFactory,
                        swaptionsDeflated.suggestedNumeraires());

                LogNormalFwdRateEuler evolver2 = new LogNormalFwdRateEuler(
                        marketModel,
                        generatorFactory,
                        swaptionsDeflated.suggestedNumeraires());

                for (int p = 0; p < pathsToDo; ++p) {
                    evolver1.startNewPath();
                    swaptionsDeflated.reset();
                    evolver2.startNewPath();
                    swaptionsDeflated2.reset();
                    int step = 0;

                    boolean done;

                    do {
                        evolver1.advanceStep();
                        done = swaptionsDeflated.nextTimeStep(
                                evolver1.currentState(),
                                numberCashFlowsThisStep1,
                                cashFlowsGenerated1);

                        evolver2.advanceStep();
                        boolean done2 = swaptionsDeflated2.nextTimeStep(
                                evolver2.currentState(),
                                numberCashFlowsThisStep2,
                                cashFlowsGenerated2);

                        if (done != done2)
                            fail("numerical swaptions derivative and swaptions "
                                    + "disagree on termination");

                        for (int prod = 0;
                             prod < swaptionsDeflated.numberOfProducts();
                             ++prod) {
                            if (!numberCashFlowsThisStep1.get(prod)
                                    .equals(numberCashFlowsThisStep2.get(prod)))
                                fail("numerical swaptions derivative and "
                                        + "swaptions disagree on number of "
                                        + "cash flows");

                            for (int cf = 0;
                                 cf < numberCashFlowsThisStep1.get(prod); ++cf)
                                for (int rate = 0; rate <= numberRates; ++rate)
                                    if (Math.abs(
                                            cashFlowsGenerated1.get(prod)
                                                    .get(cf).amount.get(rate)
                                                    - cashFlowsGenerated2
                                                    .get(prod).get(cf)
                                                    .amount.get(rate))
                                            > tolerance)
                                        fail("numerical swaptions derivative "
                                                + "and swaptions disagree on "
                                                + "cash flow size. cf = " + cf
                                                + "step " + step + ", rate "
                                                + rate + ", amount1 "
                                                + cashFlowsGenerated1
                                                .get(prod).get(cf)
                                                .amount.get(rate)
                                                + " ,amount2 "
                                                + cashFlowsGenerated2
                                                .get(prod).get(cf)
                                                .amount.get(rate) + "\n");
                        }

                        ++step;

                    } while (!done);

                }

            } // end of  for (Size m=0; m<std::size(testedFactors); ++m)
        }     // end of   for (Size j=0; j<std::size(marketModels); j++)

        /////////////////////////////////////

        // now time for the full simulation test
        // measure vega of each swaption with respect to itself, the other
        // swaptions and the caps
        // should get 0.01 and 0 respectively.
        for (MarketModelType j : marketModels) {

            int[] testedFactors = {Math.min(1, todaysForwards.size())
                    //    todaysForwards.size()
                    //, 4, 8,
            };

            for (int factors : testedFactors) {
                MTBrownianGeneratorFactory generatorFactory =
                        new MTBrownianGeneratorFactory(seed_);

                boolean logNormal = true;

                MarketModel marketModel =
                        makeMarketModel(logNormal, evolution, factors, j);

                LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(
                        marketModel,
                        generatorFactory,
                        swaptionsDeflated.suggestedNumeraires());

                int initialNumeraire = evolver.numeraires().get(0);
                // Official shadows the function-scope 0.95 here and recomputes
                // from todaysDiscounts; Java forbids that shadowing.
                double initialNumeraireValueSimulation =
                        todaysDiscounts.get(initialNumeraire);

                // we need to work out our bumps

                VegaBumpCollection possibleBumps = new VegaBumpCollection(
                        marketModel,
                        allowFactorwiseBumping);

                OrthogonalizedBumpFinder bumpFinder =
                        new OrthogonalizedBumpFinder(possibleBumps,
                                swaptions,
                                caps,
                                multiplier, // if vector length grows by more than this discard
                                tolerance); // if vector projection before scaling less than this discard
                List<List<Matrix>> theBumps = new ArrayList<>();

                bumpFinder.GetVegaBumps(theBumps);

                List<Double> values = new ArrayList<>();

                List<Double> errors = new ArrayList<>();

                {

                    PathwiseVegasAccountingEngine accountingEngine =
                            new PathwiseVegasAccountingEngine(
                                    new LogNormalFwdRateEuler(evolver),
                                    swaptionsDeflated,
                                    marketModel,
                                    theBumps,
                                    initialNumeraireValueSimulation);

                    accountingEngine.multiplePathValues(values, errors,
                            pathsToDoSimulation);
                }

                // we now have the simulation vegas, put them in more
                // convenient form

                Matrix vegasMatrix = new Matrix(
                        swaptionsDeflated.numberOfProducts(),
                        theBumps.get(0).size(), 0.0);
                Matrix standardErrors = new Matrix(
                        swaptionsDeflated.numberOfProducts(),
                        theBumps.get(0).size(), 0.0);
                int entriesPerProduct = 1 + numberRates
                        + theBumps.get(0).size();

                for (int i = 0;
                     i < swaptionsDeflated.numberOfProducts(); ++i) {
                    for (int jj = 0; jj < theBumps.get(0).size(); ++jj) {
                        vegasMatrix.set(i, jj,
                                values.get(i * entriesPerProduct
                                        + numberRates + 1 + jj));
                        standardErrors.set(i, jj,
                                errors.get(i * entriesPerProduct
                                        + numberRates + 1 + jj));
                    }
                }

                // we next get the model vegas for comparison

                List<Double> impliedVols_ =
                        filledList(swaptions.size(), 0.0);

                for (int i = 0; i < swaptions.size(); ++i)
                    impliedVols_.set(i,
                            SwapForwardMappings.swaptionImpliedVolatility(
                                    marketModel,
                                    swaptions.get(i).startIndex_,
                                    swaptions.get(i).endIndex_));

                List<Double> analyticVegas = filledList(swaptions.size(), 0.0);
                for (int i = 0; i < swaptions.size(); ++i) {
                    double swapRate = cs.coterminalSwapRates().get(i);
                    double annuity = cs.coterminalSwapAnnuity(0, i)
                            * initialNumeraireValueSimulation;
                    double expiry = rateTimes.get(i);
                    double sd = impliedVols_.get(i) * Math.sqrt(expiry);
                    double swapDisplacement = 0.0;

                    double vega = BlackFormula.blackFormulaVolDerivative(
                            swapRate,
                            swapRate,
                            sd,
                            expiry,
                            annuity,
                            swapDisplacement);

                    analyticVegas.set(i, vega * 0.01); // one percent move

                }

                // diagonal vegas should agree up to standard errors
                // off diagonal vegas should be zero

                int numberDiagonalFailures = 0;
                int offDiagonalFailures = 0;

                for (int i = 0; i < swaptions.size(); ++i) {
                    double thisError = vegasMatrix.get(i, i)
                            - analyticVegas.get(i);
                    double thisErrorInSds = thisError
                            / (standardErrors.get(i, i) + 1E-6); // silly to penalize for tiny standard error

                    if (Math.abs(thisErrorInSds) > 4)
                        ++numberDiagonalFailures;

                }

                for (int i = 0; i < swaptions.size(); ++i) {
                    for (int jj = 0; jj < theBumps.get(0).size(); ++jj) {
                        if (i != jj) {
                            double thisError = vegasMatrix.get(i, jj); // true value is zero

                            double thisErrorInSds = thisError
                                    / (standardErrors.get(i, jj) + 1E-6);

                            if (Math.abs(thisErrorInSds) > 3.5)
                                ++offDiagonalFailures;
                        }
                    }
                }

                if (offDiagonalFailures + numberDiagonalFailures > 0)
                    fail("Pathwise market vega test fails for coterminal "
                            + "swaptions : " + offDiagonalFailures
                            + " off diagonal failures \n "
                            + " and " + numberDiagonalFailures
                            + " on the diagonal.");

            } // end of  for (Size m=0; m<std::size(testedFactors); ++m)
        }     // end of   for (Size j=0; j<std::size(marketModels); j++)

        /////////////////////////////////////
        /////////////////////////////////////

        // now time for the full simulation test
        // measure vega of each caps with respect to itself, the swaptions and
        // the other caps
        // should get 0.01, 0 and 0 respectively.
        for (MarketModelType j : marketModels) {

            int[] testedFactors = {Math.min(2, todaysForwards.size())
                    //    todaysForwards.size()
                    //, 4, 8,
            };

            for (int factors : testedFactors) {
                MTBrownianGeneratorFactory generatorFactory =
                        new MTBrownianGeneratorFactory(seed_);

                boolean logNormal = true;

                MarketModel marketModel =
                        makeMarketModel(logNormal, evolution, factors, j);

                LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(
                        marketModel,
                        generatorFactory,
                        capsDeflated.suggestedNumeraires());

                int initialNumeraire = evolver.numeraires().get(0);
                double initialNumeraireValueSimulation =
                        todaysDiscounts.get(initialNumeraire);

                // we need to work out our bumps

                VegaBumpCollection possibleBumps = new VegaBumpCollection(
                        marketModel,
                        allowFactorwiseBumping);

                OrthogonalizedBumpFinder bumpFinder =
                        new OrthogonalizedBumpFinder(possibleBumps,
                                swaptions,
                                caps,
                                multiplier, // if vector length grows by more than this discard
                                tolerance); // if vector projection before scaling less than this discard
                List<List<Matrix>> theBumps = new ArrayList<>();

                bumpFinder.GetVegaBumps(theBumps);

                List<Double> values = new ArrayList<>();

                List<Double> errors = new ArrayList<>();

                {

                    PathwiseVegasAccountingEngine accountingEngine =
                            new PathwiseVegasAccountingEngine(
                                    new LogNormalFwdRateEuler(evolver),
                                    capsDeflated,
                                    marketModel,
                                    theBumps,
                                    initialNumeraireValueSimulation);

                    accountingEngine.multiplePathValues(values, errors,
                            pathsToDoSimulation);
                }

                // we now have the simulation vegas, put them in more
                // convenient form

                Matrix vegasMatrix = new Matrix(
                        capsDeflated.numberOfProducts(),
                        theBumps.get(0).size(), 0.0);
                Matrix standardErrors = new Matrix(
                        capsDeflated.numberOfProducts(),
                        theBumps.get(0).size(), 0.0);
                int entriesPerProduct = 1 + numberRates
                        + theBumps.get(0).size();

                for (int i = 0; i < capsDeflated.numberOfProducts(); ++i) {
                    for (int jj = 0; jj < theBumps.get(0).size(); ++jj) {
                        vegasMatrix.set(i, jj,
                                values.get(i * entriesPerProduct
                                        + numberRates + jj + 1));
                        standardErrors.set(i, jj,
                                errors.get(i * entriesPerProduct
                                        + numberRates + jj + 1));
                    }
                }

                // we next get the model vegas for comparison

                List<Double> impliedVols_ = filledList(caps.size(), 0.0);

                List<Double> analyticVegas = filledList(caps.size(), 0.0);
                for (int i = 0; i < caps.size(); ++i) {

                    CapPseudoDerivative capPseudo =
                            new CapPseudoDerivative(marketModel,
                                    caps.get(i).strike_,
                                    caps.get(i).startIndex_,
                                    caps.get(i).endIndex_,
                                    initialNumeraireValueSimulation);

                    impliedVols_.set(i, capPseudo.impliedVolatility());

                    double vega = 0.0;

                    for (int jj = caps.get(i).startIndex_;
                         jj < caps.get(i).endIndex_; ++jj) {

                        double forward = cs.forwardRates().get(jj);
                        double annuity = cs.discountRatio(jj + 1, 0)
                                * initialNumeraireValueSimulation
                                * accruals.get(jj);
                        double expiry = rateTimes.get(jj);
                        double sd = impliedVols_.get(i) * Math.sqrt(expiry);
                        double displacement = 0.0;

                        double capletVega =
                                BlackFormula.blackFormulaVolDerivative(
                                        caps.get(i).strike_,
                                        forward,
                                        sd,
                                        expiry,
                                        annuity,
                                        displacement);

                        vega += capletVega;
                    }

                    analyticVegas.set(i, vega * 0.01); // one percent move

                }

                // diagonal vegas should agree up to standard errors
                // off diagonal vegas should be zero

                int numberDiagonalFailures = 0;
                int offDiagonalFailures = 0;

                for (int i = 0; i < caps.size(); ++i) {
                    double thisError =
                            vegasMatrix.get(i, i + swaptions.size())
                                    - analyticVegas.get(i);
                    double thisErrorInSds = thisError
                            / (standardErrors.get(i, i + swaptions.size())
                            + 1E-6); // silly to penalize for tiny standard error

                    if (Math.abs(thisErrorInSds) > 4) {
                        System.out.println(" MC cap vega: "
                                + vegasMatrix.get(i, i + swaptions.size())
                                + " Analytic cap vega:"
                                + analyticVegas.get(i) + " Error in sds:"
                                + thisErrorInSds + "\n");
                        ++numberDiagonalFailures;
                    }

                }

                for (int i = 0; i < caps.size(); ++i) {
                    for (int jj = 0; jj < theBumps.get(0).size(); ++jj) {
                        if (i + swaptions.size() != jj) {
                            double thisError = vegasMatrix.get(i, jj); // true value is zero

                            double thisErrorInSds = thisError
                                    / (standardErrors.get(i, jj) + 1E-6);

                            if (Math.abs(thisErrorInSds) > 3.5)
                                ++offDiagonalFailures;
                        }
                    }
                }

                if (offDiagonalFailures + numberDiagonalFailures > 0)
                    fail("Pathwise market vega test fails for caps: "
                            + offDiagonalFailures
                            + " off diagonal failures \n "
                            + " and " + numberDiagonalFailures
                            + " on the diagonal.");

            } // end of  for (Size m=0; m<std::size(testedFactors); ++m)
        }     // end of   for (Size j=0; j<std::size(marketModels); j++)

        /////////////////////////////////////

    }

    @Test
    void testStochVolForwardsAndOptionlets() {
        System.out.println(
                "Testing exact repricing of "
                        + "forwards and optionlets "
                        + "in a stochastic vol displaced diffusion forward rate market model...");

        setup();

        List<Double> forwardStrikes = filledList(todaysForwards.size(), 0.0);
        List<Payoff> optionletPayoffs = new ArrayList<>();
        /* List<PlainVanillaPayoff> displacedPayoffs =
               new ArrayList<>(todaysForwards.size()); */
        for (int i = 0; i < todaysForwards.size(); ++i) {
            forwardStrikes.set(i, todaysForwards.get(i) + 0.01);
            optionletPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    todaysForwards.get(i)));
            /* displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                   todaysForwards.get(i) + displacement)); */
        }

        MultiStepForwards forwards = new MultiStepForwards(rateTimes, accruals,
                paymentTimes, forwardStrikes);
        MultiStepOptionlets optionlets = new MultiStepOptionlets(rateTimes, accruals,
                paymentTimes, optionletPayoffs);

        MultiProductComposite product = new MultiProductComposite();
        product.add(forwards);
        product.add(optionlets);
        product.finalize();

        EvolutionDescription evolution = product.evolution();

        MarketModelType[] marketModels = {
                MarketModelType.ExponentialCorrelationFlatVolatility
        };

        int firstVolatilityFactor = 2;
        int volatilityFactorStep = 2;

        double meanLevel = 1.0;
        double reversionSpeed = 1.0;

        double volVar = 1;
        double v0 = 1.0;
        int numberSubSteps = 8;
        double w1 = 0.5;
        double w2 = 0.5;
        double cutPoint = 1.5;

        MarketModelVolProcess volProcess = new SquareRootAndersen(meanLevel,
                reversionSpeed,
                volVar,
                v0,
                evolution.evolutionTimes(),
                numberSubSteps,
                w1,
                w2,
                cutPoint);

        for (MarketModelType j : marketModels) {

            int[] testedFactors = {1, 2, todaysForwards.size()};
            for (int factors : testedFactors) {
                MeasureType[] measures = {MeasureType.MoneyMarket, MeasureType.Terminal};

                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(product, measure);

                    boolean logNormal = true;
                    MarketModel marketModel = makeMarketModel(logNormal, evolution, factors, j);

                    for (int n = 0; n < 1; n++) {
                        MTBrownianGeneratorFactory generatorFactory =
                                new MTBrownianGeneratorFactory(seed_);

                        MarketModelEvolver evolver = new SVDDFwdRatePc(marketModel,
                                generatorFactory,
                                volProcess,
                                firstVolatilityFactor,
                                volatilityFactorStep,
                                numeraires);

                        String config = marketModelTypeToString(j) + ", " + factors
                                + (factors > 1
                                ? (factors == todaysForwards.size()
                                ? " (full) factors, "
                                : " factors, ")
                                : " factor,")
                                + measureTypeToString(measure) + ", "
                                + "SVDDFwdRatePc"
                                + ", "
                                + "MT BGF";
                        if (printReport_)
                            System.out.println("    " + config);

                        SequenceStatisticsInc stats = simulate(evolver, product);

                        List<Double> results = stats.mean();
                        List<Double> errors = stats.errorEstimate();

                        // check forwards

                        for (int i = 0; i < accruals.size(); ++i) {
                            double trueValue = todaysDiscounts.get(i)
                                    - todaysDiscounts.get(i + 1)
                                    * (1 + forwardStrikes.get(i) * accruals.get(i));
                            double error = results.get(i) - trueValue;
                            double errorSds = error / errors.get(i);

                            if (Math.abs(errorSds) > 3.5)
                                fail("error in sds: " + errorSds
                                        + " for forward " + i
                                        + " in SV LMM test. True value:" + trueValue
                                        + ", actual value: " + results.get(i)
                                        + " , standard error " + errors.get(i));
                        }

                        for (int i = 0; i < accruals.size(); ++i) {

                            double volCoeff = volatilities.get(i);
                            // sqrt(marketModel->totalCovariance(i)[i][i]/evolution.evolutionTimes()[i]);
                            double theta = volCoeff * volCoeff * meanLevel;
                            double kappa = reversionSpeed;
                            double sigma = volCoeff * volVar;
                            double rho = 0.0;
                            double v1 = v0 * volCoeff * volCoeff;

                            StrikedTypePayoff payoff =
                                    new PlainVanillaPayoff(Option.Type.Call, forwardStrikes.get(i));

                            double trueValue = new AnalyticHestonEngine(
                                    new HestonModel(
                                            new HestonProcess(
                                                    new Handle<>(flatRate(0.0, dayCounter), true),
                                                    new Handle<>(flatRate(0.0, dayCounter), true),
                                                    new Handle<>(new SimpleQuote(
                                                            todaysForwards.get(i) + displacement), true),
                                                    v1, kappa, theta, sigma, rho)),
                                    AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                                    FourierIntegration.gaussLaguerre(128)
                            ).priceVanillaPayoff(
                                    new PlainVanillaPayoff(payoff.optionType(),
                                            todaysForwards.get(i) + displacement),
                                    rateTimes.get(i));

                            trueValue *= accruals.get(i) * todaysDiscounts.get(i + 1);

                            //        trueValue =
                            //                              BlackCalculator(displacedPayoffs[i],
                            //                                                todaysForwards[i]+displacement,
                            //                                             volatilities[i]*std::sqrt(rateTimes[i]),
                            //                                              todaysDiscounts[i+1]*accruals[i]).value();

                            double error = results.get(i + accruals.size()) - trueValue;
                            double errorSds = error / errors.get(i);

                            if (Math.abs(errorSds) > 4)
                                fail("error in sds: " + errorSds
                                        + " for caplet " + i
                                        + " in SV LMM test. True value:" + trueValue
                                        + ", actual value: " + results.get(i + accruals.size())
                                        + " , standard error " + errors.get(i));
                        }
                    }
                }
            }
        }
    }

    // Official test-suite utilities.flatRate(Rate, DayCounter).
    private YieldTermStructure flatRate(double forward, DayCounter dc) {
        return new FlatForward(0, new NullCalendar(),
                new Handle<>(new SimpleQuote(forward), true), dc);
    }

    private static Matrix deepCopyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
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
