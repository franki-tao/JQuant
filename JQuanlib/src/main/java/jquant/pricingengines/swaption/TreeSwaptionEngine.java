package jquant.pricingengines.swaption;

import jquant.Handle;
import jquant.Lattice;
import jquant.TimeGrid;
import jquant.instruments.Settlement;
import jquant.instruments.Swaption;
import jquant.models.ShortRateModel;
import jquant.models.TermStructureConsistentModel;
import jquant.pricingengines.LatticeShortRateModelEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Tree engine for Bermudan swaptions.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/swaption/treeswaptionengine.{hpp,cpp}}.
 */
public class TreeSwaptionEngine
        extends LatticeShortRateModelEngine<Swaption.arguments, Swaption.results> {

    private final Handle<YieldTermStructure> termStructure_;

    public TreeSwaptionEngine(ShortRateModel model, int timeSteps) {
        this(model, timeSteps, new Handle<YieldTermStructure>(null, false));
    }

    public TreeSwaptionEngine(ShortRateModel model,
                              int timeSteps,
                              Handle<YieldTermStructure> termStructure) {
        super(model, timeSteps, new Swaption.arguments(), new Swaption.results());
        termStructure_ = termStructure;
        registerWith(termStructure_.get());
    }

    public TreeSwaptionEngine(ShortRateModel model, TimeGrid timeGrid) {
        this(model, timeGrid, new Handle<YieldTermStructure>(null, false));
    }

    public TreeSwaptionEngine(ShortRateModel model,
                              TimeGrid timeGrid,
                              Handle<YieldTermStructure> termStructure) {
        super(model, timeGrid, new Swaption.arguments(), new Swaption.results());
        termStructure_ = termStructure;
        registerWith(termStructure_.get());
    }

    @Override
    public void calculate() {

        QL_REQUIRE(arguments_.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with "
                        + "TreeSwaptionEngine");
        QL_REQUIRE(model_ != null, "no model specified");

        final Date referenceDate;
        final DayCounter dayCounter;

        if (model_ instanceof TermStructureConsistentModel) {
            final TermStructureConsistentModel tsmodel =
                    (TermStructureConsistentModel) model_;
            referenceDate = tsmodel.termStructure().currentLink().referenceDate();
            dayCounter = tsmodel.termStructure().currentLink().dayCounter();
        } else {
            QL_REQUIRE(!termStructure_.empty(), "no term structure specified");
            referenceDate = termStructure_.currentLink().referenceDate();
            dayCounter = termStructure_.currentLink().dayCounter();
        }

        final DiscretizedSwaption swaption = new DiscretizedSwaption(
                arguments_, referenceDate, dayCounter);
        final Lattice lattice;

        if (lattice_ != null) {
            lattice = lattice_;
        } else {
            final List<Double> times = swaption.mandatoryTimes();
            final TimeGrid timeGrid = new TimeGrid(times, timeSteps_);
            lattice = model_.tree(timeGrid);
        }

        final List<Date> exerciseDates = arguments_.exercise.dates();
        final double[] stoppingTimes = new double[exerciseDates.size()];
        for (int i = 0; i < stoppingTimes.length; ++i)
            stoppingTimes[i] = dayCounter.yearFraction(
                    referenceDate, exerciseDates.get(i));

        swaption.initialize(lattice, stoppingTimes[stoppingTimes.length - 1]);

        double nextExercise = Double.NaN;
        for (double t : stoppingTimes) {
            if (t >= 0.0) {
                nextExercise = t;
                break;
            }
        }
        QL_REQUIRE(!Double.isNaN(nextExercise), "no exercise date found");
        swaption.rollback(nextExercise);

        results_.value = swaption.presentValue();
    }
}
