package jquant.methods.montecarlo;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/methods/montecarlo/nodedata.hpp} port.
 */
public class NodeData {

    public double exerciseValue;
    public double cumulatedCashFlows;
    public List<Double> values = new ArrayList<>();
    public double controlValue;
    public boolean isValid;
}
