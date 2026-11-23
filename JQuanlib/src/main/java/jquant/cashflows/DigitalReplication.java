package jquant.cashflows;

//! Specification of a digital option replication strategy
/*! Port of QuantLib's {@code DigitalReplication} from
    {@code ql/cashflows/replication.hpp}.
*/
public class DigitalReplication {
    private final double gap_;
    private final Replication replicationType_;

    public DigitalReplication() {
        this(Replication.Central, 1e-4);
    }

    public DigitalReplication(final Replication t) {
        this(t, 1e-4);
    }

    public DigitalReplication(final Replication t, double gap) {
        gap_ = gap;
        replicationType_ = t;
    }

    public Replication replicationType() {
        return replicationType_;
    }

    public double gap() {
        return gap_;
    }
}
