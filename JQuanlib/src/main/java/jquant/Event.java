package jquant;

import jquant.patterns.Observable;
import jquant.patterns.Visitor;
import jquant.time.Date;

import java.util.Objects;
import java.util.Optional;

//! Base class for event
/*! This class acts as a base class for the actual
    event implementations.
*/
// 注意如果单继承Event， 需要实现Observable里面所有
public interface Event extends Observable {
    //! \name Event interface
    //@{
    //! returns the date at which the event occurs
    Date date();
    //! returns true if an event has already occurred before a date
    /*! If includeRefDate is true, then an event has not occurred if its
        date is the same as the refDate, i.e. this method returns false if
        the event date is the same as the refDate.
    */
    boolean hasOccurred(final Date refDate, Optional<Boolean> includeRefDate);
    //@}

    //! \name Visitability
    //@{
    void accept(Visitor<Objects> v);
}
