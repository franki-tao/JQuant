package jquant.pricingengines.vanilla;

import jquant.cashflows.Dividend;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.processes.CoxIngersollRossProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.utilities.FdmQuantoHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link FdCIRVanillaEngine}.
 *
 * <p>Port of QuantLib {@code MakeFdCIRVanillaEngine} from
 * {@code ql/pricingengines/vanilla/fdcirvanillaengine.hpp}.
 */
public class MakeFdCIRVanillaEngine {
    private final CoxIngersollRossProcess cirProcess_;
    private final GeneralizedBlackScholesProcess bsProcess_;
    private List<Dividend> dividends_ = new ArrayList<>();
    private final double rho_;
    private int tGrid_ = 10, xGrid_ = 100, rGrid_ = 100, dampingSteps_ = 0;
    private FdmSchemeDesc schemeDesc_ = FdmSchemeDesc.ModifiedHundsdorfer();
    private FdmQuantoHelper quantoHelper_;

    public MakeFdCIRVanillaEngine withQuantoHelper(
            final FdmQuantoHelper quantoHelper) {
        quantoHelper_ = quantoHelper;
        return this;
    }

    public MakeFdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess,
                                  final GeneralizedBlackScholesProcess bsProcess,
                                  double rho) {
        cirProcess_ = cirProcess;
        bsProcess_ = bsProcess;
        rho_ = rho;
    }

    public MakeFdCIRVanillaEngine withTGrid(int tGrid) {
        tGrid_ = tGrid;
        return this;
    }

    public MakeFdCIRVanillaEngine withXGrid(int xGrid) {
        xGrid_ = xGrid;
        return this;
    }

    public MakeFdCIRVanillaEngine withRGrid(int rGrid) {
        rGrid_ = rGrid;
        return this;
    }

    public MakeFdCIRVanillaEngine withDampingSteps(int dampingSteps) {
        dampingSteps_ = dampingSteps;
        return this;
    }

    public MakeFdCIRVanillaEngine withFdmSchemeDesc(final FdmSchemeDesc schemeDesc) {
        schemeDesc_ = schemeDesc;
        return this;
    }

    public MakeFdCIRVanillaEngine withCashDividends(final List<Date> dividendDates,
                                                    final double[] dividendAmounts) {
        dividends_ = Dividend.dividendVector(dividendDates, dividendAmounts);
        return this;
    }

    public FdCIRVanillaEngine build() {
        return new FdCIRVanillaEngine(cirProcess_, bsProcess_, dividends_,
                tGrid_, xGrid_, rGrid_, dampingSteps_, rho_, schemeDesc_,
                quantoHelper_);
    }
}
