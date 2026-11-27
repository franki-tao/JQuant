package jquant.models.marketmodels.models;

/**
 * Official QuantLib {@code ql/models/marketmodels/models/alphaform.hpp} port.
 */
public interface AlphaForm {

    double value(int i);

    void setAlpha(double alpha);
}
