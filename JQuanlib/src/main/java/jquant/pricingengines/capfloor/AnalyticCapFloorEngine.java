package jquant.pricingengines.capfloor;

import jquant.Handle;
import jquant.Option;
import jquant.Settings;
import jquant.instruments.CapFloor;
import jquant.models.AffineModel;
import jquant.models.TermStructureConsistentModel;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic cap/floor engine for affine models.
 *
 * <p>Official QuantLib
 * {@code ql/pricingengines/capfloor/analyticcapfloorengine.{hpp,cpp}} port.
 * Official derives from {@code GenericModelEngine<AffineModel, ...>}; the
 * Java port holds the model directly and registers with it, preserving the
 * official {@code calculate()} financial semantics.
 */
public class AnalyticCapFloorEngine extends CapFloor.engine {

    private final AffineModel model_;
    private final Handle<YieldTermStructure> termStructure_;

    public AnalyticCapFloorEngine(final AffineModel model,
                                  Handle<YieldTermStructure> termStructure) {
        model_ = model;
        termStructure_ = termStructure;
        registerWith(model_);
        registerWith(termStructure_.get());
    }

    @Override
    public void calculate() {
        QL_REQUIRE(model_ != null, "null model");

        Date referenceDate;
        DayCounter dayCounter;

        if (model_ instanceof TermStructureConsistentModel tsmodel) {
            referenceDate = tsmodel.termStructure().currentLink().referenceDate();
            dayCounter = tsmodel.termStructure().currentLink().dayCounter();
        } else {
            referenceDate = termStructure_.currentLink().referenceDate();
            dayCounter = termStructure_.currentLink().dayCounter();
        }

        double value = 0.0;
        CapFloor.Type type = arguments_.type;
        int nPeriods = arguments_.endDates.size();

        boolean includeRefDatePayments =
                Settings.instance.includeReferenceDateEvents();
        if (TimeUtils.equals(referenceDate,
                Settings.instance.evaluationDate().Date())) {
            Optional<Boolean> includeTodaysPayments =
                    Settings.instance.includeTodaysCashFlows();
            if (includeTodaysPayments != null && includeTodaysPayments.isPresent())
                includeRefDatePayments = includeTodaysPayments.get();
        }

        for (int i = 0; i < nPeriods; i++) {

            double fixingTime = dayCounter.yearFraction(referenceDate,
                    arguments_.fixingDates.get(i));
            double paymentTime = dayCounter.yearFraction(referenceDate,
                    arguments_.endDates.get(i));

            boolean notExpired = includeRefDatePayments
                    ? paymentTime >= 0.0 : paymentTime > 0.0;

            if (notExpired) {
                double tenor = arguments_.accrualTimes.get(i);
                double fixing = arguments_.forwards.get(i);
                if (fixingTime <= 0.0) {
                    if (type == CapFloor.Type.Cap || type == CapFloor.Type.Collar) {
                        double discount = model_.discount(paymentTime);
                        double strike = arguments_.capRates.get(i);
                        value += discount * arguments_.nominals.get(i) * tenor
                                * arguments_.gearings.get(i)
                                * Math.max(0.0, fixing - strike);
                    }
                    if (type == CapFloor.Type.Floor || type == CapFloor.Type.Collar) {
                        double discount = model_.discount(paymentTime);
                        double strike = arguments_.floorRates.get(i);
                        double mult = (type == CapFloor.Type.Floor) ? 1.0 : -1.0;
                        value += discount * arguments_.nominals.get(i) * tenor
                                * mult * arguments_.gearings.get(i)
                                * Math.max(0.0, strike - fixing);
                    }
                } else {
                    double maturity = dayCounter.yearFraction(referenceDate,
                            arguments_.startDates.get(i));
                    if (type == CapFloor.Type.Cap || type == CapFloor.Type.Collar) {
                        double temp = 1.0 + arguments_.capRates.get(i) * tenor;
                        value += arguments_.nominals.get(i)
                                * arguments_.gearings.get(i) * temp
                                * model_.discountBondOption(Option.Type.Put,
                                1.0 / temp, maturity, paymentTime);
                    }
                    if (type == CapFloor.Type.Floor || type == CapFloor.Type.Collar) {
                        double temp = 1.0 + arguments_.floorRates.get(i) * tenor;
                        double mult = (type == CapFloor.Type.Floor) ? 1.0 : -1.0;
                        value += arguments_.nominals.get(i)
                                * arguments_.gearings.get(i) * temp * mult
                                * model_.discountBondOption(Option.Type.Call,
                                1.0 / temp, maturity, paymentTime);
                    }
                }
            }
        }

        results_.value = value;
    }
}
