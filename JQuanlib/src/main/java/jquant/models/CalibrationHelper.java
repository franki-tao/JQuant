package jquant.models;

//! abstract base class for calibration helpers
public interface CalibrationHelper {
    //! returns the error resulting from the model valuation
    double calibrationError();
}
