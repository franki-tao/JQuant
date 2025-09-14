package jquant.math.interpolations.impl;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class ComboHelper implements SectionHelper {
    private double quadraticity_;

    private SectionHelper quadraticHelper_;

    private SectionHelper convMonoHelper_;

    public ComboHelper(SectionHelper quadraticHelper,
                       SectionHelper convMonoHelper,
                       double quadraticity) {
        quadraticity_ = quadraticity;
        quadraticHelper_ = quadraticHelper;
        convMonoHelper_ = convMonoHelper;
        QL_REQUIRE(quadraticity < 1.0 && quadraticity > 0.0,
                "Quadratic value must lie between 0 and 1");
    }

    @Override
    public double value(double x) {
        return (quadraticity_ * quadraticHelper_.value(x) + (1.0 - quadraticity_) * convMonoHelper_.value(x));
    }

    @Override
    public double primitive(double x) {
        return (quadraticity_ * quadraticHelper_.primitive(x) + (1.0 - quadraticity_) * convMonoHelper_.primitive(x));
    }

    @Override
    public double fNext() {
        return (quadraticity_ * quadraticHelper_.fNext() + (1.0 - quadraticity_) * convMonoHelper_.fNext());
    }
}
