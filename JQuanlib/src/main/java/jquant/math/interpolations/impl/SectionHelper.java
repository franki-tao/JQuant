package jquant.math.interpolations.impl;

public interface SectionHelper {
    double value(double x);
    double primitive(double x);

    double fNext();
}
