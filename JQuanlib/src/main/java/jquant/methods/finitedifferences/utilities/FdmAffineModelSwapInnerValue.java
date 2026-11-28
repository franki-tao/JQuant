package jquant.methods.finitedifferences.utilities;

import jquant.CashFlow;
import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.cashflows.Coupon;
import jquant.indexes.IborIndex;
import jquant.instruments.FixedVsFloatingSwap;
import jquant.instruments.OvernightIndexedSwap;
import jquant.instruments.Swap;
import jquant.instruments.VanillaSwap;
import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.models.AffineModel;
import jquant.models.TermStructureConsistentModel;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.models.shortrate.twofactormodels.G2;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.Map;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Inner value of a swap for affine short-rate models.
 *
 * <p>Ported from QuantLib
 * {@code ql/methods/finitedifferences/utilities/fdmaffinemodelswapinnervalue.hpp}.
 * The official class is a template; the Java port keeps the model type
 * generic but has to provide the model-specific state mapping through
 * {@link #getState}. The OIS cloning branch is added together with the
 * overnight-indexed-swap constructor extension it depends on.
 */
public class FdmAffineModelSwapInnerValue<
        M extends AffineModel & TermStructureConsistentModel>
        implements FdmInnerValueCalculator {

    private final RelinkableHandle<YieldTermStructure> disTs_ = new RelinkableHandle<>();
    private final RelinkableHandle<YieldTermStructure> fwdTs_ = new RelinkableHandle<>();

    private final M disModel_;
    private final M fwdModel_;

    private final IborIndex index_;
    private final FixedVsFloatingSwap swap_;
    private final Map<Double, Date> exerciseDates_;
    private final FdmMesher mesher_;
    private final int direction_;

    public FdmAffineModelSwapInnerValue(M disModel,
                                        M fwdModel,
                                        FixedVsFloatingSwap swap,
                                        Map<Double, Date> exerciseDates,
                                        FdmMesher mesher,
                                        int direction) {
        disModel_ = disModel;
        fwdModel_ = fwdModel;
        index_ = swap.iborIndex();

        if (swap instanceof OvernightIndexedSwap) {
            final OvernightIndexedSwap ois = (OvernightIndexedSwap) swap;
            final jquant.indexes.OvernightIndex clonedIndex =
                    (jquant.indexes.OvernightIndex) ois.overnightIndex().clone(fwdTs_);
            QL_REQUIRE(clonedIndex != null, "failed to clone OvernightIndex");
            swap_ = new OvernightIndexedSwap(
                    ois.swapType(),
                    ois.nominal(),
                    ois.fixedSchedule(),
                    ois.fixedRate(),
                    ois.fixedDayCount(),
                    ois.floatingSchedule(),
                    clonedIndex,
                    ois.spread(),
                    ois.paymentLag(),
                    ois.paymentConvention(),
                    ois.paymentCalendar(),
                    ois.telescopicValueDates(),
                    ois.averagingMethod(),
                    ois.lookbackDays(),
                    ois.lockoutDays(),
                    ois.applyObservationShift());
        } else {
            final VanillaSwap.Type vanillaType = swap.swapType() == Swap.Type.Payer
                    ? VanillaSwap.Type.Payer : VanillaSwap.Type.Receiver;

            swap_ = new VanillaSwap(
                    vanillaType, swap.nominal(),
                    swap.fixedSchedule(), swap.fixedRate(), swap.fixedDayCount(),
                    swap.floatingSchedule(), swap.iborIndex().clone(fwdTs_),
                    swap.spread(), swap.floatingDayCount(),
                    Optional.of(swap.paymentConvention()), Optional.empty());
        }

        exerciseDates_ = exerciseDates;
        mesher_ = mesher;
        direction_ = direction;
    }

    @Override
    public double innerValue(FdmLinearOpIterator iter, double t) {
        final Date iterExerciseDate = exerciseDates_.get(t);

        final Array disRate = getState(disModel_, t, iter);
        final Array fwdRate = getState(fwdModel_, t, iter);

        if (disTs_.empty()
                || !TimeUtils.equals(iterExerciseDate,
                        disTs_.currentLink().referenceDate())) {

            final Handle<YieldTermStructure> discount = disModel_.termStructure();

            disTs_.linkTo(new FdmAffineModelTermStructure(disRate,
                    discount.currentLink().calendar(),
                    discount.currentLink().dayCounter(),
                    iterExerciseDate,
                    discount.currentLink().referenceDate(),
                    disModel_), true);

            final Handle<YieldTermStructure> fwd = fwdModel_.termStructure();

            fwdTs_.linkTo(new FdmAffineModelTermStructure(fwdRate,
                    fwd.currentLink().calendar(),
                    fwd.currentLink().dayCounter(),
                    iterExerciseDate,
                    fwd.currentLink().referenceDate(),
                    fwdModel_), true);
        } else {
            ((FdmAffineModelTermStructure) disTs_.currentLink()).setVariable(disRate);
            ((FdmAffineModelTermStructure) fwdTs_.currentLink()).setVariable(fwdRate);
        }

        double npv = 0.0;
        for (int j = 0; j < 2; j++) {
            for (CashFlow cashFlow : swap_.leg(j)) {
                final Coupon coupon = (Coupon) cashFlow;
                if (TimeUtils.geq(coupon.accrualStartDate(), iterExerciseDate))
                    npv += cashFlow.amount()
                            * disTs_.currentLink().discount(cashFlow.date());
            }
            if (j == 0)
                npv *= -1.0;
        }
        if (swap_.swapType() == Swap.Type.Receiver)
            npv *= -1.0;

        return Math.max(0.0, npv);
    }

    @Override
    public double avgInnerValue(FdmLinearOpIterator iter, double t) {
        return innerValue(iter, t);
    }

    private Array getState(M model, double t, FdmLinearOpIterator iter) {
        if (model instanceof HullWhite) {
            final HullWhite hullWhite = (HullWhite) model;
            return new Array(new double[]{
                    hullWhite.dynamics().shortRate(
                            t, mesher_.location(iter, direction_))});
        } else if (model instanceof G2) {
            return new Array(new double[]{
                    mesher_.location(iter, direction_),
                    mesher_.location(iter, direction_ + 1)});
        }
        QL_FAIL("unsupported model type in FdmAffineModelSwapInnerValue");
        return null;
    }
}
