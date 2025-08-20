package math.optimization;

import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.NULL_REAL;
import static math.MathUtils.NULL_SIZE;
import static math.optimization.EndCriteria.Type.*;

public class EndCriteria {
    public enum Type {
        None,
        MaxIterations,
        StationaryPoint,
        StationaryFunctionValue,
        StationaryFunctionAccuracy,
        ZeroGradientNorm,
        FunctionEpsilonTooSmall,
        Unknown
    }

    ;

    //! Maximum number of iterations
    protected int maxIterations_;

    //! Maximun number of iterations in stationary state
    protected int maxStationaryStateIterations_;
    //! root, function and gradient epsilons
    protected double rootEpsilon_, functionEpsilon_, gradientNormEpsilon_;

    public EndCriteria(int maxIterations,
                       int maxStationaryStateIterations,
                       double rootEpsilon,
                       double functionEpsilon,
                       double gradientNormEpsilon) {
        maxIterations_ = maxIterations;
        maxStationaryStateIterations_ = maxStationaryStateIterations;
        rootEpsilon_ = rootEpsilon;
        functionEpsilon_ = functionEpsilon;
        gradientNormEpsilon_ = gradientNormEpsilon;
        if (maxStationaryStateIterations_ == NULL_SIZE) {
            maxStationaryStateIterations_ = Math.min((int) maxIterations / 2, 100);
        }
        QL_REQUIRE(maxStationaryStateIterations_ > 1,
                "maxStationaryStateIterations_ (" +
                        maxStationaryStateIterations_ +
                        ") must be greater than one");
        QL_REQUIRE(maxStationaryStateIterations_ < maxIterations_,
                "maxStationaryStateIterations_ (" +
                        maxStationaryStateIterations_ +
                        ") must be less than maxIterations_ (" +
                        maxIterations_ + ")");
        if (gradientNormEpsilon_ == NULL_REAL) {
            gradientNormEpsilon_ = functionEpsilon_;
        }
    }

    public int maxIterations() {
        return maxIterations_;
    }

    public int maxStationaryStateIterations() {
        return maxStationaryStateIterations_;
    }

    public double rootEpsilon() {
        return rootEpsilon_;
    }

    public double functionEpsilon() {
        return functionEpsilon_;
    }

    public double gradientNormEpsilon() {
        return gradientNormEpsilon_;
    }

    // !!调用此函数，注意内部变量值变动
    public boolean value(int iteration,
                         int statStateIterations,
                         boolean positiveOptimization,
                         double fold,
                         double fnew,
                         double normgnew,
                         Type ecType) {
        return checkMaxIterations(iteration, ecType) ||
                        checkStationaryFunctionValue(fold, fnew, statStateIterations, ecType) ||
                        checkStationaryFunctionAccuracy(fnew, positiveOptimization, ecType) ||
                        checkZeroGradientNorm(normgnew, ecType);
    }

    /*! Test if the number of iteration is below MaxIterations */
    // 实际中根据返回true false更改枚举， 下同
    public boolean checkMaxIterations(int iteration, Type ecType) {
        if (iteration < maxIterations_)
            return false;
        ecType = MaxIterations;
        return true;
    }

    public boolean checkStationaryPoint(double xOld,
                                        double xNew,
                                        int statStateIterations,
                                        Type ecType) {
        if (Math.abs(xNew - xOld) >= rootEpsilon_) {
            statStateIterations = 0;
            return false;
        }
        ++statStateIterations;
        if (statStateIterations <= maxStationaryStateIterations_)
            return false;
        ecType = StationaryPoint;
        return true;
    }


    public boolean checkStationaryFunctionValue(
            double fxOld,
            double fxNew,
            int statStateIterations,
            Type ecType) {
        if (Math.abs(fxNew - fxOld) >= functionEpsilon_) {
            statStateIterations = 0;
            return false;
        }
        ++statStateIterations;
        if (statStateIterations <= maxStationaryStateIterations_)
            return false;
        ecType = StationaryFunctionValue;
        return true;
    }

    public boolean checkStationaryFunctionAccuracy(
            double f,
            boolean positiveOptimization,
            Type ecType) {
        if (!positiveOptimization)
            return false;
        if (f >= functionEpsilon_)
            return false;
        ecType = StationaryFunctionAccuracy;
        return true;
    }

    //bool EndCriteria::checkZerGradientNormValue(
    //                                        const Real gNormOld,
    //                                        const Real gNormNew,
    //                                        EndCriteria::Type& ecType) const {
    //    if (std::fabs(gNormNew-gNormOld) >= gradientNormEpsilon_)
    //        return false;
    //    ecType = StationaryGradient;
    //    return true;
    //}

    public boolean checkZeroGradientNorm(double gradientNorm,
                                         Type ecType) {
        if (gradientNorm >= gradientNormEpsilon_)
            return false;
        ecType = ZeroGradientNorm;
        return true;
    }

    public static boolean succeeded(Type ecType) {
        return ecType == StationaryPoint ||
                ecType == StationaryFunctionValue ||
                ecType == StationaryFunctionAccuracy;
    }

}
