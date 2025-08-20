package math.optimization;

public class EndCriteria {
    public enum Type {None,
        MaxIterations,
        StationaryPoint,
        StationaryFunctionValue,
        StationaryFunctionAccuracy,
        ZeroGradientNorm,
        FunctionEpsilonTooSmall,
        Unknown};
}
