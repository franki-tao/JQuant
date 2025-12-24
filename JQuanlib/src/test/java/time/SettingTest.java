package time;

import jquant.Settings;
import jquant.time.Date;
import jquant.time.Month;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class SettingTest {
    class Flag implements PropertyChangeListener {
        private boolean up_ = false;
        public void raise() { up_ = true; }
        public void lower() { up_ = false; }
        public boolean isUp() { return up_; }
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            System.out.println("date发生变化，旧值{"+evt.getOldValue()+"}, 新值{"+evt.getNewValue()+"}");
            raise();
        }
    }
    @Test
    public void testNotificationsOnDateChange() {
        System.out.println("Testing notifications on evaluation-date change...");
        Date d1 = new Date(11, Month.FEBRUARY, 2021);
        Date d2 = new Date(12, Month.FEBRUARY, 2021);
        Settings.instance.evaluationDate().equal(d1);
        Flag flag = new Flag();
        // 注册监听
        Settings.instance.evaluationDate().addChangeListener(flag);
        Settings.instance.evaluationDate().equal(d1);
        assertFalse(flag.isUp(), "unexpected notification");
        Settings.instance.evaluationDate().equal(d2);
        assertFalse(!flag.isUp(), "unexpected notification");
    }
}
