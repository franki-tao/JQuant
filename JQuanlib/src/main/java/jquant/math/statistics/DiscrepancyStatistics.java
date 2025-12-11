package jquant.math.statistics;

import org.apache.commons.math3.util.FastMath;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ! Statistic tool for sequences with discrepancy calculation
 * ! It inherit from SequenceStatistics<Statistics> and adds
 * \f$ L^2 \f$ discrepancy calculation
 */
public class DiscrepancyStatistics {
    private double adiscr_, cdiscr_;
    private double bdiscr_, ddiscr_;
    private SequenceStatistics stat;

    public DiscrepancyStatistics(int dimension) {
        this.stat = new SequenceStatistics(dimension);
        reset(dimension);
    }

    public double discrepancy() {
        int N = stat.samples();
        return FastMath.sqrt(adiscr_/(N*N)-bdiscr_/N*cdiscr_+ddiscr_);
    }

    public void add(List<Double> value, double weight) {
        stat.add(value, weight);
        int k, m, N = stat.samples();

        double r_ik, r_jk, temp = 1.0;
        int it;
        for (k=0, it=0; k<stat.size(); ++it, ++k) {
            r_ik = value.get(it); //i=N
            temp *= (1.0 - r_ik*r_ik);
        }
        cdiscr_ += temp;

        for (m=0; m<N-1; m++) {
            temp = 1.0;
            for (k=0, it=0; k<stat.size(); ++it, ++k) {
                // running i=1..(N-1)
                r_ik = stat.get(k).data().get(m).getFirst();
                // fixed j=N
                r_jk = value.get(it);
                temp *= (1.0 - Math.max(r_ik, r_jk));
            }
            adiscr_ += temp;

            temp = 1.0;
            for (k=0, it=0; k<stat.size(); ++it, ++k) {
                // fixed i=N
                r_ik = value.get(it);
                // running j=1..(N-1)
                r_jk = stat.get(k).data().get(m).getFirst();
                temp *= (1.0 - Math.max(r_ik, r_jk));
            }
            adiscr_ += temp;
        }
        temp = 1.0;
        for (k=0, it=0; k<stat.size(); ++it, ++k) {
            // fixed i=N, j=N
            r_ik = r_jk = value.get(it);
            temp *= (1.0 - Math.max(r_ik, r_jk));
        }
        adiscr_ += temp;
    }

    public void reset(int dimension) {
        if (dimension == 0)           // if no size given,
            dimension = stat.size();   // keep the current one
        QL_REQUIRE(dimension != 1,
                "dimension==1 not allowed");

        stat.reset(dimension);

        adiscr_ = 0.0;
        bdiscr_ = 1.0 / FastMath.pow(2.0, (dimension - 1));
        cdiscr_ = 0.0;
        ddiscr_ = 1.0 / FastMath.pow(3.0, (dimension));
    }
}
