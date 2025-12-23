package time;

import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.math.MathUtils;
import jquant.math.ReferencePkg;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.randomnumbers.InverseCumulativeRng;
import jquant.math.randomnumbers.MersenneTwisterUniformRng;
import jquant.math.statistics.*;
import jquant.math.statistics.impl.Stat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatsTest {
    public double[] data = { 3.0, 4.0, 5.0, 2.0, 3.0, 4.0, 5.0, 6.0, 4.0, 7.0 };
    public double[] weights = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 };

    public void check(String name, Stat s) {
        for (int i=0; i<data.length; i++)
            s.add(data[i],weights[i]);

        double calculated, expected;
        double tolerance;
        assertFalse(s.samples() != data.length, name + ": wrong number of samples\n"
                + "    calculated: " + s.samples() + "\n"
                + "    expected:   " + data.length);
        expected = 10.0;
        calculated = s.weightSum();
        assertFalse(calculated != expected,name+": wrong sum of weights\n"
                +"    calculated: "+calculated+"\n"
                +"    expected:   "+expected);

        expected = 2.0;
        calculated = s.min();
        assertFalse(calculated != expected, name+": wrong minimum value\n"
                +"    calculated: "+calculated+"\n"
                +"    expected:   "+expected);

        expected = 7.0;
        calculated = s.max();
        assertFalse(calculated != expected, name+": wrong maximum value\n"
                +"    calculated: "+calculated+"\n"
                +"    expected:   "+expected);

        expected = 4.3;
        tolerance = 1.0e-9;
        calculated = s.mean();
        assertFalse(Math.abs(calculated-expected) > tolerance, name+": wrong mean value\n"
                +"    calculated: "+calculated+"\n"
                +"    expected:   "+expected);

        expected = 2.23333333333;
        calculated = s.variance();
        assertFalse(Math.abs(calculated-expected) > tolerance, name+": wrong variance\n"
                +"    calculated: "+calculated+"\n"
                +"    expected:   "+expected);

        expected = 1.4944341181;
        calculated = s.standardDeviation();
        assertFalse(Math.abs(calculated-expected) > tolerance, name+": wrong standard deviation\n"
                +"    calculated: "+calculated+"\n"
                +"    expected:   "+expected);

        expected = 0.359543071407;
        calculated = s.skewness();
        assertFalse(Math.abs(calculated-expected) > tolerance, name+": wrong skewness\n"
                +"    calculated: "+calculated+"\n"
                +"    expected:   "+expected);

        expected = -0.151799637209;
        calculated = s.kurtosis();
        assertFalse(Math.abs(calculated-expected) > tolerance, name+": wrong kurtosis\n"
                +"    calculated: "+calculated+"\n"
                +"    expected:   "+expected);
    }

    public void checkSequence(String name, int dimension, Stat s) {
        GenericSequenceStatistics ss = new GenericSequenceStatistics(dimension, s);
        int i;
        for (i = 0; i<data.length; i++) {
            List<Double> temp = CommonUtil.ArrayInit(dimension, data[i]);
            ss.add(temp, weights[i]);
        }

        List<Double> calculated = new ArrayList<>();
        double expected, tolerance;
        
        assertFalse(ss.samples() != data.length, "SequenceStatistics<"+name+">: "
                +"wrong number of samples\n"
                +"    calculated: "+ss.samples()+"\n"
                +"    expected:   "+data.length);

        expected = 10.0;
        assertFalse(ss.weightSum() != expected, "SequenceStatistics<"+name+">: "
                +"wrong sum of weights\n"
                +"    calculated: "+ss.weightSum()+"\n"
                +"    expected:   "+expected);

        expected = 2.0;
        calculated = ss.min();
        for (i=0; i<dimension; i++) {
            assertFalse(calculated.get(i) != expected);
        }

        expected = 7.0;
        calculated = ss.max();
        for (i=0; i<dimension; i++) {
            assertFalse(calculated.get(i) != expected);
        }

        expected = 4.3;
        tolerance = 1.0e-9;
        calculated = ss.mean();
        for (i=0; i<dimension; i++) {
            assertFalse(Math.abs(calculated.get(i)-expected) > tolerance);
        }

        expected = 2.23333333333;
        calculated = ss.variance();
        for (i=0; i<dimension; i++) {
            assertFalse(Math.abs(calculated.get(i)-expected) > tolerance);
        }

        expected = 1.4944341181;
        calculated = ss.standardDeviation();
        for (i=0; i<dimension; i++) {
            assertFalse(Math.abs(calculated.get(i)-expected) > tolerance);
        }

        expected = 0.359543071407;
        calculated = ss.skewness();
        for (i=0; i<dimension; i++) {
            assertFalse(Math.abs(calculated.get(i)-expected) > tolerance);
        }

        expected = -0.151799637209;
        calculated = ss.kurtosis();
        for (i=0; i<dimension; i++) {
            assertFalse(Math.abs(calculated.get(i)-expected) > tolerance);
        }
    }

    public void checkConvergence(String name, Stat s) {
        ConvergenceStatistics stats = new ConvergenceStatistics(s);
        stats.add(new ReferencePkg<>(1d), 1d);
        stats.add(new ReferencePkg<>(2d), 1d);
        stats.add(new ReferencePkg<>(3d), 1d);
        stats.add(new ReferencePkg<>(4d), 1d);
        stats.add(new ReferencePkg<>(5d), 1d);
        stats.add(new ReferencePkg<>(6d), 1d);
        stats.add(new ReferencePkg<>(7d), 1d);
        stats.add(new ReferencePkg<>(8d), 1d);

        int expectedSize1 = 3;
        int calculatedSize = stats.convergenceTable().size();
        assertFalse(calculatedSize != expectedSize1);


        double expectedValue1 = 4.0;
        double tolerance = 1.0e-9;
        double calculatedValue = stats.convergenceTable().get(stats.convergenceTable().size()-1).getSecond();
        assertFalse(Math.abs(calculatedValue-expectedValue1) > tolerance);


        int expectedSampleSize1 = 7;
        int calculatedSamples = stats.convergenceTable().get(stats.convergenceTable().size()-1).getFirst();
        assertFalse(calculatedSamples != expectedSampleSize1);

        stats.reset();
        stats.add(new ReferencePkg<>(1d), 1d);
        stats.add(new ReferencePkg<>(2d), 1d);
        stats.add(new ReferencePkg<>(3d), 1d);
        stats.add(new ReferencePkg<>(4d), 1d);

        int expectedSize2 = 2;
        calculatedSize = stats.convergenceTable().size();
        assertFalse(calculatedSize != expectedSize2);

        double expectedValue2 = 2.0;
        calculatedValue = stats.convergenceTable().get(stats.convergenceTable().size()-1).getSecond();
        assertFalse(Math.abs(calculatedValue-expectedValue2) > tolerance);

        int expectedSampleSize2 = 3;
        calculatedSamples = stats.convergenceTable().get(stats.convergenceTable().size()-1).getFirst();
        assertFalse(calculatedSamples != expectedSampleSize2);
    }

    public void test_inc_stat(double expr, double expected) {
        assertTrue(MathUtils.close_enough(expr, expected));
    }

    @Test
    public void testStatistics() {
        System.out.println("Testing statistics...");
        check("IncrementalStatistics", new IncrementalStatistics());
        GenericGaussianStatistics statistics = new GenericGaussianStatistics(new GeneralStatistics());
        check("Statistics", new GenericRiskStatistics(statistics));
    }

    @Test
    public void testSequenceStatistics() {
        System.out.println("Testing sequence statistics...");
        checkSequence("IncrementalStatistics", 5, new IncrementalStatistics());
        GeneralStatistics statistics = new GenericGaussianStatistics(new GeneralStatistics());
        checkSequence("Statistics", 5, new GenericRiskStatistics(statistics));
    }

    @Test
    public void testConvergenceStatistics() {
        System.out.println("Testing convergence statistics...");
        checkConvergence("IncrementalStatistics", new IncrementalStatistics());
        GenericGaussianStatistics statistics = new GenericGaussianStatistics(new GeneralStatistics());
        checkConvergence("Statistics", new GenericRiskStatistics(statistics));
    }

    @Test
    public void testIncrementalStatistics() {
        System.out.println("Testing incremental statistics...");
        MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(42);
        IncrementalStatistics stat = new IncrementalStatistics();
        for (int i = 0; i < 500000; ++i) {
            double x = 2.0 * (mt.nextReal() - 0.5) * 1234.0;
            double w = mt.nextReal();
            stat.add(x, w);
        }
        assertFalse(stat.samples() != 500000);
        test_inc_stat(stat.weightSum(), 2.5003623600676749e+05);
        test_inc_stat(stat.mean(), 4.9122325964293845e-01);
        test_inc_stat(stat.variance(), 5.0706503959683329e+05);
        test_inc_stat(stat.standardDeviation(), 7.1208499464378076e+02);
        test_inc_stat(stat.errorEstimate(), 1.0070402569876076e+00);
        test_inc_stat(stat.skewness(), -1.7360169326722712e-03);
        test_inc_stat(stat.kurtosis(), -1.1990742562086147e+00);
        test_inc_stat(stat.min(), -1.2339945045639761e+03);
        test_inc_stat(stat.max(), 1.2339958308008499e+03);
        test_inc_stat(stat.downsideVariance(), 5.0786776146975247e+05);
        test_inc_stat(stat.downsideDeviation(), 7.1264841364431061e+02);

        InverseCumulativeNormal normal = new InverseCumulativeNormal();
        Function f = new Function() {
            @Override
            public double value(double x) {
                return normal.value(x);
            }
        };
        InverseCumulativeRng normal_gen = new InverseCumulativeRng(new MersenneTwisterUniformRng(0), f);

        IncrementalStatistics stat2  = new IncrementalStatistics();

        for (int i = 0; i < 500000; ++i) {
            double x = normal_gen.next().value * 1E-1 + 1E8;
            double w = 1.0;
            stat2.add(x, w);
        }

        double tol = 1E-3;
        assertFalse(Math.abs(stat2.variance() - 1e-2) > tol);
    }

}
