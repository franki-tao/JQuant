package math;

import jquant.math.CommonUtil;
import jquant.math.ode.AdaptiveRungeKutta;
import jquant.math.ode.OdeFct;
import jquant.math.ode.OdeFct1d;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class OdeTest {
    @Test
    public void testAdaptiveRungeKutta() {
        System.out.println("Testing adaptive Runge Kutta...");
        AdaptiveRungeKutta rk_real = new AdaptiveRungeKutta(1E-12,1E-4,0.0);
        double tol1 = 5E-10, tol2 = 2E-12, tol3 = 2E-12, tol4 = 2E-12;
        OdeFct1d ode1_ = (x, t) -> t;
        double y10=1;
        OdeFct ode3_ = (x, v) -> {
            List<Double> r = CommonUtil.ArrayInit(2);
            r.set(0, v.get(1));
            r.set(1, -v.get(0));
            return r;
        };
        List<Double> y30 = Arrays.asList(0.0, 1.0);
        double x=0.0;
        double y1 = y10;
        List<Double> y3 = Arrays.asList(0.0, 1.0);
        while (x<5.0) {
            double exact1 = Math.exp(x);
            double exact3 = Math.sin(x);
            assertFalse(Math.abs( exact1 - y1 ) > tol1, "Error in ode #1: exact solution at x=" + x
                    + " is " + exact1
                    + ", numerical solution is " + y1
                    + " difference " + Math.abs(exact1-y1)
                    + " outside tolerance " + tol1);

            assertFalse(Math.abs(exact3 - y3.get(0)) > tol3, "Error in ode #3: exact solution at x=" + x
                    + " is " + exact3
                    + ", numerical solution is " + y3.get(0)
                    + " difference " + Math.abs(exact3-y3.get(0))
                    + " outside tolerance " + tol3);
            x+=0.01;
            y1=rk_real.value(ode1_,y10,0.0,x);
            y3=rk_real.value(ode3_,y30,0.0,x);
        }

    }
}
