package jquant.models.marketmodels;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/utilities.hpp/.cpp} port.
 */
public final class Utilities {

    private Utilities() {
    }

    public static void mergeTimes(final List<List<Double>> times,
                                  List<Double> mergedTimes,
                                  List<boolean[]> isPresent) {
        List<Double> allTimes = new ArrayList<>();
        for (List<Double> time : times)
            allTimes.addAll(time);

        // ...sort and compact the vector mergedTimes...
        allTimes.sort(Double::compare);
        // The official code sizes isPresent with allTimes.size() (the
        // pre-unique size); keep a separate count to reproduce that.
        int allTimesSize = allTimes.size();
        List<Double> uniqueTimes = new ArrayList<>();
        for (double t : allTimes) {
            if (uniqueTimes.isEmpty() || t != uniqueTimes.get(uniqueTimes.size() - 1))
                uniqueTimes.add(t);
        }
        mergedTimes.addAll(uniqueTimes);

        isPresent.clear();
        for (int i = 0; i < times.size(); i++) {
            boolean[] present = new boolean[allTimesSize];
            for (int j = 0; j < allTimesSize; j++) {
                present[j] = binarySearch(times.get(i), allTimes.get(j));
            }
            isPresent.add(present);
        }
    }

    public static boolean[] isInSubset(final List<Double> set,
                                       final List<Double> subset) {
        boolean[] result = new boolean[set.size()];
        int dimsubSet = subset.size();
        if (dimsubSet == 0)
            return result;
        int dimSet = set.size();

        QL_REQUIRE(dimSet >= dimsubSet,
                "set is required to be larger or equal than subset");

        for (int i = 0; i < dimSet; ++i) {  // loop in set
            int j = 0;
            double setElement = set.get(i);
            for (; ; ) {              // loop in subset
                double subsetElement = subset.get(j);
                result[i] = false;
                // if smaller no hope, leave false and go to next i
                if (setElement < subsetElement)
                    break;
                // if match, set result[i] to true and go to next i
                if (setElement == subsetElement) {
                    result[i] = true;
                    break;
                }
                // if larger, leave false if at the end or go to next j
                if (j == dimsubSet - 1)
                    break;
                ++j;
            }
        }
        return result;
    }

    /** check for strictly increasing times, first time greater than zero */
    public static void checkIncreasingTimes(final List<Double> times) {
        int nTimes = times.size();
        QL_REQUIRE(nTimes > 0,
                "at least one time is required");
        QL_REQUIRE(times.get(0) > 0.0,
                "first time (" + times.get(0) +
                        ") must be greater than zero");
        for (int i = 0; i < nTimes - 1; ++i)
            QL_REQUIRE(times.get(i + 1) - times.get(i) > 0,
                    "non increasing rate times: " +
                            "times[" + i + "]=" + times.get(i) + ", " +
                            "times[" + (i + 1) + "]=" + times.get(i + 1));
    }

    public static void checkIncreasingTimesAndCalculateTaus(final List<Double> times,
                                                            List<Double> taus) {
        int nTimes = times.size();
        QL_REQUIRE(nTimes > 1,
                "at least two times are required, " + nTimes + " provided");
        QL_REQUIRE(times.get(0) > 0.0,
                "first time (" + times.get(0) +
                        ") must be greater than zero");
        taus.clear();
        for (int i = 0; i < nTimes - 1; ++i) {
            double tau = times.get(i + 1) - times.get(i);
            taus.add(tau);
            QL_REQUIRE(tau > 0,
                    "non increasing rate times: " +
                            "times[" + i + "]=" + times.get(i) + ", " +
                            "times[" + (i + 1) + "]=" + times.get(i + 1));
        }
    }

    private static boolean binarySearch(List<Double> sorted, double value) {
        int low = 0;
        int high = sorted.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            double midVal = sorted.get(mid);
            if (midVal < value)
                low = mid + 1;
            else if (midVal > value)
                high = mid - 1;
            else
                return true;
        }
        return false;
    }

    /** Official {@code io::ordinal} formatting, used in checkCompatibility. */
    public static String ordinal(int n) {
        String suffix;
        if (n == 11 || n == 12 || n == 13) {
            suffix = "th";
        } else {
            switch (n % 10) {
                case 1:
                    suffix = "st";
                    break;
                case 2:
                    suffix = "nd";
                    break;
                case 3:
                    suffix = "rd";
                    break;
                default:
                    suffix = "th";
            }
        }
        return n + suffix;
    }
}
