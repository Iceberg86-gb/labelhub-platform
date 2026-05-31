package com.labelhub.api.module.quality.service;

final class ReviewLevels {
    static final String REVIEWER = "reviewer";
    static final String SENIOR_REVIEWER = "senior_reviewer";

    private ReviewLevels() {
    }

    static String normalize(String value) {
        if (SENIOR_REVIEWER.equals(value)) {
            return SENIOR_REVIEWER;
        }
        return REVIEWER;
    }

    static boolean isValid(String value) {
        return REVIEWER.equals(value) || SENIOR_REVIEWER.equals(value);
    }
}
