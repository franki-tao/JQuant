package jquant.methods.finitedifferences.meshers;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.operators.FdmLinearOpLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Mesher which is a composite of one-dimensional meshers.
 *
 * <p>Port of {@code ql/methods/finitedifferences/meshers/fdmmeshercomposite.{hpp,cpp}}.
 */
public class FdmMesherComposite extends FdmMesher {
    private final List<Fdm1dMesher> mesher_;

    public FdmMesherComposite(Fdm1dMesher mesher) {
        this(Collections.singletonList(mesher));
    }

    public FdmMesherComposite(List<Fdm1dMesher> mesher) {
        super(getLayoutFromMeshers(mesher));
        mesher_ = new ArrayList<>(mesher);
    }

    public FdmMesherComposite(FdmLinearOpLayout layout, List<Fdm1dMesher> mesher) {
        super(layout);
        mesher_ = new ArrayList<>(mesher);
        for (int i = 0; i < mesher.size(); ++i) {
            QL_REQUIRE(mesher.get(i).size() == layout.dim()[i],
                    "size of 1d mesher " + i + " does not fit to layout");
        }
    }

    private static FdmLinearOpLayout getLayoutFromMeshers(List<Fdm1dMesher> meshers) {
        final int[] dim = new int[meshers.size()];
        for (int i = 0; i < dim.length; ++i) {
            dim[i] = meshers.get(i).size();
        }
        return new FdmLinearOpLayout(dim);
    }

    @Override
    public double dplus(FdmLinearOpIterator iter, int direction) {
        return mesher_.get(direction).dplus(iter.coordinates()[direction]);
    }

    @Override
    public double dminus(FdmLinearOpIterator iter, int direction) {
        return mesher_.get(direction).dminus(iter.coordinates()[direction]);
    }

    @Override
    public double location(FdmLinearOpIterator iter, int direction) {
        return mesher_.get(direction).location(iter.coordinates()[direction]);
    }

    @Override
    public Array locations(int direction) {
        final Array retVal = new Array(layout_.size());
        for (FdmLinearOpIterator iter : layout_) {
            retVal.set(iter.index(),
                    mesher_.get(direction).locations().get(iter.coordinates()[direction]));
        }
        return retVal;
    }

    public List<Fdm1dMesher> getFdm1dMeshers() {
        return mesher_;
    }
}
