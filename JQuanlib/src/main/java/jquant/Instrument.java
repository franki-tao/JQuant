package jquant;

import jquant.patterns.LazyObject;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.Map;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Abstract instrument class
/*! This class is purely abstract and defines the interface of concrete
    instruments which will be derived from this one.

    \test observability of class instances is checked.
*/
public abstract class Instrument extends LazyObject {
    protected double NPV_;
    protected double errorEstimate_;
    protected Date valuationDate_;
    protected Map<String, Object> additionalResults_;
    protected PricingEngine engine_;

    public static class results implements PricingEngine.results {
        public double value;
        public double errorEstimate;
        public Date valuationDate;
        public Map<String, Object> additionalResults;

        @Override
        public void reset() {
            value = Double.NaN;
            errorEstimate = Double.NaN;
            valuationDate = new Date();
            additionalResults.clear();
        }
    }

    public Instrument() {
        NPV_ = Double.NaN;
        errorEstimate_ = Double.NaN;
        // this makes sense in general (if the evaluation date
        // changes, you probably want to recalculate) and can also
        // help avoid some edge cases when lazy objects only forward
        // their first notification.
        registerWith(Settings.instance.evaluationDate());
    }

    //! returns the net present value of the instrument.
    public double NPV() {
        calculate();
        QL_REQUIRE(!Double.isNaN(NPV_), "NPV not provided");
        return NPV_;
    }

    //! returns the error estimate on the NPV when available.
    public double errorEstimate() {
        calculate();
        QL_REQUIRE(!Double.isNaN(errorEstimate_),
                "error estimate not provided");
        return errorEstimate_;
    }

    //! returns the date the net present value refers to.
    public final Date valuationDate() {
        calculate();
        QL_REQUIRE(TimeUtils.neq(valuationDate_, new Date()),
                "valuation date not provided");
        return valuationDate_;
    }

    //! returns any additional result returned by the pricing engine.
    public <T> T result(final String tag, Class<T> type) {
        calculate();
        Object value = additionalResults_.get(tag);
        QL_REQUIRE(value != null, tag + " not provided");
        return type.cast(value);
    }
    //! returns all additional result returned by the pricing engine.
    public final Map<String, Object> additionalResults() {
        calculate();
        return additionalResults_;
    }

    //! returns whether the instrument might have value greater than zero.
    public abstract boolean isExpired();

    //! set the pricing engine to be used.
    /*! \warning calling this method will have no effects in
                 case the <b>performCalculation</b> method
                 was overridden in a derived class.
    */
    public void setPricingEngine(final PricingEngine e) {
        if (engine_ != null)
            unregisterWith(engine_);
        engine_ = e;
        if (engine_ != null)
            registerWith(engine_);
        // trigger (lazy) recalculation and notify observers
        update();
    }

    /*! When a derived argument structure is defined for an
        instrument, this method should be overridden to fill
        it. This is mandatory in case a pricing engine is used.
    */
    public void setupArguments(PricingEngine.arguments a) {
        QL_FAIL("Instrument::setupArguments() not implemented");
    }

    /*! When a derived result structure is defined for an
        instrument, this method should be overridden to read from
        it. This is mandatory in case a pricing engine is used.
    */
    public void fetchResults(final PricingEngine.results r) {
        if (r instanceof Instrument.results rr) {
            NPV_ = rr.value;
            errorEstimate_ = rr.errorEstimate;
            valuationDate_ = rr.valuationDate;

            additionalResults_ = rr.additionalResults;
        } else {
            QL_FAIL("no results returned from pricing engine");
        }
    }

    public void calculate() {
        if (!calculated_) {
            if (isExpired()) {
                setupExpired();
                calculated_ = true;
            } else {
                super.calculate();
            }
        }
    }

    /*! This method must leave the instrument in a consistent
        state when the expiration condition is met.
    */
    protected void setupExpired() {
        NPV_ = errorEstimate_ = 0.0;
        valuationDate_ = new Date();
        additionalResults_.clear();
    }

    /*! In case a pricing engine is <b>not</b> used, this
        method must be overridden to perform the actual
        calculations and set any needed results. In case
        a pricing engine is used, the default implementation
        can be used.
    */
    protected void performCalculations() {
        QL_REQUIRE(engine_ != null, "null pricing engine");
        engine_.reset();
        setupArguments(engine_.getArguments());
        engine_.getArguments().validate();
        engine_.calculate();
        fetchResults(engine_.getResults());
    }
}
