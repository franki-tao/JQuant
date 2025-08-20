package math.optimization;

public abstract class OptimizationMethod {
    public abstract EndCriteria.Type minimize(Problem P, EndCriteria endCriteria);
}
