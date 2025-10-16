package jquant.math.interpolations.impl;

public class BilinearInterpolationImpl extends Interpolation2DTemplateImpl {

    public BilinearInterpolationImpl(double[] x, double[] y, double[][] z) {
        super(x, y, z);
        calculate();
    }

    @Override
    public void calculate() {

    }

    @Override
    public double value(double x, double y) {
        int i = super.locateX(x), j = super.locateY(y);

        double z1 = super.zData_[j][i];
        double z2 = super.zData_[j][i + 1];
        double z3 = super.zData_[j + 1][i];
        double z4 = super.zData_[j + 1][i + 1];

        double t = (x - super.xValues[i]) / (super.xValues[i + 1] - super.xValues[i]);
        double u = (y - super.yValues[j]) / (super.yValues[j + 1] - super.yValues[j]);

        return (1.0 - t) * (1.0 - u) * z1 + t * (1.0 - u) * z2
                + (1.0 - t) * u * z3 + t * u * z4;
    }
}
