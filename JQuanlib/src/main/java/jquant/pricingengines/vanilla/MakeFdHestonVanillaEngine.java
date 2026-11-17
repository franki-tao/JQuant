package jquant.pricingengines.vanilla;

import jquant.cashflows.Dividend;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.models.equity.HestonModel;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.time.Date;
import jquant.utilities.FdmQuantoHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link FdHestonVanillaEngine}.
 *
 * <p>Port of QuantLib {@code MakeFdHestonVanillaEngine} from
 * {@code ql/pricingengines/vanilla/fdhestonvanillaengine.hpp}.
 */
public class MakeFdHestonVanillaEngine {
    private final HestonModel hestonModel_;
    private List<Dividend> dividends_ = new ArrayList<>();
    private int tGrid_ = 100, xGrid_ = 100, vGrid_ = 50, dampingSteps_ = 0;
    private FdmSchemeDesc schemeDesc_ = FdmSchemeDesc.Hundsdorfer();
    private LocalVolTermStructure leverageFct_;
    private FdmQuantoHelper quantoHelper_;

    public MakeFdHestonVanillaEngine(final HestonModel hestonModel) {
        hestonModel_ = hestonModel;
    }

    public MakeFdHestonVanillaEngine withQuantoHelper(
            final FdmQuantoHelper quantoHelper) {
        quantoHelper_ = quantoHelper;
        return this;
    }

    public MakeFdHestonVanillaEngine withTGrid(int tGrid) {
        tGrid_ = tGrid;
        return this;
    }

    public MakeFdHestonVanillaEngine withXGrid(int xGrid) {
        xGrid_ = xGrid;
        return this;
    }

    public MakeFdHestonVanillaEngine withVGrid(int vGrid) {
        vGrid_ = vGrid;
        return this;
    }

    public MakeFdHestonVanillaEngine withDampingSteps(int dampingSteps) {
        dampingSteps_ = dampingSteps;
        return this;
    }

    public MakeFdHestonVanillaEngine withFdmSchemeDesc(
            final FdmSchemeDesc schemeDesc) {
        schemeDesc_ = schemeDesc;
        return this;
    }

    public MakeFdHestonVanillaEngine withLeverageFunction(
            final LocalVolTermStructure leverageFct) {
        leverageFct_ = leverageFct;
        return this;
    }

    public MakeFdHestonVanillaEngine withCashDividends(
            final List<Date> dividendDates,
            final double[] dividendAmounts) {
        dividends_ = Dividend.dividendVector(dividendDates, dividendAmounts);
        return this;
    }

    public FdHestonVanillaEngine build() {
        return new FdHestonVanillaEngine(
                hestonModel_, dividends_, quantoHelper_,
                tGrid_, xGrid_, vGrid_, dampingSteps_,
                schemeDesc_, leverageFct_, 1.0);
    }
}
