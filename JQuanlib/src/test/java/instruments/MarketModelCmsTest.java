package instruments;

import jquant.Option;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.math.MathUtils;
import jquant.math.Matrix;
import jquant.models.marketmodels.AccountingEngine;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.MarketModelEvolver;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.SequenceStatisticsInc;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.browniangenerators.SobolBrownianGeneratorBase;
import jquant.models.marketmodels.browniangenerators.SobolBrownianGeneratorFactory;
import jquant.models.marketmodels.correlations.ExponentialCorrelations;
import jquant.models.marketmodels.correlations.TimeHomogeneousForwardCorrelation;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.models.marketmodels.evolvers.LogNormalCmSwapRatePc;
import jquant.models.marketmodels.models.AbcdVol;
import jquant.models.marketmodels.models.FlatVol;
import jquant.models.marketmodels.products.MultiProductComposite;
import jquant.models.marketmodels.products.multistep.MultiStepCoterminalSwaps;
import jquant.models.marketmodels.products.multistep.MultiStepCoterminalSwaptions;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.SimpleDayCounter;
import jquant.time.Schedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/marketmodel_cms.cpp} port (single named
 * case {@code testMultiStepCmSwapsAndSwaptions}).
 *
 * <p>Ported from QuantLib at SHA {@code cf74c9f}. Official data, 32767 Sobol
 * paths (seed 42, {@code Diagonal} ordering), full-factor models and the
 * official 0.5 / 2.0 standard-error thresholds are kept unchanged.
 */
public class MarketModelCmsTest {

    private SavedSettings savedSettings;

    private Date todaysDate;
    private Date startDate;
    private Date endDate;
    private List<Double> rateTimes;
    private List<Double> accruals;
    private Calendar calendar;
    private DayCounter dayCounter;
    private List<Double> todaysForwards;
    private List<Double> todaysCMSwapRates;
    private List<Double> cMSwapAnnuity;
    private double displacement;
    private List<Double> todaysDiscounts;
    private List<Double> volatilities;
    private List<Double> blackVols;
    private double a, b, c, d;
    private double longTermCorrelation, beta;
    private int measureOffset_;
    private long seed_;
    private int paths_, trainingPaths_;
    private boolean printReport_ = false;
    private int spanningForwards;

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
        endDate = todaysDate.add(new Period(10, TimeUnit.YEARS));
        Schedule dates = new Schedule(todaysDate, endDate,
                new Period(Frequency.SEMIANNUAL),
                calendar, BusinessDayConvention.FOLLOWING,
                BusinessDayConvention.FOLLOWING,
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        rateTimes = filledList(dates.size() - 1, 0.0);

        accruals = filledList(rateTimes.size() - 1, 0.0);
        dayCounter = new SimpleDayCounter();
        for (int i = 1; i < dates.size(); ++i)
            rateTimes.set(i - 1, dayCounter.yearFraction(todaysDate, dates.dates().get(i)));

        for (int i = 1; i < rateTimes.size(); ++i)
            accruals.set(i - 1, rateTimes.get(i) - rateTimes.get(i - 1));

        // Rates & displacement
        todaysForwards = filledList(accruals.size(), 0.0);
        displacement = 0.02;
        for (int i = 0; i < todaysForwards.size(); ++i)
            todaysForwards.set(i, 0.03 + 0.0010 * i);
        LMMCurveState curveState_lmm = new LMMCurveState(rateTimes);
        curveState_lmm.setOnForwardRates(todaysForwards);
        // until ConstantMaturitySwap is ready
        spanningForwards = todaysForwards.size();
        todaysCMSwapRates = new ArrayList<>(
                curveState_lmm.cmSwapRates(spanningForwards));

        // Discounts
        todaysDiscounts = filledList(rateTimes.size(), 0.0);
        todaysDiscounts.set(0, 0.95);
        for (int i = 1; i < rateTimes.size(); ++i)
            todaysDiscounts.set(i, todaysDiscounts.get(i - 1)
                    / (1.0 + todaysForwards.get(i - 1) * accruals.get(i - 1)));

        // Swaption Volatilities
        double[] mktVols = {0.15541283,
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
        volatilities = filledList(todaysCMSwapRates.size(), 0.0);
        blackVols = filledList(todaysCMSwapRates.size(), 0.0);
        for (int i = 0; i < todaysCMSwapRates.size(); i++) {
            volatilities.set(i, todaysCMSwapRates.get(i) * mktVols[i]
                    / (todaysCMSwapRates.get(i) + displacement));
            blackVols.set(i, mktVols[i]);
        }

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

    private MarketModel makeMarketModel(final EvolutionDescription evolution,
                                        int numberOfFactors,
                                        MarketModelType marketModelType) {
        return makeMarketModel(evolution, numberOfFactors, marketModelType, 0.0, 0.0);
    }

    private MarketModel makeMarketModel(final EvolutionDescription evolution,
                                        int numberOfFactors,
                                        MarketModelType marketModelType,
                                        double rateBump,
                                        double volBump) {
        List<Double> fixingTimes = new ArrayList<>(evolution.rateTimes());
        fixingTimes.remove(fixingTimes.size() - 1);
        // Official also constructs LmExtLinearExponentialVolModel and
        // LmLinearExponentialCorrelationModel here, but they are only used by
        // the commented-out CalibratedMM branch; they are omitted.

        List<Double> bumpedRates = filledList(todaysCMSwapRates.size(), 0.0);
        LMMCurveState curveState_lmm = new LMMCurveState(rateTimes);
        curveState_lmm.setOnForwardRates(todaysForwards);
        List<Double> usedRates =
                curveState_lmm.cmSwapRates(spanningForwards);
        for (int i = 0; i < usedRates.size(); ++i)
            bumpedRates.set(i, usedRates.get(i) + rateBump);

        List<Double> bumpedVols = filledList(volatilities.size(), 0.0);
        for (int i = 0; i < volatilities.size(); ++i)
            bumpedVols.set(i, volatilities.get(i) + volBump);
        Matrix correlations = ExponentialCorrelations.exponentialCorrelations(
                evolution.rateTimes(), longTermCorrelation, beta);
        PiecewiseConstantCorrelation corr = new TimeHomogeneousForwardCorrelation(
                correlations, evolution.rateTimes());
        List<Double> displacements = filledList(bumpedRates.size(), displacement);
        switch (marketModelType) {
            case ExponentialCorrelationFlatVolatility:
                return new FlatVol(bumpedVols,
                        corr,
                        evolution,
                        numberOfFactors,
                        bumpedRates,
                        displacements);
            case ExponentialCorrelationAbcdVolatility:
                return new AbcdVol(0.0, 0.0, 1.0, 1.0,
                        bumpedVols,
                        corr,
                        evolution,
                        numberOfFactors,
                        bumpedRates,
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
                    fail("failure in verifying Terminal measure:\n" +
                            toStream(result));
                }
                break;
            case MoneyMarket:
                result = EvolutionDescription.moneyMarketMeasure(evolution);
                if (!EvolutionDescription.isInMoneyMarketMeasure(evolution, result)) {
                    fail("failure in verifying MoneyMarket measure:\n" +
                            toStream(result));
                }
                break;
            case MoneyMarketPlus:
                result = EvolutionDescription.moneyMarketPlusMeasure(evolution, measureOffset_);
                if (!EvolutionDescription.isInMoneyMarketPlusMeasure(evolution, result,
                        measureOffset_)) {
                    fail("failure in verifying MoneyMarketPlus(" +
                            measureOffset_ + ") measure:\n" +
                            toStream(result));
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
        Ipc, Pc, NormalPc
    }

    private static String evolverTypeToString(EvolverType type) {
        switch (type) {
            case Ipc:
                return "iterative predictor corrector";
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
            final jquant.models.marketmodels.BrownianGeneratorFactory generatorFactory,
            EvolverType evolverType) {
        switch (evolverType) {
            case Pc:
                return new LogNormalCmSwapRatePc(spanningForwards,
                        marketModel, generatorFactory,
                        numeraires,
                        0);
            default:
                throw new IllegalArgumentException(
                        "unknown ConstantMaturitySwapMarketModelEvolver type");
        }
    }

    private void checkCMSAndSwaptions(final SequenceStatisticsInc stats,
                                      final double fixedRate,
                                      final List<StrikedTypePayoff> displacedPayoff,
                                      final MarketModel marketModel,
                                      final String config) {
        List<Double> results = stats.mean();
        List<Double> errors = stats.errorEstimate();
        double[] discrepancies = new double[todaysForwards.size()];

        int N = todaysForwards.size();
        // check Swaps
        double maxError = MathUtils.QL_MIN_REAL;
        LMMCurveState curveState_lmm = new LMMCurveState(rateTimes);
        curveState_lmm.setOnForwardRates(todaysForwards);

        double[] expectedNPVs = new double[todaysCMSwapRates.size()];
        double errorThreshold = 0.5;
        for (int i = 0; i < N; ++i) {
            double expectedNPV = curveState_lmm.cmSwapAnnuity(i, i, spanningForwards)
                    * (todaysCMSwapRates.get(i) - fixedRate) * todaysDiscounts.get(i);
            expectedNPVs[i] = expectedNPV;
            discrepancies[i] = (results.get(i) - expectedNPVs[i]) / errors.get(i);
            maxError = Math.max(Math.abs(discrepancies[i]), maxError);
        }

        if (maxError > errorThreshold) {
            StringBuilder message = new StringBuilder();
            message.append(config).append("\n");
            for (int i = 0; i < N; ++i) {
                message.append(Utilities.ordinal(i + 1)).append(" CMS NPV: ")
                        .append(rate(results.get(i)))
                        .append(" +- ").append(rate(errors.get(i)))
                        .append("; expected: ").append(rate(expectedNPVs[i]))
                        .append("; discrepancy/error = ")
                        .append(discrepancies[N - 1 - i])
                        .append(" standard errors\n");
            }
            message.append("test failed");
            fail(message.toString());
        }

        // check Swaptions
        maxError = 0;

        double[] expectedSwaptions = new double[N];
        for (int i = 0; i < N; ++i) {
            double expectedSwaption =
                    new BlackCalculator(displacedPayoff.get(i),
                            todaysCMSwapRates.get(i) + displacement,
                            volatilities.get(i) * Math.sqrt(rateTimes.get(i)),
                            curveState_lmm.cmSwapAnnuity(i, i, spanningForwards)
                                    * todaysDiscounts.get(i)).value();
            expectedSwaptions[i] = expectedSwaption;
            discrepancies[i] = (results.get(N + i) - expectedSwaptions[i]) / errors.get(N + i);
            maxError = Math.max(Math.abs(discrepancies[i]), maxError);
        }
        errorThreshold = 2.0;

        if (maxError > errorThreshold) {
            StringBuilder message = new StringBuilder();
            message.append(config).append("\n");
            for (int i = 1; i <= N; ++i) {
                message.append(Utilities.ordinal(i)).append(" Swaption: ")
                        .append(rate(results.get(2 * N - i)))
                        .append(" +- ").append(rate(errors.get(2 * N - i)))
                        .append("; expected: ").append(rate(expectedSwaptions[N - i]))
                        .append("; discrepancy/error = ")
                        .append(percent(discrepancies[N - i]))
                        .append(" standard errors\n");
            }
            message.append("test failed");
            fail(message.toString());
        }
    }

    @Test
    void testMultiStepCmSwapsAndSwaptions() {
        // Testing exact repricing of multi-step constant maturity swaps and
        // swaptions in a lognormal constant maturity swap market model...
        setup();

        double fixedRate = 0.04;

        // swaps
        List<Double> swapPaymentTimes = new ArrayList<>(
                rateTimes.subList(1, rateTimes.size()));
        // until ConstantMaturitySwap is ready
        MultiStepCoterminalSwaps swaps = new MultiStepCoterminalSwaps(rateTimes, accruals,
                accruals,
                swapPaymentTimes,
                fixedRate);
        // swaptions
        List<Double> swaptionPaymentTimes = new ArrayList<>(
                rateTimes.subList(0, rateTimes.size() - 1));
        List<StrikedTypePayoff> displacedPayoff =
                filledPayoffs(todaysForwards.size(), fixedRate + displacement);
        List<StrikedTypePayoff> undisplacedPayoff =
                filledPayoffs(todaysForwards.size(), fixedRate);

        // until ConstantMaturitySwap is ready
        MultiStepCoterminalSwaptions swaptions = new MultiStepCoterminalSwaptions(rateTimes,
                swaptionPaymentTimes,
                undisplacedPayoff);
        MultiProductComposite product = new MultiProductComposite();
        product.add(swaps);
        product.add(swaptions);
        product.finalize();

        EvolutionDescription evolution = product.evolution();

        MarketModelType[] marketModels = {// CalibratedMM,
                MarketModelType.ExponentialCorrelationFlatVolatility,
                MarketModelType.ExponentialCorrelationAbcdVolatility};

        for (MarketModelType j : marketModels) {

            int[] testedFactors = { /*4, 8,*/ todaysForwards.size()};
            for (int factors : testedFactors) {
                // Composite's ProductSuggested is the Terminal one
                MeasureType[] measures = { // ProductSuggested,
                        MeasureType.Terminal,
                        // MoneyMarketPlus,
                        MeasureType.MoneyMarket};
                for (MeasureType measure : measures) {
                    List<Integer> numeraires = makeMeasure(product, measure);

                    MarketModel marketModel = makeMarketModel(evolution, factors, j);

                    EvolverType[] evolvers = {EvolverType.Pc/*, Ipc*/};

                    MarketModelEvolver evolver;
                    int stop = EvolutionDescription.isInTerminalMeasure(
                            evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; i++) {
                        for (int n = 0; n < 1; n++) {
                            //MTBrownianGeneratorFactory generatorFactory(seed_);
                            SobolBrownianGeneratorFactory generatorFactory =
                                    new SobolBrownianGeneratorFactory(
                                            SobolBrownianGeneratorBase.Ordering.Diagonal, seed_);
                            evolver = makeMarketModelEvolver(marketModel,
                                    numeraires,
                                    generatorFactory,
                                    evolvers[i]);
                            String config = marketModelTypeToString(j) + ", " + factors
                                    + (factors > 1
                                    ? (factors == todaysForwards.size()
                                    ? " (full) factors, "
                                    : " factors, ")
                                    : " factor,")
                                    + measureTypeToString(measure) + ", "
                                    + evolverTypeToString(evolvers[i]) + ", "
                                    + "MT BGF";
                            if (printReport_)
                                System.out.println("    " + config);

                            SequenceStatisticsInc stats = simulate(evolver, product);
                            checkCMSAndSwaptions(stats, fixedRate,
                                    displacedPayoff, marketModel, config);

                        }
                    }
                }
            }
        }
    }

    // Official io::percent / io::rate formatting (both print value * 100 with
    // the stream's default fixed precision of 6 digits, followed by " %").
    private static String rate(double value) {
        return String.format(Locale.ROOT, "%.6f %%", value * 100.0);
    }

    private static String percent(double value) {
        return rate(value);
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

    private static List<StrikedTypePayoff> filledPayoffs(int n, double strike) {
        List<StrikedTypePayoff> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(new PlainVanillaPayoff(Option.Type.Call, strike));
        return result;
    }
}
