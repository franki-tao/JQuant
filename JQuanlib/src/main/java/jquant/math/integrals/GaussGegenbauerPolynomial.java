package jquant.math.integrals;

public class GaussGegenbauerPolynomial extends GaussJacobiPolynomial{
    public GaussGegenbauerPolynomial(double lambda) {
        super(lambda-0.5, lambda-0.5);
    }
}
