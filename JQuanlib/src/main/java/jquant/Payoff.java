package jquant;

import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;

import static jquant.math.CommonUtil.QL_FAIL;

//! Abstract base class for option payoffs
public interface Payoff {
    //! \name Payoff interface
    //@{
    /*! \warning This method is used for output and comparison between
            payoffs. It is <b>not</b> meant to be used for writing
            switch-on-type code.
    */
    String name();

    String description();

    double value(double price);

    default void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<Payoff> vv = (Visitor<Payoff>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a payoff visitor");
            }
        }
    }
}
