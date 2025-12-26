package jquant.methods.finitedifferences.meshers;

import jquant.math.CommonUtil;

import java.util.List;

public abstract class Fdm1dMesher {
    protected List<Double> locations_;
    protected List<Double> dplus_, dminus_;

    public Fdm1dMesher(int size) {
        locations_ = CommonUtil.ArrayInit(size);
        dplus_ = CommonUtil.ArrayInit(size);
        dminus_ = CommonUtil.ArrayInit(size);
    }

    public int size() {
        return locations_.size();
    }

    public double dplus(int index) {
        return dplus_.get(index);
    }

    public double dminus(int index) {
        return dminus_.get(index);
    }

    public double location(int index) {
        return locations_.get(index);
    }

    public List<Double> locations() {
        return locations_;
    }
}
