package jquant.math.integrals;

import jquant.math.CommonUtil;
import jquant.math.Function;

import java.util.Arrays;
import java.util.List;

public class TabulatedGaussLegendre {

    private List<Double> x_;

    private List<Double> w_;

    private final List<Double> x6 = Arrays.asList(0.238619186083197, 0.661209386466265, 0.932469514203152);

    private final List<Double> w6 = Arrays.asList(0.467913934572691, 0.360761573048139, 0.171324492379170);

    private final int n6 = 3;

    private final List<Double> x7 = Arrays.asList(0.000000000000000,
            0.405845151377397,
            0.741531185599394,
            0.949107912342759);

    private final List<Double> w7 = Arrays.asList(0.417959183673469,
            0.381830050505119,
            0.279705391489277,
            0.129484966168870);

    private final int n7 = 4;

    private final List<Double> x12 = Arrays.asList(0.125233408511469,
            0.367831498998180,
            0.587317954286617,
            0.769902674194305,
            0.904117256370475,
            0.981560634246719);

    private final List<Double> w12 = Arrays.asList(0.249147045813403,
            0.233492536538355,
            0.203167426723066,
            0.160078328543346,
            0.106939325995318,
            0.047175336386512);

    private final int n12 = 6;

    private final List<Double> x20 = Arrays.asList(0.076526521133497,
            0.227785851141645,
            0.373706088715420,
            0.510867001950827,
            0.636053680726515,
            0.746331906460151,
            0.839116971822219,
            0.912234428251326,
            0.963971927277914,
            0.993128599185095);

    private final List<Double> w20 = Arrays.asList(0.152753387130726,
            0.149172986472604,
            0.142096109318382,
            0.131688638449177,
            0.118194531961518,
            0.101930119817240,
            0.083276741576704,
            0.062672048334109,
            0.040601429800387,
            0.017614007139152);

    private final int n20 = 10;


    private int n_;

    private int order_;

    public TabulatedGaussLegendre() {
        order(20);
    }

    public TabulatedGaussLegendre(int n) {
        order(n);
    }

    public void order(int order) {
        switch (order) {
            case (6):
                order_ = order;
                x_ = CommonUtil.clone(x6);
                w_ = CommonUtil.clone(w6);
                n_ = n6;
                break;
            case (7):
                order_ = order;
                x_ = CommonUtil.clone(x7);
                w_ = CommonUtil.clone(w7);
                n_ = n7;
                break;
            case (12):
                order_ = order;
                x_ = CommonUtil.clone(x12);
                w_ = CommonUtil.clone(w12);
                n_ = n12;
                break;
            case (20):
                order_ = order;
                x_ = CommonUtil.clone(x20);
                w_ = CommonUtil.clone(w20);
                n_ = n20;
                break;
            default:
                throw new IllegalArgumentException("not support order!");
        }
    }

    public int order() {
        return order_;
    }

    public double value(Function f) {
        if (w_ == null) {
            throw new IllegalArgumentException("Null weights");
        }
        if (x_ == null) {
            throw new IllegalArgumentException("Null abscissas");
        }
        int startIdx;
        double val;

        int isOrderOdd = order_ & 1;

        if (isOrderOdd != 0) {
            if (n_ <= 0) {
                throw new IllegalArgumentException("assume at least 1 point in quadrature");
            }
            val = w_.get(0) * f.value(x_.get(0));
            startIdx = 1;
        } else {
            val = 0.0;
            startIdx = 0;
        }

        for (int i = startIdx; i < n_; ++i) {
            val += w_.get(i) * f.value(x_.get(i));
            val += w_.get(i) * f.value(-x_.get(i));
        }
        return val;
    }
}
