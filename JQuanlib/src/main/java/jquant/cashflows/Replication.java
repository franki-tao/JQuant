package jquant.cashflows;

//! Digital option replication strategy
/*! Specification of replication strategies used to price the embedded
    digital option in a digital coupon. Port of QuantLib's
    {@code Replication::Type} from {@code ql/cashflows/replication.hpp}.
*/
public enum Replication {
    Sub,
    Central,
    Super
}
