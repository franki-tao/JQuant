package jquant.models.marketmodels;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/evolutiondescription.hpp/.cpp} port.
 */
public class EvolutionDescription {

    private int numberOfRates_;
    private List<Double> rateTimes_ = new ArrayList<>();
    private List<Double> evolutionTimes_ = new ArrayList<>();
    private List<int[]> relevanceRates_ = new ArrayList<>();
    // convenience variables
    private List<Double> rateTaus_ = new ArrayList<>();
    private List<Integer> firstAliveRate_ = new ArrayList<>();

    public EvolutionDescription() {
    }

    public EvolutionDescription(final List<Double> rateTimes) {
        this(rateTimes, new ArrayList<>(), new ArrayList<>());
    }

    public EvolutionDescription(final List<Double> rateTimes,
                                final List<Double> evolutionTimes) {
        this(rateTimes, evolutionTimes, new ArrayList<>());
    }

    public EvolutionDescription(final List<Double> rateTimes,
                                final List<Double> evolutionTimes,
                                final List<int[]> relevanceRates) {
        numberOfRates_ = rateTimes.isEmpty() ? 0 : rateTimes.size() - 1;
        rateTimes_ = new ArrayList<>(rateTimes);
        if (evolutionTimes.isEmpty() && !rateTimes.isEmpty())
            evolutionTimes_ = new ArrayList<>(rateTimes.subList(0, rateTimes.size() - 1));
        else
            evolutionTimes_ = new ArrayList<>(evolutionTimes);
        relevanceRates_ = new ArrayList<>(relevanceRates);
        rateTaus_ = new ArrayList<>();
        firstAliveRate_ = new ArrayList<>();

        Utilities.checkIncreasingTimesAndCalculateTaus(rateTimes_, rateTaus_);

        Utilities.checkIncreasingTimes(evolutionTimes_);
        int numberOfSteps = evolutionTimes_.size();

        QL_REQUIRE(evolutionTimes_.get(evolutionTimes_.size() - 1)
                        <= rateTimes.get(rateTimes.size() - 2),
                "The last evolution time (" + evolutionTimes_.get(evolutionTimes_.size() - 1) +
                        ") is past the last fixing time (" +
                        rateTimes.get(numberOfRates_ - 2) + ")");

        if (relevanceRates.isEmpty()) {
            relevanceRates_ = new ArrayList<>();
            for (int i = 0; i < numberOfSteps; ++i)
                relevanceRates_.add(new int[]{0, numberOfRates_});
        } else {
            QL_REQUIRE(relevanceRates.size() == numberOfSteps,
                    "relevanceRates / evolutionTimes mismatch");
        }

        double currentEvolutionTime = 0.0;
        int firstAliveRate = 0;
        for (int j = 0; j < numberOfSteps; ++j) {
            while (rateTimes_.get(firstAliveRate) <= currentEvolutionTime)
                ++firstAliveRate;
            firstAliveRate_.add(firstAliveRate);
            currentEvolutionTime = evolutionTimes_.get(j);
        }
    }

    public List<Double> rateTimes() {
        return rateTimes_;
    }

    public List<Double> rateTaus() {
        return rateTaus_;
    }

    public List<Double> evolutionTimes() {
        return evolutionTimes_;
    }

    public List<Integer> firstAliveRate() {
        return firstAliveRate_;
    }

    public List<int[]> relevanceRates() {
        return relevanceRates_;
    }

    public int numberOfRates() {
        return numberOfRates_;
    }

    public int numberOfSteps() {
        return evolutionTimes_.size();
    }

    // Numeraire functions

    /** Check that there is one numeraire for each evolution time. */
    public static void checkCompatibility(final EvolutionDescription evolution,
                                          final List<Integer> numeraires) {
        final List<Double> evolutionTimes = evolution.evolutionTimes();
        int n = evolutionTimes.size();
        QL_REQUIRE(numeraires.size() == n,
                "Size mismatch between numeraires (" + numeraires.size() +
                        ") and evolution times (" + n + ")");

        final List<Double> rateTimes = evolution.rateTimes();
        for (int i = 0; i < n - 1; i++)
            QL_REQUIRE(rateTimes.get(numeraires.get(i)) >= evolutionTimes.get(i),
                    Utilities.ordinal(i + 1) + " step, evolution time " +
                            evolutionTimes.get(i) + ": the numeraire (" + numeraires.get(i) +
                            "), corresponding to rate time " +
                            rateTimes.get(numeraires.get(i)) + ", is expired");
    }

    public static boolean isInTerminalMeasure(final EvolutionDescription evolution,
                                              final List<Integer> numeraires) {
        final List<Double> rateTimes = evolution.rateTimes();
        int min = numeraires.get(0);
        for (int n : numeraires)
            min = Math.min(min, n);
        return min == rateTimes.size() - 1;
    }

    public static boolean isInMoneyMarketPlusMeasure(final EvolutionDescription evolution,
                                                     final List<Integer> numeraires,
                                                     int offset) {
        boolean res = true;
        final List<Double> rateTimes = evolution.rateTimes();
        int maxNumeraire = rateTimes.size() - 1;
        QL_REQUIRE(offset <= maxNumeraire,
                "offset (" + offset +
                        ") is greater than the max allowed value for numeraire ("
                        + maxNumeraire + ")");
        final List<Double> evolutionTimes = evolution.evolutionTimes();
        for (int i = 0, j = 0; i < evolutionTimes.size(); ++i) {
            while (rateTimes.get(j) < evolutionTimes.get(i))
                j++;
            res = (numeraires.get(i) == Math.min(j + offset, maxNumeraire)) && res;
        }
        return res;
    }

    public static boolean isInMoneyMarketMeasure(final EvolutionDescription evolution,
                                                 final List<Integer> numeraires) {
        return isInMoneyMarketPlusMeasure(evolution, numeraires, 0);
    }

    /** Terminal measure: the last bond is used as numeraire. */
    public static List<Integer> terminalMeasure(final EvolutionDescription evolution) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < evolution.evolutionTimes().size(); ++i)
            result.add(evolution.rateTimes().size() - 1);
        return result;
    }

    /**
     * Offsetted discretely compounded money market account measure:
     * for each step the offset-th unexpired bond is used as numeraire.
     */
    public static List<Integer> moneyMarketPlusMeasure(final EvolutionDescription ev,
                                                       int offset) {
        final List<Double> rateTimes = ev.rateTimes();
        int maxNumeraire = rateTimes.size() - 1;
        QL_REQUIRE(offset <= maxNumeraire,
                "offset (" + offset +
                        ") is greater than the max allowed value for numeraire ("
                        + maxNumeraire + ")");

        final List<Double> evolutionTimes = ev.evolutionTimes();
        int n = evolutionTimes.size();
        List<Integer> numeraires = new ArrayList<>();
        for (int i = 0, j = 0; i < n; ++i) {
            while (rateTimes.get(j) < evolutionTimes.get(i))
                j++;
            numeraires.add(Math.min(j + offset, maxNumeraire));
        }
        return numeraires;
    }

    /** Discretely compounded money market account measure. */
    public static List<Integer> moneyMarketMeasure(final EvolutionDescription evol) {
        return moneyMarketPlusMeasure(evol, 0);
    }
}
