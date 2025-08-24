package math.optimization;

import math.Array;
import math.CommonUtil;
import math.ReferencePkg;

import java.util.List;

import static math.CommonUtil.QL_REQUIRE;

public interface Projection {

    default void mapFreeParameters(Array parameterValues) {

        QL_REQUIRE(parameterValues.size() == numberOfFreeParameters_.getT(),
                "parameterValues.size()!=numberOfFreeParameters");
        int i = 0;
        for (int j = 0; j < actualParameters_.getT().size(); j++) {
            if (!fixParameters_.getT().get(j)) {
                actualParameters_.getT().set(j, parameterValues.get(i++));
            }
        }

    }

    ReferencePkg<Integer> numberOfFreeParameters_ = new ReferencePkg<>(0);
    ReferencePkg<Array> fixedParameters_ = new ReferencePkg<>();

    ReferencePkg<Array> actualParameters_ = new ReferencePkg<>();

    ReferencePkg<List<Boolean>> fixParameters_ = new ReferencePkg<>();

    //! returns the subset of free parameters corresponding
    // to set of parameters
    default Array project(Array parameters) {
        QL_REQUIRE(parameters.size() == fixParameters_.getT().size(),
                "parameters.size()!=parametersFreedoms_.size()");
        Array projectedParameters = new Array(numberOfFreeParameters_.getT());
        int i = 0;
        for (int j = 0; j < fixParameters_.getT().size(); j++) {
            if (!fixParameters_.getT().get(j)) {
                projectedParameters.set(i++, parameters.get(j));
            }
        }
        return projectedParameters;
    }

    //! returns whole set of parameters corresponding to the set
    // of projected parameters
    default Array include(Array projectedParameters) {
        QL_REQUIRE(projectedParameters.size() == numberOfFreeParameters_.getT(),
                "projectedParameters.size()!=numberOfFreeParameters");
        Array y = new Array(fixedParameters_.getT());
        int i = 0;
        for (int j = 0; j < y.size(); j++) {
            if (!fixParameters_.getT().get(j)) {
                y.set(j, projectedParameters.get(i++));
            }
        }
        return y;
    }

    default void Projection(Array parameterValues, List<Boolean> fixParameters) {
        fixedParameters_.setT(parameterValues);
        actualParameters_.setT(parameterValues);
        fixParameters_.setT(fixParameters);
        if (fixParameters_.getT().isEmpty()) {
            fixParameters_.setT(CommonUtil.ArrayInit(actualParameters_.getT().size(), false));
        }


        QL_REQUIRE(fixedParameters_.getT().size() == fixParameters_.getT().size(),
                "fixedParameters_.size()!=parametersFreedoms_.size()");
        for (boolean fixParameter : fixParameters_.getT()) {
            if (!fixParameter) {
                numberOfFreeParameters_.setT(numberOfFreeParameters_.getT() + 1);
            }
        }
        QL_REQUIRE(numberOfFreeParameters_.getT() > 0, "numberOfFreeParameters==0");
    }

}
