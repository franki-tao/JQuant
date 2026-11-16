package jquant.math.interpolations;

/**
 * Factory for two-dimensional interpolations over a grid.
 *
 * <p>Port of the interpolator argument accepted by
 * {@code Interpolation2D::factory} implementations in QuantLib: the
 * interpolation is built from the x values, the y values and the z data.
 */
public interface Interpolator2D {
    Interpolation2D interpolate(double[] x, double[] y, double[][] z);
}
