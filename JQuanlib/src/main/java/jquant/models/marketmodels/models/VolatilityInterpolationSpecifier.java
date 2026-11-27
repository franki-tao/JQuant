package jquant.models.marketmodels.models;

import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/volatilityinterpolationspecifier.hpp}
 * port (abstract header-only class).
 */
public abstract class VolatilityInterpolationSpecifier {

    public abstract void setScalingFactors(List<Double> scales);

    public abstract void setLastCapletVol(double vol);

    public abstract List<PiecewiseConstantVariance> interpolatedVariances();

    public abstract List<PiecewiseConstantVariance> originalVariances();

    public abstract int getPeriod();

    public abstract int getOffset();

    public abstract int getNoBigRates();

    public abstract int getNoSmallRates();
}
