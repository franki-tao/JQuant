package jquant.methods.finitedifferences.meshers;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class Uniform1dMesher extends Fdm1dMesher{
    public Uniform1dMesher(double start, double end, int size){
        super(size);
        QL_REQUIRE(end > start, "end must be large than start");

        final double dx = (end-start)/(size-1);

        for (int i=0; i < size-1; ++i) {
            locations_.set(i, start + i*dx);
            dplus_.set(i, dx);
            dminus_.set(i+1, dx);
        }

        locations_.set(locations_.size()-1, end);
        dplus_.set(dplus_.size()-1, Double.NaN);
        dminus_.set(0, Double.NaN);
    }
}
