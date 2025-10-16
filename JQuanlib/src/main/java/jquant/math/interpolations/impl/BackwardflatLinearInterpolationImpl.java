package jquant.math.interpolations.impl;

public class BackwardflatLinearInterpolationImpl extends Interpolation2DTemplateImpl {

    public BackwardflatLinearInterpolationImpl(double[] x, double[] y, double[][] z) {
        super(x, y, z);
        calculate();
    }

    @Override
    public void calculate() {

    }

    @Override
    public double value(double x, double y) {
        int j = super.locateY(y);
        double z1, z2;
        if (x <= super.xValues[0]) {
            z1 = super.zData_[j][0];
            z2 = super.zData_[j + 1][0];
        } else {
            int i = super.locateX(x);
            if (x == super.xValues[i]) {
                z1 = super.zData_[j][i];
                z2 = super.zData_[j + 1][i];
            } else {
                z1 = super.zData_[j][i + 1];
                z2 = super.zData_[j + 1][i + 1];
            }
        }

        double u = (y - super.yValues[j]) /
                (super.yValues[j + 1] - super.yValues[j]);

        return (1.0 - u) * z1 + u * z2;
    }
}
