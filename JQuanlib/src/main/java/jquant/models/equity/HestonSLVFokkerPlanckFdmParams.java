package jquant.models.equity;

import jquant.methods.finitedifferences.operators.FdmSquareRootFwdOp;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.utilities.FdmHestonGreensFct;

/**
 * Parameter set for the Heston SLV Fokker-Planck FDM calibration.
 *
 * <p>Port of the official {@code HestonSLVFokkerPlanckFdmParams} struct from
 * {@code ql/models/equity/hestonslvfdmmodel.hpp}. The field name
 * {@code predictionCorretionSteps} intentionally keeps the official typo.
 */
public class HestonSLVFokkerPlanckFdmParams {
    public int xGrid, vGrid;
    public int tMaxStepsPerYear, tMinStepsPerYear;
    public double tStepNumberDecay;

    // Rannacher smoothing steps at the beginning
    public int nRannacherTimeSteps;

    public int predictionCorretionSteps;

    // local volatility forward equation
    public double x0Density;
    public double localVolEpsProb;
    public int maxIntegrationIterations;

    // variance mesher definition
    public double vLowerEps, vUpperEps, vMin;
    public double v0Density, vLowerBoundDensity, vUpperBoundDensity;

    // do not calculate leverage function if prob is smaller than eps
    public double leverageFctPropEps;

    // algorithm to get to the start configuration at time point one
    public FdmHestonGreensFct.Algorithm greensAlgorithm;
    public FdmSquareRootFwdOp.TransformationType trafoType;

    // define finite difference scheme
    public FdmSchemeDesc schemeDesc;

    public HestonSLVFokkerPlanckFdmParams() {
    }

    public HestonSLVFokkerPlanckFdmParams(
            int xGrid, int vGrid,
            int tMaxStepsPerYear, int tMinStepsPerYear,
            double tStepNumberDecay,
            int nRannacherTimeSteps,
            int predictionCorretionSteps,
            double x0Density,
            double localVolEpsProb,
            int maxIntegrationIterations,
            double vLowerEps, double vUpperEps, double vMin,
            double v0Density, double vLowerBoundDensity, double vUpperBoundDensity,
            double leverageFctPropEps,
            FdmHestonGreensFct.Algorithm greensAlgorithm,
            FdmSquareRootFwdOp.TransformationType trafoType,
            FdmSchemeDesc schemeDesc) {
        this.xGrid = xGrid;
        this.vGrid = vGrid;
        this.tMaxStepsPerYear = tMaxStepsPerYear;
        this.tMinStepsPerYear = tMinStepsPerYear;
        this.tStepNumberDecay = tStepNumberDecay;
        this.nRannacherTimeSteps = nRannacherTimeSteps;
        this.predictionCorretionSteps = predictionCorretionSteps;
        this.x0Density = x0Density;
        this.localVolEpsProb = localVolEpsProb;
        this.maxIntegrationIterations = maxIntegrationIterations;
        this.vLowerEps = vLowerEps;
        this.vUpperEps = vUpperEps;
        this.vMin = vMin;
        this.v0Density = v0Density;
        this.vLowerBoundDensity = vLowerBoundDensity;
        this.vUpperBoundDensity = vUpperBoundDensity;
        this.leverageFctPropEps = leverageFctPropEps;
        this.greensAlgorithm = greensAlgorithm;
        this.trafoType = trafoType;
        this.schemeDesc = schemeDesc;
    }
}
