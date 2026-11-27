package jquant.methods.finitedifferences.operators;

import jquant.methods.finitedifferences.meshers.FdmMesher;

/**
 * Modifiable triple-band linear operator.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/operators/modtriplebandlinearop.hpp}.
 * The official class only exposes mutable accessors for the three bands of
 * a {@link TripleBandLinearOp}.
 */
public class ModTripleBandLinearOp extends TripleBandLinearOp {

    public ModTripleBandLinearOp(int direction, FdmMesher mesher) {
        super(direction, mesher);
    }

    public ModTripleBandLinearOp(final TripleBandLinearOp m) {
        super(m);
    }

    public double lower(int i) {
        return lower_[i];
    }

    public void setLower(int i, double value) {
        lower_[i] = value;
    }

    public double diag(int i) {
        return diag_[i];
    }

    public void setDiag(int i, double value) {
        diag_[i] = value;
    }

    public double upper(int i) {
        return upper_[i];
    }

    public void setUpper(int i, double value) {
        upper_[i] = value;
    }
}
