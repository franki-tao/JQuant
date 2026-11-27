package jquant.methods.finitedifferences.utilities;

import jquant.math.Array;
import jquant.math.integrals.DiscreteSimpsonIntegral;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesher based integral over a target function.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/fdmmesherintegral.{hpp,cpp}}.
 */
public class FdmMesherIntegral {

    @FunctionalInterface
    public interface Integrator1D {
        double integrate(Array x, Array f);
    }

    private final List<Fdm1dMesher> meshers_;
    private final Integrator1D integrator1d_;

    public FdmMesherIntegral(final FdmMesherComposite mesher,
                             final Integrator1D integrator1d) {
        meshers_ = new ArrayList<>(mesher.getFdm1dMeshers());
        integrator1d_ = integrator1d;
    }

    public FdmMesherIntegral(final FdmMesherComposite mesher,
                             final DiscreteSimpsonIntegral integrator) {
        this(mesher, (x, f) -> integrator.value(x.getList(), f.getList()));
    }

    public double integrate(final Array f) {
        final Fdm1dMesher last = meshers_.get(meshers_.size() - 1);
        final double[] x = new double[last.size()];
        for (int i = 0; i < x.length; ++i)
            x[i] = last.location(i);

        if (meshers_.size() == 1) {
            return integrator1d_.integrate(new Array(x), f);
        }

        final FdmMesherComposite subMesher = new FdmMesherComposite(
                meshers_.subList(0, meshers_.size() - 1));
        final FdmMesherIntegral subMesherIntegral =
                new FdmMesherIntegral(subMesher, integrator1d_);
        final int subSize = subMesher.layout().size();

        final double[] g = new double[x.length];
        final double[] fSub = new double[subSize];
        final double[] fAll = f.toArray();

        for (int i = 0; i < x.length; ++i) {
            System.arraycopy(fAll, i * subSize, fSub, 0, subSize);
            g[i] = subMesherIntegral.integrate(new Array(fSub));
        }

        return integrator1d_.integrate(new Array(x), new Array(g));
    }
}
