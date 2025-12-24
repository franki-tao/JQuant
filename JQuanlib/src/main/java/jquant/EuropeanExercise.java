package jquant;

import jquant.time.Date;

import java.util.Collections;

//! European exercise
/*! A European option can only be exercised at one (expiry) date.
 */
public class EuropeanExercise extends Exercise {
    public EuropeanExercise(final Date date) {
        super(Type.European);
        dates_ = Collections.singletonList(date);
    }
}
