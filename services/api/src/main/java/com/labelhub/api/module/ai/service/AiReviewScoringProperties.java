package com.labelhub.api.module.ai.service;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "labelhub.ai.scoring")
public class AiReviewScoringProperties {

    private BigDecimal defaultThreshold = new BigDecimal("0.80");
    private BigDecimal rejectFloor = new BigDecimal("0.20");
    private String scoringRuleVersion = "equal-weight-three-zone-v2";

    public BigDecimal getDefaultThreshold() {
        return defaultThreshold;
    }

    public void setDefaultThreshold(BigDecimal defaultThreshold) {
        this.defaultThreshold = defaultThreshold;
    }

    public BigDecimal getRejectFloor() {
        return rejectFloor;
    }

    public void setRejectFloor(BigDecimal rejectFloor) {
        this.rejectFloor = rejectFloor;
    }

    public String getScoringRuleVersion() {
        return scoringRuleVersion;
    }

    public void setScoringRuleVersion(String scoringRuleVersion) {
        this.scoringRuleVersion = scoringRuleVersion;
    }
}
