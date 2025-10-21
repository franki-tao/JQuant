package jquant.math.ode;

import java.util.List;

public interface OdeFct {
    List<Double> value(double x, List<Double> v);
}
