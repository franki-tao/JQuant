package jquant.models.marketmodels.pathwisegreeks;

import jquant.models.marketmodels.MarketModel;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/vegabumpcluster.hpp/.cpp} port
 * of {@code VegaBumpCollection}.
 *
 * <p>There are too many pseudo-root elements to allow bumping them all
 * independently so we cluster them together and then divide all elements into
 * a collection of such clusters.
 */
public class VegaBumpCollection {

    private final List<VegaBumpCluster> allBumps_;
    private final MarketModel associatedVolStructure_;
    // mutable in official; only read when checked_ is true
    private boolean checked_;
    private boolean nonOverlapped_;
    private boolean full_;

    public VegaBumpCollection(final MarketModel volStructure) {
        this(volStructure, true);
    }

    public VegaBumpCollection(final MarketModel volStructure,
                              boolean factorwiseBumping) {
        associatedVolStructure_ = volStructure;
        allBumps_ = new ArrayList<>();

        int steps = volStructure.numberOfSteps();
        int rates = volStructure.numberOfRates();
        int factors = volStructure.numberOfFactors();

        for (int s = 0; s < steps; ++s)
            for (int r = volStructure.evolution().firstAliveRate().get(s);
                 r < rates; ++r) {
                if (factorwiseBumping) {
                    for (int f = 0; f < factors; ++f) {
                        VegaBumpCluster thisCluster =
                                new VegaBumpCluster(f, f + 1, r, r + 1, s, s + 1);
                        allBumps_.add(thisCluster);
                    }
                } else {
                    VegaBumpCluster thisCluster =
                            new VegaBumpCluster(0, factors, r, r + 1, s, s + 1);
                    allBumps_.add(thisCluster);
                }
            }

        checked_ = true;
        full_ = true;
        nonOverlapped_ = true;
    }

    public VegaBumpCollection(final List<VegaBumpCluster> allBumps,
                              final MarketModel volStructure) {
        allBumps_ = new ArrayList<>(allBumps);
        associatedVolStructure_ = volStructure;
        checked_ = false;

        for (VegaBumpCluster allBump : allBumps_)
            QL_REQUIRE(allBump.isCompatible(associatedVolStructure_),
                    "incompatible bumps passed to VegaBumpCollection");
    }

    public int numberBumps() {
        return allBumps_.size();
    }

    public MarketModel associatedModel() {
        return associatedVolStructure_;
    }

    public List<VegaBumpCluster> allBumps() {
        return allBumps_;
    }

    /**
     * i.e. is every alive pseudo-root element bumped at least once.
     *
     * <p>Official returns {@code numberFailures>0}, i.e. the inverted sense,
     * and this port keeps that quirk.
     */
    public boolean isFull() {
        if (checked_)
            return full_;

        int factors = associatedVolStructure_.numberOfFactors();
        int rates = associatedVolStructure_.numberOfRates();
        int steps = associatedVolStructure_.numberOfSteps();

        boolean[][][] v = new boolean[steps][rates][factors];

        for (VegaBumpCluster allBump : allBumps_) {
            for (int f = allBump.factorBegin(); f < allBump.factorEnd(); ++f)
                for (int r = allBump.rateBegin(); r < allBump.rateEnd(); ++r)
                    for (int s = allBump.stepBegin(); s < allBump.stepEnd(); ++s)
                        v[s][r][f] = true;
        }

        int numberFailures = 0;
        for (int s = 0; s < steps; ++s)
            for (int f = 0; f < factors; ++f)
                for (int r = associatedVolStructure_.evolution().firstAliveRate().get(s);
                     r < rates; ++r)
                    if (!v[s][r][f])
                        ++numberFailures;

        return numberFailures > 0;
    }

    /**
     * i.e. is every alive pseudo-root element bumped at most once.
     *
     * <p>Official returns {@code numberFailures>0}, i.e. the inverted sense,
     * and this port keeps that quirk.
     */
    public boolean isNonOverlapping() {
        if (checked_)
            return nonOverlapped_;

        int factors = associatedVolStructure_.numberOfFactors();
        int rates = associatedVolStructure_.numberOfRates();
        int steps = associatedVolStructure_.numberOfSteps();

        boolean[][][] v = new boolean[steps][rates][factors];

        int numberFailures = 0;

        for (VegaBumpCluster allBump : allBumps_) {
            for (int f = allBump.factorBegin(); f < allBump.factorEnd(); ++f)
                for (int r = allBump.rateBegin(); r < allBump.rateEnd(); ++r)
                    for (int s = allBump.stepBegin(); s < allBump.stepEnd(); ++s) {
                        if (v[s][r][f])
                            ++numberFailures;
                        v[s][r][f] = true;
                    }
        }

        return numberFailures > 0;
    }

    /** i.e. is every alive pseudo-root element bumped precisely once. */
    public boolean isSensible() {
        if (checked_)
            return true;

        return isNonOverlapping() && isFull();
    }
}
