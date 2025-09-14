package jquant.math;

public class tanh_sinh {
    private int max_refinements;
    private double min_complement;

    private final int FUDGE1 = 160;
    private final int FUDGE2 = 16;

    public tanh_sinh(int max_refinements, double min_complement) {
        this.max_refinements = max_refinements;
        this.min_complement = min_complement;
    }

    public double integrate(Function f, double a, double b, int n, double eps, Double err) {
        final double tol = FUDGE1 * eps;
        double c = (a + b) / 2;
        double d = (b - a) / 2;
        double s = f.value(c);
        double v, h = 2;
        int k = 0;
        do {
            double q, t, eh, p = 0,  fp = 0, fm = 0;
            h /= 2;
            eh = Math.exp(h);
            t = eh;
            if (k > 0)
                eh *= eh;
            do {
                double u = Math.exp(1 / t - t);      // = exp(-2*sinh(j*h)) = 1/exp(sinh(j*h))^2
                double r = 2 * u / (1 + u);       // = 1 - tanh(sinh(j*h))
                double w = (t + 1 / t) * r / (1 + u); // = cosh(j*h)/cosh(sinh(j*h))^2
                double x = d * r;
                if (a + x > a) {              // if too close to a then reuse previous fp
                    double y = f.value(a + x);
                    fp = y;                 // if f(x) is finite, add to local sum
                }
                if (b - x < b) {              // if too close to b then reuse previous fm
                    double y = f.value(b - x);
                    fm = y;                 // if f(x) is finite, add to local sum
                }
                q = w * (fp + fm);
                p += q;
                t *= eh;
            } while (Math.abs(q) > eps * Math.abs(p));
            v = s - p;
            s += p;
            ++k;
        } while (Math.abs(v) > tol * Math.abs(s) && k <= n);
        // if the estimated relative error is desired, then return it
        if (err != null)
            err = Math.abs(v) / (FUDGE2 * Math.abs(s) + eps);
        // result with estimated relative error err
        return d * s * h;
    }

    public static void main(String[] args) {
        tanh_sinh tanhSinh = new tanh_sinh(1,1);
        Function f = new Function() {
            @Override
            public double value(double x) {
                return Math.sin(x);
            }
        };
        double v = tanhSinh.integrate(f, 0, Math.PI, 6, 1e-8, null);
        System.out.println(v);
    }
}
