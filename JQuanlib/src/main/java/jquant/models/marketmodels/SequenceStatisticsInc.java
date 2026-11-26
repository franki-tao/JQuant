package jquant.models.marketmodels;

import jquant.math.statistics.GenericSequenceStatistics;
import jquant.math.statistics.IncrementalStatistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code SequenceStatisticsInc} typedef
 * ({@code GenericSequenceStatistics<IncrementalStatistics>}).
 *
 * <p>The official C++ code copies the returned {@code std::vector}s by value;
 * Java lists are returned as fresh copies here so that a subsequent call
 * (e.g. {@code errorEstimate()} after {@code mean()}) does not overwrite a
 * previously retrieved result.
 */
public class SequenceStatisticsInc extends GenericSequenceStatistics {

    public SequenceStatisticsInc(int dimension) {
        super(dimension, new IncrementalStatistics());
    }

    @Override
    public List<Double> mean() {
        return new ArrayList<>(super.mean());
    }

    @Override
    public List<Double> errorEstimate() {
        return new ArrayList<>(super.errorEstimate());
    }
}
