package jquant.methods.finitedifferences.meshers;

import jquant.math.*;
import jquant.math.interpolations.LinearInterpolation;
import jquant.math.solvers1d.Brent;
import jquant.methods.finitedifferences.meshers.impl.OdeIntegrationFct;
import jquant.methods.finitedifferences.meshers.impl.Tuple;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.ArrayInit;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.*;

public class Concentrating1dMesher extends Fdm1dMesher {
    // requireCPoint = false
    public Concentrating1dMesher(double start, double end, int size, final Point<Double, Double> cPoints, final boolean requireCPoint) {
        super(size);
        QL_REQUIRE(end > start, "end must be larger than start");

        final double cPoint = cPoints.getFirst();
        final double density = Double.isNaN(cPoints.getSecond()) ? Double.NaN : cPoints.getSecond() * (end - start);

        QL_REQUIRE(Double.isNaN(cPoint) || (cPoint >= start && cPoint <= end), "cPoint must be between start and end");
        QL_REQUIRE(Double.isNaN(density) || density > 0.0, "density > 0 required");
        QL_REQUIRE(Double.isNaN(cPoint) || !Double.isNaN(density), "density must be given if cPoint is given");
        QL_REQUIRE(!requireCPoint || !Double.isNaN(cPoint), "cPoint is required in grid but not given");

        final double dx = 1.0 / (size - 1);

        if (!Double.isNaN(cPoint)) {
            List<Double> u = new ArrayList<>();
            List<Double> z = new ArrayList<>();
            Interpolation transform = new Interpolation() {
                @Override
                public boolean empty() {
                    return super.empty();
                }
            };
            final double c1 = FastMath.asinh((start - cPoint) / density);
            final double c2 = FastMath.asinh((end - cPoint) / density);
            if (requireCPoint) {
                u.add(0.0);
                z.add(0.0);
                if (!close(cPoint, start) && !close(cPoint, end)) {
                    final double z0 = -c1 / (c2 - c1);
                    final double u0 = Math.max(Math.min(Math.round(z0 * (size - 1)), size - 2), 1L) / ((double) (size - 1));
                    u.add(u0);
                    z.add(z0);
                }
                u.add(1.0);
                z.add(1.0);
                transform = new LinearInterpolation(CommonUtil.toArray(u), CommonUtil.toArray(z));
            }

            for (int i = 1; i < size - 1; ++i) {
                final double li = requireCPoint ? transform.value(i * dx, false) : i * dx;
                locations_.set(i, cPoint + density * Math.sinh(c1 * (1.0 - li) + c2 * li));
            }
        } else {
            for (int i = 1; i < size - 1; ++i) {
                locations_.set(i, start + i * dx * (end - start));
            }
        }

        locations_.set(0, start);
        locations_.set(size() - 1, end);

        for (int i = 0; i < size - 1; ++i) {
            double t = locations_.get(i + 1) - locations_.get(i);
            dplus_.set(i, t);
            dminus_.set(i + 1, t);
        }
        dplus_.set(size() - 1, Double.NaN);
        dminus_.set(0, Double.NaN);
    }

    public Concentrating1dMesher(double start, double end, int size, final List<Tuple> cPoints, double tol) {
        super(size);
        QL_REQUIRE(end > start, "end must be larger than start");

        List<Double> points = new ArrayList<>();
        List<Double> betas = new ArrayList<>();
        for (Tuple cPoint : cPoints) {
            points.add(cPoint.x);
            betas.add(squared(cPoint.y * (end - start)));
        }

        // get scaling factor a so that y(1) = end
        double aInit = 0.0;
        for (int i = 0; i < points.size(); ++i) {
            final double c1 = FastMath.asinh((start - points.get(i)) / betas.get(i));
            final double c2 = FastMath.asinh((end - points.get(i)) / betas.get(i));
            aInit += (c2 - c1) / points.size();
        }

        OdeIntegrationFct fct = new OdeIntegrationFct(points, betas, tol);
        final double a = new Brent().solve(x -> fct.solve(x, start, 0.0, 1.0) - end, tol, aInit, 0.1 * aInit);

        // solve ODE for all grid points
        Array x = new Array(size);
        Array y = new Array(size);
        x.set(0, 0.0);
        y.set(0, start);
        final double dx = 1.0 / (size - 1);
        for (int i = 1; i < size; ++i) {
            x.set(i, i * dx);
            y.set(i, fct.solve(a, y.get(i - 1), x.get(i - 1), x.get(i)));
        }

        // eliminate numerical noise and ensure y(1) = end
        final double dy = y.back() - end;
        for (int i = 1; i < size; ++i) {
            y.subtractEq(i, i * dx * dy);
        }

        LinearInterpolation odeSolution = new LinearInterpolation(x.toArray(), y.toArray());

        // ensure required points are part of the grid
        List<Point<Double, Double>> w = ArrayInit(1, new Point<>(0.0, 0.0));

        for (int i = 0; i < points.size(); ++i) {
            if ((cPoints.get(i).f) && points.get(i) > start && points.get(i) < end) {
                int j = Arrays.binarySearch(y.toArray(), points.get(i));
                // 关键：创建一个局部 final 变量
                final double targetPoint = points.get(i);
                final double e = new Brent().solve(x1 -> odeSolution.value(x1, true) - targetPoint, QL_EPSILON, x.get(j), 0.5 / size);
                w.add(new Point<>(Math.min(x.get(size - 2), x.get(j)), e));
            }
        }
        w.add(new Point<>(1d, 1d));
        Collections.sort(w);
        if (!w.isEmpty()) {
            int j = 0;
            for (int i = 1; i < w.size(); i++) {
                // 如果当前元素的 first 与上一个保留元素的 first 不同
                if (!w.get(i).getFirst().equals(w.get(j).getFirst())) {
                    j++;
                    w.set(j, w.get(i));
                }
            }
            // 移除末尾多余的元素（等同于 C++ 的 erase(new_end, end)）
            w.subList(j + 1, w.size()).clear();
        }

        List<Double> u = CommonUtil.ArrayInit(w.size());
        List<Double> z = CommonUtil.ArrayInit(w.size());
        for (int i = 0; i < w.size(); ++i) {
            u.set(i, w.get(i).getFirst());
            z.set(i, w.get(i).getSecond());
        }
        LinearInterpolation transform = new LinearInterpolation(CommonUtil.toArray(u), CommonUtil.toArray(z));

        for (int i = 0; i < size; ++i) {
            locations_.set(i, odeSolution.value(transform.value(i * dx, false), false));
        }

        for (int i = 0; i < size - 1; ++i) {
            double t = locations_.get(i + 1) - locations_.get(i);
            dplus_.set(i, t);
            dminus_.set(i + 1, t);
        }
        dplus_.set(size() - 1, Double.NaN);
        dminus_.set(0, Double.NaN);
    }
}
