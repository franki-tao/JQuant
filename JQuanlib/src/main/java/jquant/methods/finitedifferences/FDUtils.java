package jquant.methods.finitedifferences;

public class FDUtils {
    public static TridiagonalOperator positive(final  TridiagonalOperator D) {
        return new TridiagonalOperator(D.lowerDiagonal_, D.diagonal_, D.upperDiagonal_);
    }

    public static TridiagonalOperator negative(final TridiagonalOperator D) {
        return new TridiagonalOperator(
                D.lowerDiagonal_.mutiply(-1),
                D.diagonal_.mutiply(-1),
                D.upperDiagonal_.mutiply(-1));
    }
    public static TridiagonalOperator add(final TridiagonalOperator D1, final TridiagonalOperator D2) {
        return new TridiagonalOperator(
                D1.lowerDiagonal_.add(D2.lowerDiagonal_),
                D1.diagonal_.add(D2.diagonal_),
                D1.upperDiagonal_.add(D2.upperDiagonal_)
        );
    }
    public static TridiagonalOperator subtract(final TridiagonalOperator D1, final TridiagonalOperator D2) {
        return new TridiagonalOperator(
                D1.lowerDiagonal_.subtract(D2.lowerDiagonal_),
                D1.diagonal_.subtract(D2.diagonal_),
                D1.upperDiagonal_.subtract(D2.upperDiagonal_)
        );
    }
    public static TridiagonalOperator multiply(final TridiagonalOperator D, double x) {
        return new TridiagonalOperator(
                D.lowerDiagonal_.mutiply(x),
                D.diagonal_.mutiply(x),
                D.upperDiagonal_.mutiply(x)
        );
    }
    public static TridiagonalOperator multiply(double x, final TridiagonalOperator D) {
        return multiply(D, x);
    }
    public static TridiagonalOperator divide(final TridiagonalOperator D, double x) {
        return new TridiagonalOperator(
                D.lowerDiagonal_.div(x),
                D.diagonal_.div(x),
                D.upperDiagonal_.div(x)
        );
    }
}
