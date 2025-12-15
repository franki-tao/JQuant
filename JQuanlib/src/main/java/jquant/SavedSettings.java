package jquant;

import jquant.time.Date;

import java.util.Optional;

// helper class to temporarily and safely change the settings
public class SavedSettings {
    private Date evaluationDate_;
    private boolean includeReferenceDateEvents_;
    private Optional<Boolean> includeTodaysCashFlows_;
    private boolean enforcesTodaysHistoricFixings_;

    public SavedSettings() {
        evaluationDate_ = Settings.instance.evaluationDate().Date();
        includeReferenceDateEvents_ = Settings.instance.includeReferenceDateEvents();
        includeTodaysCashFlows_ = Settings.instance.includeTodaysCashFlows();
        enforcesTodaysHistoricFixings_ = Settings.instance.enforcesTodaysHistoricFixings();
    }
}
