package math.integrals;

import math.Array;
import utilities.SharePtr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static math.CommonUtil.ArrayInit;

public class MultiDimGaussianIntegration {
    private Array weights_;
    private List<Array> x_;

    public MultiDimGaussianIntegration(List<Integer> ns, SharePtr<GaussianQuadrature, Integer> genQuad) {
        int si = 1;
        for (int n : ns) {
            si *= n;
        }
        weights_ = new Array(si, 1d);
        x_ = new ArrayList<>();
        for (int i = 0; i < weights_.size(); i++) {
            x_.add(new Array(ns.size()));
        }

        final int m = ns.size();
        final int n = x_.size();

        List<Integer> spacing = ArrayInit(m);
        spacing.set(0, 1);
        int temp = 1;
        for (int i = 0; i < ns.size() - 1; i++) {
            temp *= ns.get(i);
            spacing.set(i + 1, temp);
        }
        Map<Integer, Array> n2weights = new HashMap<>();
        Map<Integer, Array> n2x = new HashMap<>();
        for (int order : ns) {
            if (!n2x.containsKey(order)) {
                GaussianQuadrature quad = genQuad.value(order);
                n2x.put(order, quad.x());
                n2weights.put(order, quad.weights());
            }
        }

        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < m; ++j) {
                final int order = ns.get(j);
                final int nx = (i / spacing.get(j)) % ns.get(j);
                weights_.set(i, weights_.get(i) * n2weights.get(order).get(nx));
                x_.get(i).set(j, n2x.get(order).get(nx));
            }
        }
    }

    public double value(SharePtr<Double, Array> f) {
        double s = 0.0;
        final int n = x_.size();
        for (int i = 0; i < n; ++i) {
            s += weights_.get(i) * f.value(x_.get(i));
        }
        return s;
    }

    public static void main(String[] args) {
        List<Array> tt = new ArrayList<>();
        Array a = new Array(5, 2);
        tt.add(a);
        tt.get(0).set(1, 100);
        System.out.println(tt.get(0).get(1));
    }
}
