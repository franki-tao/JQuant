package jquant.pricingengines.vanilla;

import jquant.cashflows.Dividend;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.MathUtils.NULL_REAL;

/**
 * Factory for {@link FdBlackScholesVanillaEngine}.
 *
 * <p>Port of {@code MakeFdBlackScholesVanillaEngine} from
 * {@code ql/pricingengines/vanilla/fdblackscholesvanillaengine.hpp}.
 * The quanto helper is not part of the Java port.
 */
public class MakeFdBlackScholesVanillaEngine {
    private final GeneralizedBlackScholesProcess process_;
    private List<Dividend> dividends_ = new ArrayList<>();
    private int tGrid_ = 100, xGrid_ = 100, dampingSteps_ = 0;
    private FdmSchemeDesc schemeDesc_ = FdmSchemeDesc.Douglas();
    private boolean localVol_ = false;
    private double illegalLocalVolOverwrite_ = -NULL_REAL;
    private FdBlackScholesVanillaEngine.CashDividendModel cashDividendModel_ =
            FdBlackScholesVanillaEngine.CashDividendModel.Spot;

    public MakeFdBlackScholesVanillaEngine(
            final GeneralizedBlackScholesProcess process) {
        process_ = process;
    }

    public MakeFdBlackScholesVanillaEngine withTGrid(int tGrid) {
        tGrid_ = tGrid;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withXGrid(int xGrid) {
        xGrid_ = xGrid;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withDampingSteps(int dampingSteps) {
        dampingSteps_ = dampingSteps;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withFdmSchemeDesc(
            final FdmSchemeDesc schemeDesc) {
        schemeDesc_ = schemeDesc;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withLocalVol(boolean localVol) {
        localVol_ = localVol;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withIllegalLocalVolOverwrite(
            double illegalLocalVolOverwrite) {
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withCashDividends(
            final List<Date> dividendDates,
            final double[] dividendAmounts) {
        dividends_ = Dividend.dividendVector(dividendDates, dividendAmounts);
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withCashDividendModel(
            FdBlackScholesVanillaEngine.CashDividendModel cashDividendModel) {
        cashDividendModel_ = cashDividendModel;
        return this;
    }

    public FdBlackScholesVanillaEngine build() {
        return new FdBlackScholesVanillaEngine(
                process_, dividends_,
                tGrid_, xGrid_, dampingSteps_,
                schemeDesc_,
                localVol_,
                illegalLocalVolOverwrite_,
                cashDividendModel_);
    }
}
