package jquant;

import jquant.impl.HandleImpl;
import jquant.patterns.Observable;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Shared handle to an observable
/*! All copies of an instance of this class refer to the same
    observable by means of a relinkable smart pointer. When such
    pointer is relinked to another observable, the change will be
    propagated to all the copies.

    \pre Class T must inherit from Observable
*/
public class Handle <T extends Observable & HandleImpl> {
    protected Link<T> link_;
    /*! \name Constructors
        \warning <tt>registerAsObserver</tt> is left as a backdoor
                 in case the programmer cannot guarantee that the
                 object pointed to will remain alive for the whole
                 lifetime of the handle---namely, it should be set
                 to <tt>false</tt> when the passed shared pointer
                 does not own the pointee (this should only happen
                 in a controlled environment, so that the
                 programmer is aware of it). Failure to do so can
                 very likely result in a program crash.  If the
                 programmer does want the handle to register as
                 observer of such a shared pointer, it is his
                 responsibility to ensure that the handle gets
                 destroyed before the pointed object does.
    */
    //@{
    //registerAsObserver = true, 此处p支持引用与final要加以区分
    public Handle(T p, boolean registerAsObserver) {
        link_ = new Link<>(p, registerAsObserver);
    }
    //@}
    //! dereferencing
    // 与->, *一致
    public final T currentLink() {
        QL_REQUIRE(!empty(), "empty Handle cannot be dereferenced");
        return link_.currentLink();
    }
    //! checks if the contained shared pointer points to anything
    public boolean empty() {
        return link_.empty();
    }
    //! allows registration as observable
    public Observable get() {
        return link_;
    }
    public T getValue() {
        return link_.currentLink();
    }
}
