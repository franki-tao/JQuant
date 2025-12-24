package jquant;

import jquant.time.Date;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Base exercise class
public abstract class Exercise {
    public enum Type {
        American, Bermudan, European
    }
    protected Type type_;
    protected List<Date> dates_;
    public Exercise(Type type) {
        type_ = type;
    }
    // inspectors
    public Type type() { return type_; }
    public Date date(int index) { return dates_.get(index); }
    public Date dateAt(int index) { return dates_.get(index); }
    //! Returns all exercise dates
    public final List<Date> dates() { return dates_; }
    public Date lastDate() {
        QL_REQUIRE(!dates_.isEmpty(), "no exercise date given");
        return dates_.get(dates_.size() - 1);
    }
}
