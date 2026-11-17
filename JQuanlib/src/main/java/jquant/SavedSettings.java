package jquant;

import jquant.time.Date;

import java.util.Optional;

// helper class to temporarily and safely change the settings
public class SavedSettings implements AutoCloseable {
    private final Date evaluationDate_;
    private final boolean includeReferenceDateEvents_;
    private final Optional<Boolean> includeTodaysCashFlows_;
    private final boolean enforcesTodaysHistoricFixings_;

    public SavedSettings() {
        // Keep the raw value (which may be the null date) so that the
        // "not explicitly set" state is restored as well.
        evaluationDate_ = Settings.instance.evaluationDate().getValue();
        includeReferenceDateEvents_ = Settings.instance.includeReferenceDateEvents();
        includeTodaysCashFlows_ = Settings.instance.includeTodaysCashFlows();
        enforcesTodaysHistoricFixings_ = Settings.instance.enforcesTodaysHistoricFixings();
    }

    public void restore() {
        Settings.instance.evaluationDate().equal(evaluationDate_);
        Settings.instance.setIncludeReferenceDateEvents(includeReferenceDateEvents_);
        Settings.instance.setIncludeTodaysCashFlows(includeTodaysCashFlows_);
        Settings.instance.setEnforcesTodaysHistoricFixings(enforcesTodaysHistoricFixings_);
    }

    @Override
    public void close() {
        restore();
    }
}
