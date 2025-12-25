package time;

import jquant.time.Calendar;
import time.impl.TargetImpl;

public class Target extends Calendar {
    public Target() {
        super();
        impl_ = new TargetImpl();
    }
}
