package jquant;

import jquant.math.Array;

import java.util.ArrayList;
import java.util.List;

//! Useful discretized discount bond asset
public abstract class DiscretizedDiscountBond extends DiscretizedAsset {
    @Override
    public void reset(int size) {
        values_ = new Array(size, 1.0);
    }

    @Override
    public List<Double> mandatoryTimes() {
        return new ArrayList<>();
    }
}
