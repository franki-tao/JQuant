package math;

public abstract class IntegrationPolicyImpl {
    public abstract double integrate(Function f,double a,
                     double b,
                     double I,
                     int N);

    public abstract int nbEvalutions();
}
