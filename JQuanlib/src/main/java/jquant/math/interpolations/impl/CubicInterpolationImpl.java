package jquant.math.interpolations.impl;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.math.interpolations.CubicInterpolation;
import jquant.math.templateImpl;
import jquant.methods.finitedifferences.TridiagonalOperator;

import java.util.List;

import static java.lang.Math.abs;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_REAL;

public class CubicInterpolationImpl extends templateImpl {
    public int n_;
    // P[i](x) = y[i] +
    //           a[i]*(x-x[i]) +
    //           b[i]*(x-x[i])^2 +
    //           c[i]*(x-x[i])^3
    public List<Double> primitiveConst_, a_, b_, c_;
    public List<Boolean> monotonicityAdjustments_;
    private CubicInterpolation.DerivativeApprox da_;
    private boolean monotonic_;
    private CubicInterpolation.BoundaryCondition leftType_, rightType_;
    private double leftValue_, rightValue_;
    private Array tmp_;
    private List<Double> dx_, S_;
    private TridiagonalOperator L_;

    private final double cubicInterpolatingPolynomialDerivative(
            double a, double b, double c, double d,
            double u, double v, double w, double z, double x) {
        return (-((((a - c) * (b - c) * (c - x) * z - (a - d) * (b - d) * (d - x) * w) * (a - x + b - x)
                + ((a - c) * (b - c) * z - (a - d) * (b - d) * w) * (a - x) * (b - x)) * (a - b) +
                ((a - c) * (a - d) * v - (b - c) * (b - d) * u) * (c - d) * (c - x) * (d - x)
                + ((a - c) * (a - d) * (a - x) * v - (b - c) * (b - d) * (b - x) * u)
                * (c - x + d - x) * (c - d))) /
                ((a - b) * (a - c) * (a - d) * (b - c) * (b - d) * (c - d));
    }

    public CubicInterpolationImpl(double[] x,
                                  double[] y,
                                  CubicInterpolation.DerivativeApprox da,
                                  boolean monotonic,
                                  CubicInterpolation.BoundaryCondition leftCondition,
                                  double leftConditionValue,
                                  CubicInterpolation.BoundaryCondition rightCondition,
                                  double rightConditionValue) {
        super(x, y, Cubic.requiredPoints);
        int n = x.length;
        n_ = (n);
        primitiveConst_ = CommonUtil.ArrayInit(n - 1);
        a_ = CommonUtil.ArrayInit(n - 1);
        b_ = CommonUtil.ArrayInit(n - 1);
        c_ = CommonUtil.ArrayInit(n - 1);
        monotonicityAdjustments_ = CommonUtil.ArrayInit(n);
        da_ = (da);
        monotonic_ = (monotonic);
        leftType_ = (leftCondition);
        rightType_ = (rightCondition);
        leftValue_ = (leftConditionValue);
        rightValue_ = (rightConditionValue);
        tmp_ = new Array(n_);
        dx_ = CommonUtil.ArrayInit(n_ - 1);
        S_ = CommonUtil.ArrayInit(n_ - 1);
        L_ = new TridiagonalOperator(n_);
        if (leftType_ == CubicInterpolation.BoundaryCondition.Lagrange
                || rightType_ == CubicInterpolation.BoundaryCondition.Lagrange) {
            QL_REQUIRE((xValue.length) >= 4,
                    "Lagrange boundary condition requires at least " +
                            "4 points (" + (xValue.length) + " are given)");
        }
    }

    @Override
    public void update() {
        for (int i = 0; i < n_ - 1; ++i) {
            dx_.set(i, xValue[i + 1] - xValue[i]);
            S_.set(i, (yValue[i + 1] - yValue[i]) / dx_.get(i));
        }

        // first derivative approximation
        if (da_ == CubicInterpolation.DerivativeApprox.Spline) {
            for (int i = 1; i < n_ - 1; ++i) {
                L_.setMidRow(i, dx_.get(i), 2.0 * (dx_.get(i) + dx_.get(i - 1)), dx_.get(i - 1));
                tmp_.set(i, 3.0 * (dx_.get(i) * S_.get(i - 1) + dx_.get(i - 1) * S_.get(i)));
            }

            // left boundary condition
            switch (leftType_) {
                case NotAKnot:
                    // ignoring end condition value
                    L_.setFirstRow(dx_.get(1) * (dx_.get(1) + dx_.get(0)),
                            (dx_.get(0) + dx_.get(1)) * (dx_.get(0) + dx_.get(1)));
                    tmp_.set(0, S_.get(0) * dx_.get(1) * (2.0 * dx_.get(1) + 3.0 * dx_.get(0)) +
                            S_.get(1) * dx_.get(0) * dx_.get(0));
                    break;
                case FirstDerivative:
                    L_.setFirstRow(1.0, 0.0);
                    tmp_.set(0, leftValue_);
                    break;
                case SecondDerivative:
                    L_.setFirstRow(2.0, 1.0);
                    tmp_.set(0, 3.0 * S_.get(0) - leftValue_ * dx_.get(0) / 2.0);
                    break;
                case Periodic:
                    QL_FAIL("this end condition is not implemented yet");
                case Lagrange:
                    L_.setFirstRow(1.0, 0.0);
                    tmp_.set(0, cubicInterpolatingPolynomialDerivative(xValue[0], xValue[1],
                            xValue[2], xValue[3],
                            yValue[0], yValue[1],
                            yValue[2], yValue[3],
                            xValue[0]));
                    break;
                default:
                    QL_FAIL("unknown end condition");
            }

            // right boundary condition
            switch (rightType_) {
                case NotAKnot:
                    // ignoring end condition value
                    L_.setLastRow(-(dx_.get(n_ - 2) + dx_.get(n_ - 3)) * (dx_.get(n_ - 2) + dx_.get(n_ - 3)),
                            -dx_.get(n_ - 3) * (dx_.get(n_ - 3) + dx_.get(n_ - 2)));
                    tmp_.set(n_ - 1, -S_.get(n_ - 3) * dx_.get(n_ - 2) * dx_.get(n_ - 2) -
                            S_.get(n_ - 2) * dx_.get(n_ - 3) * (3.0 * dx_.get(n_ - 2) + 2.0 * dx_.get(n_ - 3)));
                    break;
                case FirstDerivative:
                    L_.setLastRow(0.0, 1.0);
                    tmp_.set(n_ - 1, rightValue_);
                    break;
                case SecondDerivative:
                    L_.setLastRow(1.0, 2.0);
                    tmp_.set(n_ - 1, 3.0 * S_.get(n_ - 2) + rightValue_ * dx_.get(n_ - 2) / 2.0);
                    break;
                case Periodic:
                    QL_FAIL("this end condition is not implemented yet");
                case Lagrange:
                    L_.setLastRow(0.0, 1.0);
                    tmp_.set(n_ - 1, cubicInterpolatingPolynomialDerivative(xValue[n_ - 4], xValue[n_ - 3],
                            xValue[n_ - 2], xValue[n_ - 1],
                            yValue[n_ - 4], yValue[n_ - 3],
                            yValue[n_ - 2], yValue[n_ - 1],
                            xValue[n_ - 1]));
                    break;
                default:
                    QL_FAIL("unknown end condition");
            }

            // solve the system
            L_.solveFor(tmp_, tmp_);
        } else if (da_ == CubicInterpolation.DerivativeApprox.SplineOM1) {
            Matrix T_ = new Matrix(n_ - 2, n_, 0.0);
            for (int i = 0; i < n_ - 2; ++i) {
                T_.set(i, i, dx_.get(i) / 6.0);
                T_.set(i, i + 1, (dx_.get(i + 1) + dx_.get(i)) / 3.0);
                T_.set(i, i + 2, dx_.get(i + 1) / 6.0);
            }
            Matrix S_ = new Matrix(n_ - 2, n_, 0.0);
            for (int i = 0; i < n_ - 2; ++i) {
                S_.set(i, i, 1.0 / dx_.get(i));
                S_.set(i, i + 1, -(1.0 / dx_.get(i + 1) + 1.0 / dx_.get(i)));
                S_.set(i, i + 2, 1.0 / dx_.get(i + 1));
            }
            Matrix Up_ = new Matrix(n_, 2, 0.0);
            Up_.set(0, 0, 1);
            Up_.set(n_ - 1, 1, 1);
            Matrix Us_ = new Matrix(n_, n_ - 2, 0.0);
            for (int i = 0; i < n_ - 2; ++i)
                Us_.set(i + 1, i, 1);
            Matrix Z_ = Us_.multipy(CommonUtil.inverse(T_.multipy(Us_)));
            //Matrix Z_ = Us_*inverse(T_*Us_);
            Matrix I_ = new Matrix(n_, n_, 0.0);
            for (int i = 0; i < n_; ++i)
                I_.set(i, i, 1);
            Matrix V_ = (I_.subtract(Z_.multipy(T_))).multipy(Up_);
            // Matrix V_ = (I_-Z_*T_)*Up_;
            Matrix W_ = Z_.multipy(S_);
            Matrix Q_ = new Matrix(n_, n_, 0.0);
            Q_.set(0, 0, 1.0 / (n_ - 1) * dx_.get(0) * dx_.get(0) * dx_.get(0));
            Q_.set(0, 1, 7.0 / 8 / (n_ - 1) * dx_.get(0) * dx_.get(0) * dx_.get(0));
            for (int i = 1; i < n_ - 1; ++i) {
                Q_.set(i, i - 1, 7.0 / 8 / (n_ - 1) * dx_.get(i - 1) * dx_.get(i - 1) * dx_.get(i - 1));
                Q_.set(i, i, 1.0 / (n_ - 1) * dx_.get(i) * dx_.get(i) * dx_.get(i) + 1.0 / (n_ - 1) * dx_.get(i - 1) * dx_.get(i - 1) * dx_.get(i - 1));
                Q_.set(i, i + 1, 7.0 / 8 / (n_ - 1) * dx_.get(i) * dx_.get(i) * dx_.get(i));
            }
            Q_.set(n_ - 1, n_ - 2, 7.0 / 8 / (n_ - 1) * dx_.get(n_ - 2) * dx_.get(n_ - 2) * dx_.get(n_ - 2));
            Q_.set(n_ - 1, n_ - 1, 1.0 / (n_ - 1) * dx_.get(n_ - 2) * dx_.get(n_ - 2) * dx_.get(n_ - 2));
            Matrix J_ = (I_.subtract(V_
                    .multipy(CommonUtil.inverse(CommonUtil.transpose(V_).multipy(Q_).multipy(V_)))
                    .multipy(CommonUtil.transpose(V_))
                    .multipy(Q_)))
                    .multipy(W_);
            // Matrix J_ = (I_-V_*inverse(transpose(V_)*Q_*V_)*transpose(V_)*Q_)*W_;
            Array Y_ = new Array(n_);
            for (int i = 0; i < n_; ++i)
                Y_.set(i, yValue[i]);
            Array D_ = CommonUtil.Multiply(J_, Y_); //J_*Y_;
            for (int i = 0; i < n_ - 1; ++i)
                tmp_.set(i, (Y_.get(i + 1) - Y_.get(i)) / dx_.get(i) - (2.0 * D_.get(i) + D_.get(i + 1)) * dx_.get(i) / 6.0);
            tmp_.set(n_ - 1, tmp_.get(n_ - 2) + D_.get(n_ - 2) * dx_.get(n_ - 2) + (D_.get(n_ - 1) - D_.get(n_ - 2)) * dx_.get(n_ - 2) / 2.0);

        } else if (da_ == CubicInterpolation.DerivativeApprox.SplineOM2) {
            Matrix T_ = new Matrix(n_ - 2, n_, 0.0);
            for (int i = 0; i < n_ - 2; ++i) {
                T_.set(i, i, dx_.get(i) / 6.0);
                T_.set(i, i + 1, (dx_.get(i) + dx_.get(i + 1)) / 3.0);
                T_.set(i, i + 2, dx_.get(i + 1) / 6.0);
            }
            Matrix S_ = new Matrix(n_ - 2, n_, 0.0);
            for (int i = 0; i < n_ - 2; ++i) {
                S_.set(i, i, 1.0 / dx_.get(i));
                S_.set(i, i + 1, -(1.0 / dx_.get(i + 1) + 1.0 / dx_.get(i)));
                S_.set(i, i + 2, 1.0 / dx_.get(i + 1));
            }
            Matrix Up_ = new Matrix(n_, 2, 0.0);
            Up_.set(0, 0, 1);
            Up_.set(n_ - 1, 1, 1);
            Matrix Us_ = new Matrix(n_, n_ - 2, 0.0);
            for (int i = 0; i < n_ - 2; ++i)
                Us_.set(i + 1, i, 1);
            Matrix Z_ = Us_.multipy(CommonUtil.inverse(T_.multipy(Us_)));
            Matrix I_ = new Matrix(n_, n_, 0.0);
            for (int i = 0; i < n_; ++i)
                I_.set(i, i, 1);
            Matrix V_ = (I_.subtract(Z_.multipy(T_))).multipy(Up_);    //(I_-Z_*T_)*Up_;
            Matrix W_ = Z_.multipy(S_);
            Matrix Q_ = new Matrix(n_, n_, 0.0);
            Q_.set(0, 0, 1.0 / (n_ - 1) * dx_.get(0));
            Q_.set(0, 1, 1.0 / 2 / (n_ - 1) * dx_.get(0));
            for (int i = 1; i < n_ - 1; ++i) {
                Q_.set(i, i - 1, 1.0 / 2 / (n_ - 1) * dx_.get(i - 1));
                Q_.set(i, i, 1.0 / (n_ - 1) * dx_.get(i) + 1.0 / (n_ - 1) * dx_.get(i - 1));
                Q_.set(i, i + 1, 1.0 / 2 / (n_ - 1) * dx_.get(i));
            }
            Q_.set(n_ - 1, n_ - 2, 1.0 / 2 / (n_ - 1) * dx_.get(n_ - 2));
            Q_.set(n_ - 1, n_ - 1, 1.0 / (n_ - 1) * dx_.get(n_ - 2));
            Matrix J_ = (I_.subtract(
                    V_.multipy(CommonUtil.inverse(CommonUtil.transpose(V_).multipy(Q_).multipy(V_)))
                            .multipy(CommonUtil.transpose(V_))
                            .multipy(Q_))).multipy(W_);
            // (I_-V_*inverse(transpose(V_)*Q_*V_)*transpose(V_)*Q_)*W_;
            Array Y_ = new Array(n_);
            for (int i = 0; i < n_; ++i)
                Y_.set(i, yValue[i]);
            Array D_ = CommonUtil.Multiply(J_, Y_);
            for (int i = 0; i < n_ - 1; ++i)
                tmp_.set(i, (Y_.get(i + 1) - Y_.get(i)) / dx_.get(i) - (2.0 * D_.get(i) + D_.get(i + 1)) * dx_.get(i) / 6.0);
            tmp_.set(n_ - 1, tmp_.get(n_ - 2) + D_.get(n_ - 2) * dx_.get(n_ - 2) + (D_.get(n_ - 1) - D_.get(n_ - 2)) * dx_.get(n_ - 2) / 2.0);
        } else { // local schemes
            if (n_ == 2) {
                tmp_.set(0, S_.get(0));
                tmp_.set(1, S_.get(0));
                // tmp_[0] = tmp_[1] = S_[0];
            } else {
                switch (da_) {
                    case FourthOrder:
                        QL_FAIL("FourthOrder not implemented yet");
                        break;
                    case Parabolic:
                        // intermediate points
                        for (int i = 1; i < n_ - 1; ++i)
                            tmp_.set(i, (dx_.get(i - 1) * S_.get(i) + dx_.get(i) * S_.get(i - 1)) / (dx_.get(i) + dx_.get(i - 1)));
                        // end points
                        tmp_.set(0, ((2.0 * dx_.get(0) + dx_.get(1)) * S_.get(0) - dx_.get(0) * S_.get(1)) / (dx_.get(0) + dx_.get(1)));
                        tmp_.set(n_ - 1, ((2.0 * dx_.get(n_ - 2) + dx_.get(n_ - 3)) * S_.get(n_ - 2) - dx_.get(n_ - 2) * S_.get(n_ - 3)) / (dx_.get(n_ - 2) + dx_.get(n_ - 3)));
                        break;
                    case FritschButland:
                        // intermediate points
                        for (int i = 1; i < n_ - 1; ++i) {
                            double Smin = Math.min(S_.get(i - 1), S_.get(i));
                            double Smax = Math.max(S_.get(i - 1), S_.get(i));
                            if (Smax + 2.0 * Smin == 0) {
                                if (Smin * Smax < 0)
                                    tmp_.set(i, QL_MIN_REAL);
                                else if (Smin * Smax == 0)
                                    tmp_.set(i, 0);
                                else
                                    tmp_.set(i, QL_MAX_REAL);
                            } else
                                tmp_.set(i, 3.0 * Smin * Smax / (Smax + 2.0 * Smin)); //3.0*Smin*Smax/(Smax+2.0*Smin);
                        }
                        // end points
                        tmp_.set(0, ((2.0 * dx_.get(0) + dx_.get(1)) * S_.get(0) - dx_.get(0) * S_.get(1)) / (dx_.get(0) + dx_.get(1)));
                        tmp_.set(n_ - 1, ((2.0 * dx_.get(n_ - 2) + dx_.get(n_ - 3)) * S_.get(n_ - 2) - dx_.get(n_ - 2) * S_.get(n_ - 3)) / (dx_.get(n_ - 2) + dx_.get(n_ - 3)));
                        break;
                    case Akima:
                        tmp_.set(0, (abs(S_.get(1) - S_.get(0)) * 2 * S_.get(0) * S_.get(1) +
                                abs(2 * S_.get(0) * S_.get(1) - 4 * S_.get(0) * S_.get(0) * S_.get(1)) * S_.get(0)) / (abs(S_.get(1) - S_.get(0)) +
                                abs(2 * S_.get(0) * S_.get(1) - 4 * S_.get(0) * S_.get(0) * S_.get(1))));
                        tmp_.set(1, (abs(S_.get(2) - S_.get(1)) * S_.get(0) +
                                abs(S_.get(0) - 2 * S_.get(0) * S_.get(1)) * S_.get(1)) / (abs(S_.get(2) - S_.get(1)) +
                                abs(S_.get(0) - 2 * S_.get(0) * S_.get(1))));
                        for (int i = 2; i < n_ - 2; ++i) {
                            if ((S_.get(i - 2) == S_.get(i - 1)) && (S_.get(i) != S_.get(i + 1)))
                                tmp_.set(i, S_.get(i - 1));
                            else if ((S_.get(i - 2) != S_.get(i - 1)) && (S_.get(i) == S_.get(i + 1)))
                                tmp_.set(i, S_.get(i));
                            else if (S_.get(i) == S_.get(i - 1))
                                tmp_.set(i, S_.get(i));
                            else if ((S_.get(i - 2) == S_.get(i - 1)) && (S_.get(i - 1) != S_.get(i)) && (S_.get(i) == S_.get(i + 1)))
                                tmp_.set(i, (S_.get(i - 1) + S_.get(i)) / 2.0);
                            else
                                tmp_.set(i, (abs(S_.get(i + 1) - S_.get(i)) * S_.get(i - 1) +
                                        abs(S_.get(i - 1) - S_.get(i - 2)) * S_.get(i)) / (abs(S_.get(i + 1) - S_.get(i)) +
                                        abs(S_.get(i - 1) - S_.get(i - 2))));
                        }
                        tmp_.set(n_ - 2, (abs(2 * S_.get(n_ - 2) * S_.get(n_ - 3) - S_.get(n_ - 2)) * S_.get(n_ - 3) +
                                abs(S_.get(n_ - 3) - S_.get(n_ - 4)) * S_.get(n_ - 2)) / (abs(2 * S_.get(n_ - 2) *
                                S_.get(n_ - 3) - S_.get(n_ - 2)) + abs(S_.get(n_ - 3) - S_.get(n_ - 4))));
                        tmp_.set(n_ - 1, (abs(4 * S_.get(n_ - 2) * S_.get(n_ - 2) * S_.get(n_ - 3) - 2 * S_.get(n_ - 2) *
                                S_.get(n_ - 3)) * S_.get(n_ - 2) + abs(S_.get(n_ - 2) - S_.get(n_ - 3)) * 2 * S_.get(n_ - 2) *
                                S_.get(n_ - 3)) / (abs(4 * S_.get(n_ - 2) * S_.get(n_ - 2) * S_.get(n_ - 3) - 2 * S_.get(n_ - 2) *
                                S_.get(n_ - 3)) + abs(S_.get(n_ - 2) - S_.get(n_ - 3))));
                        break;
                    case Kruger:
                        // intermediate points
                        for (int i = 1; i < n_ - 1; ++i) {
                            if (S_.get(i - 1) * S_.get(i) < 0.0)
                                // slope changes sign at point
                                tmp_.set(i, 0d);
                            else
                                // slope will be between the slopes of the adjacent
                                // straight lines and should approach zero if the
                                // slope of either line approaches zero
                                tmp_.set(i, 2.0 / (1.0 / S_.get(i - 1) + 1.0 / S_.get(i)));
                            // tmp_[i] = 2.0/(1.0/S_[i-1]+1.0/S_[i]);
                        }
                        // end points
                        tmp_.set(0, (3.0 * S_.get(0) - tmp_.get(1)) / 2.0);
                        //tmp_[0] = (3.0*S_[0]-tmp_[1])/2.0;
                        tmp_.set(n_ - 1, (3.0 * S_.get(n_ - 2) - tmp_.get(n_ - 2)) / 2.0);
                        //tmp_[n_-1] = (3.0*S_[n_-2]-tmp_[n_-2])/2.0;
                        break;
                    case Harmonic:
                        // intermediate points
                        for (int i = 1; i < n_ - 1; ++i) {
                            double w1 = 2 * dx_.get(i) + dx_.get(i - 1);
                            double w2 = dx_.get(i) + 2 * dx_.get(i - 1);
                            if (S_.get(i - 1) * S_.get(i) <= 0.0)
                                // slope changes sign at point
                                tmp_.set(i, 0d);
                            else
                                // weighted harmonic mean of S_[i] and S_[i-1] if they
                                // have the same sign; otherwise 0
                                tmp_.set(i, (w1 + w2) / (w1 / S_.get(i - 1) + w2 / S_.get(i)));
                            //tmp_[i] = (w1 + w2) / (w1 / S_[i - 1] + w2 / S_[i]);
                        }
                        // end points [0]
                        tmp_.set(0, ((2 * dx_.get(0) + dx_.get(1)) * S_.get(0) - dx_.get(0) * S_.get(1)) / (dx_.get(1) + dx_.get(0)));
                        if (tmp_.get(0) * S_.get(0) < 0.0) {
                            tmp_.set(0, 0);
                        } else if (S_.get(0) * S_.get(1) < 0) {
                            if (abs(tmp_.get(0)) > abs(3 * S_.get(0))) {
                                tmp_.set(0, 3 * S_.get(0));
                            }
                        }
                        // end points [n-1]
                        tmp_.set(n_ - 1, ((2 * dx_.get(n_ - 2) + dx_.get(n_ - 3)) * S_.get(n_ - 2) - dx_.get(n_ - 2) * S_.get(n_ - 3)) / (dx_.get(n_ - 3) + dx_.get(n_ - 2)));
                        if (tmp_.get(n_ - 1) * S_.get(n_ - 2) < 0.0) {
                            tmp_.set(n_ - 1, 0);
                        } else if (S_.get(n_ - 2) * S_.get(n_ - 3) < 0) {
                            if (abs(tmp_.get(n_ - 1)) > abs(3 * S_.get(n_ - 2))) {
                                tmp_.set(n_ - 1, 3 * S_.get(n_ - 2));
                            }
                        }
                        break;
                    default:
                        QL_FAIL("unknown scheme");
                }
            }
        }
        monotonicityAdjustments_ = CommonUtil.ArrayInit(monotonicityAdjustments_.size(), false);
        // Hyman monotonicity constrained filter
        if (monotonic_) {
            double correction;
            double pm, pu, pd, M;
            for (int i = 0; i < n_; ++i) {
                if (i == 0) {
                    if (tmp_.get(i) * S_.get(0) > 0.0) {
                        correction = tmp_.get(i) / abs(tmp_.get(i)) *
                                Math.min(abs(tmp_.get(i)), abs(3.0 * S_.get(0)));
                    } else {
                        correction = 0.0;
                    }
                    if (correction != tmp_.get(i)) {
                        tmp_.set(i, correction);
                        monotonicityAdjustments_.set(i, true);
                    }
                } else if (i == n_ - 1) {
                    if (tmp_.get(i) * S_.get(n_ - 2) > 0.0) {
                        correction = tmp_.get(i) / abs(tmp_.get(i)) *
                                Math.min(abs(tmp_.get(i)),
                                        abs(3.0 * S_.get(n_ - 2)));
                    } else {
                        correction = 0.0;
                    }
                    if (correction != tmp_.get(i)) {
                        tmp_.set(i, correction);
                        monotonicityAdjustments_.set(i, true);
                    }
                } else {
                    pm = (S_.get(i - 1) * dx_.get(i) + S_.get(i) * dx_.get(i - 1)) /
                            (dx_.get(i - 1) + dx_.get(i));
                    M = 3.0 * CommonUtil.min(abs(S_.get(i - 1)), abs(S_.get(i)), abs(pm));
                    if (i > 1) {
                        if ((S_.get(i - 1) - S_.get(i - 2)) * (S_.get(i) - S_.get(i - 1)) > 0.0) {
                            pd = (S_.get(i - 1) * (2.0 * dx_.get(i - 1) + dx_.get(i - 2))
                                    - S_.get(i - 2) * dx_.get(i - 1)) /
                                    (dx_.get(i - 2) + dx_.get(i - 1));
                            if (pm * pd > 0.0 && pm * (S_.get(i - 1) - S_.get(i - 2)) > 0.0) {
                                M = Math.max(M, 1.5 * Math.min(abs(pm), abs(pd)));
                            }
                        }
                    }
                    if (i < n_ - 2) {
                        if ((S_.get(i) - S_.get(i - 1)) * (S_.get(i + 1) - S_.get(i)) > 0.0) {
                            pu = (S_.get(i) * (2.0 * dx_.get(i) + dx_.get(i + 1)) - S_.get(i + 1) * dx_.get(i)) /
                                    (dx_.get(i) + dx_.get(i + 1));
                            if (pm * pu > 0.0 && -pm * (S_.get(i) - S_.get(i - 1)) > 0.0) {
                                M = Math.max(M, 1.5 * Math.min(
                                        abs(pm), abs(pu)));
                            }
                        }
                    }
                    if (tmp_.get(i) * pm > 0.0) {
                        correction = tmp_.get(i) / abs(tmp_.get(i)) *
                                Math.min(abs(tmp_.get(i)), M);
                    } else {
                        correction = 0.0;
                    }
                    if (correction != tmp_.get(i)) {
                        tmp_.set(i, correction);
                        monotonicityAdjustments_.set(i, true);
                    }
                }
            }
        }


        // cubic coefficients
        for (int i = 0; i < n_ - 1; ++i) {
            a_.set(i, tmp_.get(i));
            b_.set(i, (3.0 * S_.get(i) - tmp_.get(i + 1) - 2.0 * tmp_.get(i)) / dx_.get(i));
            c_.set(i, (tmp_.get(i + 1) + tmp_.get(i) - 2.0 * S_.get(i)) / (dx_.get(i) * dx_.get(i)));
        }

        primitiveConst_.set(0, 0.0);
        for (int i = 1; i < n_ - 1; ++i) {
            primitiveConst_.set(i, primitiveConst_.get(i - 1)
                    + dx_.get(i - 1) *
                    (yValue[i - 1] + dx_.get(i - 1) *
                            (a_.get(i - 1) / 2.0 + dx_.get(i - 1) *
                                    (b_.get(i - 1) / 3.0 + dx_.get(i - 1) * c_.get(i - 1) / 4.0))));
        }
    }

    @Override
    public double value(double x) {
        int j = locale(x);
        double dx_ = x - xValue[j];
        return yValue[j] + dx_ * (a_.get(j) + dx_ * (b_.get(j) + dx_ * c_.get(j)));
    }

    @Override
    public double primitive(double x) {
        int j = locale(x);
        double dx_ = x - xValue[j];
        return primitiveConst_.get(j)
                + dx_ * (yValue[j] + dx_ * (a_.get(j) / 2.0
                + dx_ * (b_.get(j) / 3.0 + dx_ * c_.get(j) / 4.0)));
    }

    @Override
    public double derivative(double x) {
        int j = locale(x);
        double dx_ = x - xValue[j];
        return a_.get(j) + (2.0 * b_.get(j) + 3.0 * c_.get(j) * dx_) * dx_;
    }

    @Override
    public double secondDerivative(double x) {
        int j = locale(x);
        double dx_ = x - xValue[j];
        return 2.0 * b_.get(j) + 6.0 * c_.get(j) * dx_;
    }
}
