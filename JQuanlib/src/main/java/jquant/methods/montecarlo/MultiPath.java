package jquant.methods.montecarlo;

import jquant.TimeGrid;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Correlated multiple asset paths.
 *
 * <p>Port of QuantLib {@code ql/methods/montecarlo/multipath.hpp}.
 * {@code multiPath.get(j)} is the path followed by the j-th asset.
 */
public class MultiPath {
    private final List<Path> multiPath_;

    public MultiPath() {
        multiPath_ = new ArrayList<>();
    }

    public MultiPath(int nAsset, TimeGrid timeGrid) {
        QL_REQUIRE(nAsset > 0, "number of asset must be positive");
        multiPath_ = new ArrayList<>(nAsset);
        for (int i = 0; i < nAsset; i++) {
            multiPath_.add(new Path(timeGrid));
        }
    }

    public MultiPath(List<Path> multiPath) {
        multiPath_ = new ArrayList<>(multiPath);
    }

    public int assetNumber() {
        return multiPath_.size();
    }

    public int pathSize() {
        return multiPath_.get(0).length();
    }

    public Path get(int j) {
        return multiPath_.get(j);
    }

    public Path at(int j) {
        return multiPath_.get(j);
    }

    public void set(int j, Path path) {
        multiPath_.set(j, path);
    }
}
