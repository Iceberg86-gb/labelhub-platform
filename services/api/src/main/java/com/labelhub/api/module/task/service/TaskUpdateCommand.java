package com.labelhub.api.module.task.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TaskUpdateCommand(
    String title,
    String description,
    String instructionRichText,
    List<String> tags,
    Map<String, Object> rewardRule,
    LocalDateTime deadlineAt
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String title;
        private String description;
        private String instructionRichText;
        private List<String> tags;
        private Map<String, Object> rewardRule;
        private LocalDateTime deadlineAt;

        private Builder() {
        }

        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder instructionRichText(String instructionRichText) { this.instructionRichText = instructionRichText; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }
        public Builder rewardRule(Map<String, Object> rewardRule) { this.rewardRule = rewardRule; return this; }
        public Builder deadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; return this; }

        public TaskUpdateCommand build() {
            return new TaskUpdateCommand(title, description, instructionRichText, tags, rewardRule, deadlineAt);
        }
    }
}
