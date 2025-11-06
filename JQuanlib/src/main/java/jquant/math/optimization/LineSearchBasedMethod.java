package jquant.math.optimization;

public class LineSearchBasedMethod extends OptimizationMethod {
    protected LineSearch lineSearch_;
    //default ArmijoLineSearch
    public LineSearchBasedMethod(LineSearch lineSearch) {
        lineSearch_ = lineSearch;
    }
    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria endCriteria) {
        return null;
    }
}
