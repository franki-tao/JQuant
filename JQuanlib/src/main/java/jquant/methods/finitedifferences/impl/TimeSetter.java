package jquant.methods.finitedifferences.impl;


import jquant.methods.finitedifferences.TridiagonalOperator;

//! encapsulation of time-setting logic
public interface TimeSetter {
    void setTime(double t, TridiagonalOperator L);

}
