package jquant.models;

import jquant.Handle;
import jquant.patterns.Observable;
import jquant.termstructures.YieldTermStructure;

//! Term-structure consistent model class
/*! This is a base class for models that can reprice exactly
    any discount bond.

    \ingroup shortrate
*/
/*
谁用谁实现
class TermStructureConsistentModel : public virtual Observable {
  public:
    TermStructureConsistentModel(Handle<YieldTermStructure> termStructure)
    : termStructure_(std::move(termStructure)) {}
    const Handle<YieldTermStructure>& termStructure() const {
        return termStructure_;
    }
  private:
    Handle<YieldTermStructure> termStructure_;
};
 */
public interface TermStructureConsistentModel extends Observable {
    Handle<YieldTermStructure> termStructure();
}
