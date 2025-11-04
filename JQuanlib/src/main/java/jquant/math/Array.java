package jquant.math;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class Array {
    public RealVector realVector;

    public Array(double[] a) {
        realVector = new ArrayRealVector(a);
    }

    public Array(RealVector v) {
        this.realVector = v;
    }

    public Array(Array a) {
        this.realVector = a.realVector;
    }

    public Array(int dim, double value) {
        realVector = new ArrayRealVector(dim, value);
    }

    public Array(int dim) {
        realVector = new ArrayRealVector(dim);
    }

    public int size() {
        return realVector.getDimension();
    }

    public void set(int index, double val) {
        realVector.setEntry(index, val);
    }

    public double get(int index) {
        QL_REQUIRE(index >= 0 && index < size(), "index out of bound!");
        return realVector.getEntry(index);
    }

    public void addEq(int i, double eps) {
        realVector.addToEntry(i, eps);
    }

    public void subtractEq(int i, double eps) {
        realVector.addToEntry(i, -eps);
    }

    public double max() {
        return realVector.getMaxValue();
    }

    public Array add(Array a) {
        Array result = new Array(this);
        result.realVector = result.realVector.add(a.realVector);
        return result;
    }

    public Array subtract(Array a) {
        Array result = new Array(this);
        result.realVector = result.realVector.subtract(a.realVector);
        return result;
    }

    public Array mutiply(double x) {
        Array result = new Array(this);
        result.realVector = result.realVector.mapMultiply(x);
        return result;
    }

    public List<Double> getList() {
        List<Double> res = new ArrayList<>();
        for (int i = 0; i < this.size(); i++) {
            res.add(this.get(i));
        }
        return res;
    }

    public void copy(double[] x, int start, int end) {
        for (int i = start; i < end; i++) {
            this.set(i, x[i]);
        }
    }

    public double[] toArray() {
        double[] res = new double[this.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = this.get(i);
        }
        return res;
    }

    public Array transform(Function f) {
        Array res = new Array(this);
        for (int i = 0; i < res.size(); i++) {
            res.set(i, f.value(res.get(i)));
        }
        return res;
    }

    public void swap(Array arr) {
        RealVector tp = this.realVector;
        this.realVector = arr.realVector;
        arr.realVector = tp;
    }

    public boolean empty() {
        return size() == 0;
    }

    public int maxIndex() {
        int maxIndex = 0;
        double maxVal = get(0);
        for (int i = 1; i < size(); i++) {
            if (this.get(i) > maxVal) {
                maxIndex = i;
                maxVal = this.get(i);
            }
        }
        return maxIndex;
    }
}
