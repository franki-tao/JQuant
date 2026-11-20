package jquant;

/**
 * Choice of time range for partial-time barrier options.
 *
 * <p>Ported from QuantLib {@code ql/instruments/partialtimebarrieroption.hpp}.
 *
 * <p>The official enum values are explicit: {@code Start = 0},
 * {@code EndB1 = 2} and {@code EndB2 = 3}. There is no value 1; in
 * particular, the Java ordinals (0, 1, 2) must not be mistaken for the
 * official values.
 */
public class PartialBarrier {

    /** Time range monitored by a partial-time barrier option. */
    public enum Range {
        /** Monitor from the start of the option lifetime until the cover
         *  event. */
        Start(0),
        /** Monitor from the cover event to the exercise date; trigger a
         *  knock-out only if the barrier is hit or crossed from either
         *  side, regardless of the underlying value when monitoring
         *  starts. */
        EndB1(2),
        /** Monitor from the cover event to the exercise date; immediately
         *  trigger a knock-out if the underlying value is on the wrong
         *  side of the barrier when monitoring starts. */
        EndB2(3);

        private final int value;

        Range(int value) {
            this.value = value;
        }

        /** Official integer value of this range. */
        public int value() {
            return value;
        }
    }
}
