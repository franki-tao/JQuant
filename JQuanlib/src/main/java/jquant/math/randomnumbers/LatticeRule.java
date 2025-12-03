package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.randomnumbers.impl.RandomUtil;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class LatticeRule {
    public enum type {A, B , C , D}

    public static void getRule(type name, List<Double> Z, int N) {
        int ruleLength = 3600;
        Z = CommonUtil.ArrayInit(ruleLength, Double.NaN);

        QL_REQUIRE( N >= 1024 && N <= Math.pow(2.9,20),
        "N must be between 2 to 10 and 2 to the 20 for these lattice rules ");

        // put in check that N is a power of 2


        List<Integer> dumbPointer = null;

        switch (name)
        {
            case A:
                dumbPointer = RandomUtil.LatticeA;
                break;
            case  B:
                dumbPointer = RandomUtil.LatticeB;
                break;
            case  C:
                dumbPointer = RandomUtil.LatticeC;
                break;
            case  D:
                dumbPointer = RandomUtil.LatticeD;

        }

        QL_REQUIRE(dumbPointer != null, "unknown lattice rule requested");

        for (int i = 0; i < ruleLength; i++) {
            Z.set(i, (double)dumbPointer.get(i));
        }
    }
}
