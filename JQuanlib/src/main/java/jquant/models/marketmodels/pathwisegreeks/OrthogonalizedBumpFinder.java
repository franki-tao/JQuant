package jquant.models.marketmodels.pathwisegreeks;

import jquant.math.Matrix;
import jquant.math.matrixutilities.OrthogonalProjections;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/bumpinstrumentjacobian.hpp/.cpp}
 * port of {@code OrthogonalizedBumpFinder}.
 *
 * <p>Pass in a market model, a list of instruments, and possible bumps. Get
 * out pseudo-root bumps that shift each implied vol by one percent, and leave
 * the other instruments fixed.
 *
 * <p>If the contribution of an instrument is too correlated with other
 * instruments used, discard it.
 */
public class OrthogonalizedBumpFinder {

    private final VolatilityBumpInstrumentJacobian derivativesProducer_;
    private final double multiplierCutOff_;
    private final double tolerance_;

    public OrthogonalizedBumpFinder(final VegaBumpCollection bumps,
                                    final List<VolatilityBumpInstrumentJacobian.Swaption> swaptions,
                                    final List<VolatilityBumpInstrumentJacobian.Cap> caps,
                                    double multiplierCutOff,
                                    double tolerance) {
        derivativesProducer_ =
                new VolatilityBumpInstrumentJacobian(bumps, swaptions, caps);
        multiplierCutOff_ = multiplierCutOff;
        tolerance_ = tolerance;
    }

    /**
     * This is precisely the vector to pass into
     * {@code PathwiseVegasAccountingEngine}.
     *
     * <p>The bumps are the outermost vector by time step and the inner one by
     * which vega.
     */
    public void GetVegaBumps(List<List<Matrix>> theBumps) {
        OrthogonalProjections projector =
                new OrthogonalProjections(
                        derivativesProducer_.getAllOnePercentBumps(),
                        multiplierCutOff_,
                        tolerance_);

        int numberRestrictedBumps = projector.numberValidVectors();

        MarketModel marketmodel =
                derivativesProducer_.getInputBumps().associatedModel();
        final EvolutionDescription evolution = marketmodel.evolution();

        int numberSteps = evolution.numberOfSteps();
        int numberRates = evolution.numberOfRates();
        int factors = marketmodel.numberOfFactors();

        theBumps.clear();

        Matrix modelMatrix = new Matrix(numberRates, factors, 0.0);

        for (int i = 0; i < numberSteps; ++i) {
            List<Matrix> stepBumps = new ArrayList<>();
            for (int j = 0; j < numberRestrictedBumps; ++j)
                stepBumps.add(copyMatrix(modelMatrix));
            theBumps.add(stepBumps);
        }

        final List<VegaBumpCluster> bumpClusters =
                derivativesProducer_.getInputBumps().allBumps();

        int bumpIndex = 0;

        for (int instrument = 0;
             instrument < projector.validVectors().size(); ++instrument) {
            if (projector.validVectors().get(instrument)) {
                for (int cluster = 0; cluster < bumpClusters.size(); ++cluster) {
                    double magnitude = projector.GetVector(instrument).get(cluster);

                    for (int step = bumpClusters.get(cluster).stepBegin();
                         step < bumpClusters.get(cluster).stepEnd(); ++step)
                        for (int rate = bumpClusters.get(cluster).rateBegin();
                             rate < bumpClusters.get(cluster).rateEnd(); ++rate)
                            for (int factor = bumpClusters.get(cluster).factorBegin();
                                 factor < bumpClusters.get(cluster).factorEnd(); ++factor)
                                theBumps.get(step).get(bumpIndex).set(rate, factor,
                                        magnitude);
                }

                ++bumpIndex;
            }
        }
    }

    private static Matrix copyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }
}
