package jquant.methods.finitedifferences.solvers;

import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * Description of a finite-difference solver.
 *
 * <p>Port of {@code ql/methods/finitedifferences/solvers/fdmsolverdesc.hpp}.
 */
public class FdmSolverDesc {
    public final FdmMesher mesher;
    public final FdmBoundaryConditionSet bcSet;
    public final FdmStepConditionComposite condition;
    public final FdmInnerValueCalculator calculator;
    public final double maturity;
    public final int timeSteps;
    public final int dampingSteps;

    public FdmSolverDesc(FdmMesher mesher,
                         FdmBoundaryConditionSet bcSet,
                         FdmStepConditionComposite condition,
                         FdmInnerValueCalculator calculator,
                         double maturity,
                         int timeSteps,
                         int dampingSteps) {
        this.mesher = mesher;
        this.bcSet = bcSet;
        this.condition = condition;
        this.calculator = calculator;
        this.maturity = maturity;
        this.timeSteps = timeSteps;
        this.dampingSteps = dampingSteps;
    }
}
