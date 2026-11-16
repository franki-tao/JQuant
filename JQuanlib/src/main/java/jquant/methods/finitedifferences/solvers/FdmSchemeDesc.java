package jquant.methods.finitedifferences.solvers;

/**
 * Description of a finite-difference time-stepping scheme.
 *
 * <p>Port of {@code FdmSchemeDesc} from
 * {@code ql/methods/finitedifferences/solvers/fdmbackwardsolver.{hpp,cpp}}.
 */
public class FdmSchemeDesc {
    public enum FdmSchemeType {
        HundsdorferType,
        DouglasType,
        CraigSneydType,
        ModifiedCraigSneydType,
        ImplicitEulerType,
        ExplicitEulerType,
        MethodOfLinesType,
        TrBDF2Type,
        CrankNicolsonType
    }

    public final FdmSchemeType type;
    public final double theta;
    public final double mu;

    public FdmSchemeDesc(FdmSchemeType type, double theta, double mu) {
        this.type = type;
        this.theta = theta;
        this.mu = mu;
    }

    //! Same as Crank-Nicolson in one dimension.
    public static FdmSchemeDesc Douglas() {
        return new FdmSchemeDesc(FdmSchemeType.DouglasType, 0.5, 0.0);
    }

    public static FdmSchemeDesc CrankNicolson() {
        return new FdmSchemeDesc(FdmSchemeType.CrankNicolsonType, 0.5, 0.0);
    }

    public static FdmSchemeDesc CraigSneyd() {
        return new FdmSchemeDesc(FdmSchemeType.CraigSneydType, 0.5, 0.5);
    }

    public static FdmSchemeDesc ModifiedCraigSneyd() {
        return new FdmSchemeDesc(FdmSchemeType.ModifiedCraigSneydType,
                1.0 / 3.0, 1.0 / 3.0);
    }

    public static FdmSchemeDesc Hundsdorfer() {
        return new FdmSchemeDesc(FdmSchemeType.HundsdorferType,
                0.5 + Math.sqrt(3.0) / 6, 0.5);
    }

    public static FdmSchemeDesc ModifiedHundsdorfer() {
        return new FdmSchemeDesc(FdmSchemeType.HundsdorferType,
                1.0 - Math.sqrt(2.0) / 2, 0.5);
    }

    public static FdmSchemeDesc ExplicitEuler() {
        return new FdmSchemeDesc(FdmSchemeType.ExplicitEulerType, 0.0, 0.0);
    }

    public static FdmSchemeDesc ImplicitEuler() {
        return new FdmSchemeDesc(FdmSchemeType.ImplicitEulerType, 0.0, 0.0);
    }

    public static FdmSchemeDesc MethodOfLines() {
        return MethodOfLines(0.001, 0.01);
    }

    public static FdmSchemeDesc MethodOfLines(double eps, double relInitStepSize) {
        return new FdmSchemeDesc(FdmSchemeType.MethodOfLinesType, eps, relInitStepSize);
    }

    public static FdmSchemeDesc TrBDF2() {
        return new FdmSchemeDesc(FdmSchemeType.TrBDF2Type,
                2.0 - Math.sqrt(2.0), 1e-8);
    }
}
