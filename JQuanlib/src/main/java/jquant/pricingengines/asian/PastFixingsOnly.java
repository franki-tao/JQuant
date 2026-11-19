package jquant.pricingengines.asian;

/**
 * Thrown when all fixing dates of a discrete Asian option are in the past.
 *
 * <p>Port of {@code QuantLib::detail::PastFixingsOnly} from
 * {@code ql/pricingengines/asian/mcdiscreteasianenginebase.hpp}.
 */
public class PastFixingsOnly extends RuntimeException {
    public PastFixingsOnly() {
        super("all fixings are in the past");
    }
}
