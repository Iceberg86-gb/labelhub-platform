package com.labelhub.api.module.ai.prereview;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiPrereviewStatusService {

    private static final long PROCESSING_LEASE_SECONDS = 60L;

    private final AiPrereviewStatusMapper mapper;
    private final Clock clock;

    public AiPrereviewStatusService(AiPrereviewStatusMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public AiPrereviewSignalsView viewFor(Long submissionId) {
        if (submissionId == null) {
            return defaultView(null);
        }
        return viewsFor(List.of(submissionId)).getOrDefault(submissionId, defaultView(submissionId));
    }

    public Map<Long, AiPrereviewSignalsView> viewsFor(Collection<Long> submissionIds) {
        Map<Long, AiPrereviewSignalsView> result = new LinkedHashMap<>();
        List<Long> ids = submissionIds == null
            ? List.of()
            : submissionIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        ids.forEach(id -> result.put(id, defaultView(id)));
        for (AiPrereviewSignalRow row : mapper.selectSignalsBySubmissionIds(ids)) {
            result.put(row.getSubmissionId(), derive(row));
        }
        return result;
    }

    AiPrereviewSignalsView derive(AiPrereviewSignalRow row) {
        if (row == null) {
            return defaultView(null);
        }
        boolean hasRecommendation = Boolean.TRUE.equals(row.getHasAiOverallRecommendation());
        AiPrereviewSignals signals = new AiPrereviewSignals(
            row.getOutboxStatus(),
            row.getAiCallStatus(),
            hasRecommendation
        );
        return new AiPrereviewSignalsView(row.getSubmissionId(), deriveStatus(row, hasRecommendation), signals);
    }

    public AiPrereviewSignalsView defaultView(Long submissionId) {
        return new AiPrereviewSignalsView(
            submissionId,
            "pending",
            new AiPrereviewSignals(null, null, false)
        );
    }

    private String deriveStatus(AiPrereviewSignalRow row, boolean hasRecommendation) {
        if (hasRecommendation || "completed".equals(row.getAiCallStatus())) {
            return "completed";
        }
        if ("failed".equals(row.getAiCallStatus()) || "dead_letter".equals(row.getOutboxStatus())) {
            return "failed";
        }
        if ("processing".equals(row.getOutboxStatus()) && hasFreshLock(row.getOutboxLockedAt())) {
            return "processing";
        }
        return "pending";
    }

    private boolean hasFreshLock(LocalDateTime lockedAt) {
        return lockedAt != null
            && !lockedAt.isBefore(LocalDateTime.now(clock).minusSeconds(PROCESSING_LEASE_SECONDS));
    }
}
