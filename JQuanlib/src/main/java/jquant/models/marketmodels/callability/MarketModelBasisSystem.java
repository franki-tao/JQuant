package jquant.models.marketmodels.callability;

import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/marketmodelbasissystem.hpp} port.
 *
 * <p>Official {@code clone()} is expressed in Java as {@link #copy()}.
 */
public abstract class MarketModelBasisSystem implements MarketModelNodeDataProvider {

    // possibly different for each exercise
    public abstract List<Integer> numberOfFunctions();

    @Override
    public List<Integer> numberOfData() {
        return numberOfFunctions();
    }

    public abstract MarketModelBasisSystem copy();
}
