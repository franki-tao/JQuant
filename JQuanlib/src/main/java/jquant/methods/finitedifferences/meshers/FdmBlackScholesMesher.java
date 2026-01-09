package jquant.methods.finitedifferences.meshers;

import jquant.Handle;
import jquant.Quote;
import jquant.cashflows.Dividend;
import jquant.math.Point;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.processes.EulerDiscretization;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.yield.QuantoTermStructure;
import jquant.time.calendars.NullCalendar;
import jquant.utilities.FdmQuantoHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class FdmBlackScholesMesher extends Fdm1dMesher {
    public FdmBlackScholesMesher(
            int size,
            final GeneralizedBlackScholesProcess process,
            double maturity,
            double strike,
            double xMinConstraint,
            double xMaxConstraint,
            double eps,
            double scaleFactor,
            final Point<Double, Double> cPoint,
            final List<Dividend> dividendSchedule,
            final FdmQuantoHelper fdmQuantoHelper,
            double spotAdjustment) {
        super(size);
        final double S = process.x0();
        QL_REQUIRE(S > 0.0, "negative or null underlying given");

        List<Point<Double, Double>> intermediateSteps = new ArrayList<>();
        for (Dividend i : dividendSchedule) {
            final double t = process.time(i.date());
            if (t <= maturity && t >= 0.0)
                intermediateSteps.add(new Point<>(process.time(i.date()), i.amount()));
        }

        final int intermediateTimeSteps = Math.max(2, (int) (24.0 * maturity));
        for (int i = 0; i < intermediateTimeSteps; ++i)
            intermediateSteps.add(new Point<>((i + 1) * (maturity / intermediateTimeSteps), 0.0));

        Collections.sort(intermediateSteps);
        final Handle<YieldTermStructure> rTS = process.riskFreeRate();

        final Handle<YieldTermStructure> qTS = (fdmQuantoHelper) != null ? new Handle<>(new QuantoTermStructure(process.dividendYield(), process.riskFreeRate(),
                new Handle<>(fdmQuantoHelper.fTS_, true), process.blackVolatility(),
                strike, new Handle<>(fdmQuantoHelper.fxVolTS_, true),
                fdmQuantoHelper.exchRateATMlevel_, fdmQuantoHelper.equityFxCorrelation_), true) :
                process.dividendYield();

        double lastDivTime = 0.0;
        double fwd = S + spotAdjustment;
        double mi = fwd, ma = fwd;

        for (Point<Double, Double> intermediateStep : intermediateSteps) {
            final double divTime = intermediateStep.getFirst();
            final double divAmount = intermediateStep.getSecond();

            fwd = fwd / rTS.getValue().discount(divTime, false) * rTS.getValue().discount(lastDivTime, false)
                    * qTS.getValue().discount(divTime, false) / qTS.getValue().discount(lastDivTime, false);

            mi = Math.min(mi, fwd);
            ma = Math.max(ma, fwd);

            fwd -= divAmount;

            mi = Math.min(mi, fwd);
            ma = Math.max(ma, fwd);

            lastDivTime = divTime;
        }

        // Set the grid boundaries
        final double normInvEps = new InverseCumulativeNormal().value(1 - eps);
        final double sigmaSqrtT
                = process.blackVolatility().getValue().blackVol(maturity, strike, false)
                * Math.sqrt(maturity);

        double xMin = Math.log(mi) - sigmaSqrtT * normInvEps * scaleFactor;
        double xMax = Math.log(ma) + sigmaSqrtT * normInvEps * scaleFactor;

        if (!Double.isNaN(xMinConstraint)) {
            xMin = xMinConstraint;
        }
        if (!Double.isNaN(xMaxConstraint)) {
            xMax = xMaxConstraint;
        }

        Fdm1dMesher helper;
        if (!Double.isNaN(cPoint.getFirst())
                && Math.log(cPoint.getFirst()) >= xMin && Math.log(cPoint.getFirst()) <= xMax) {

            helper = new Concentrating1dMesher(xMin, xMax, size, new Point<>(Math.log(cPoint.getFirst()), cPoint.getSecond()), false);
        } else {
            helper = new Uniform1dMesher(xMin, xMax, size);

        }

        locations_ = helper.locations();
        for (int i = 0; i < locations_.size(); ++i) {
            dplus_.set(i, helper.dplus(i));
            dminus_.set(i, helper.dminus(i));
        }
    }

    public static GeneralizedBlackScholesProcess processHelper(final Handle<Quote> s0,
                                                               final Handle<YieldTermStructure> rTS,
                                                               final Handle<YieldTermStructure> qTS,
                                                               double vol) {
        return new GeneralizedBlackScholesProcess(s0, qTS, rTS,
                new Handle<>(new BlackConstantVol(rTS.getValue().referenceDate(),
                        new NullCalendar(),
                        vol,
                        rTS.getValue().dayCounter()
                ), true),
                new EulerDiscretization(),
                false);
    }
}
