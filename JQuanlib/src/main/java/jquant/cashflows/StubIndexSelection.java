package jquant.cashflows;

import jquant.indexes.IborIndex;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Index selection for irregular Ibor coupons
/*! A default constructor is empty, meaning that broken
    periods fix on the leg's own index as usual.  A non-empty one supplies
    the candidate indices used instead.

    https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/stubiborcoupon.hpp
 */
public class StubIndexSelection {
    //! Convention used to select an index for an irregular Ibor coupon
    public enum Convention {
        ClosestIndex,
        Interpolated
    }

    private Convention convention_ = Convention.ClosestIndex;
    private List<IborIndex> indices_ = new ArrayList<>();

    //! empty selection: broken periods fix on the leg's own index
    public StubIndexSelection() {
    }

    public StubIndexSelection(Convention convention, List<IborIndex> indices) {
        convention_ = convention;
        indices_ = indices;
        QL_REQUIRE(!indices_.isEmpty(),
                "no candidate indices provided for stub index selection");
        for (IborIndex candidate : indices_)
            QL_REQUIRE(candidate != null,
                    "null candidate index for stub index selection");
    }

    public boolean empty() {
        return indices_.isEmpty();
    }

    public Convention convention() {
        return convention_;
    }

    public List<IborIndex> indices() {
        return indices_;
    }
}
