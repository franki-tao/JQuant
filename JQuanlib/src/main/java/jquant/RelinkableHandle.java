package jquant;

import jquant.impl.HandleImpl;
import jquant.patterns.Observable;

//! Relinkable handle to an observable
/*! An instance of this class can be relinked so that it points to
    another observable. The change will be propagated to all
    handles that were created as copies of such instance.

    \pre Class T must inherit from Observable

    \warning see the Handle documentation for issues
             relatives to <tt>registerAsObserver</tt>.
*/
public class RelinkableHandle <T extends Observable & HandleImpl> extends Handle<T> {
    public RelinkableHandle(T p, boolean registerAsObserver) {
        super(p, registerAsObserver);
    }
    /*! \deprecated Use one of the constructors taking shared_ptr.
                    Deprecated in version 1.35.
    */
    public void linkTo(final T h, boolean registerAsObserver) {
        this.link_.linkTo(h, registerAsObserver);
    }
    public void reset() {
        this.link_.linkTo(null, true);
    }
}
