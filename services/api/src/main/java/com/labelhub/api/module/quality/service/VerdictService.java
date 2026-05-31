package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.service.view.VerdictView;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class VerdictService {

    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;
    private final Clock clock;

    public VerdictService(
        SubmissionMapper submissionMapper,
        TaskMapper taskMapper,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        Clock clock
    ) {
        this.submissionMapper = submissionMapper;
        this.taskMapper = taskMapper;
        this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
        this.clock = clock;
    }

    public VerdictView deriveCurrentVerdict(Long submissionId, Long requesterUserId, Set<String> requesterRoles) {
        requireReadableSubmission(submissionId, requesterUserId, requesterRoles);
        QualityLedgerEntryEntity latest = qualityLedgerEntryMapper.selectLatestReviewerOverallVerdict(submissionId);
        if (latest == null) {
            return new VerdictView(submissionId, "pending", null, LocalDateTime.now(clock));
        }
        return new VerdictView(
            submissionId,
            statusFromPayload(latest.getPayload()),
            latest.getId(),
            LocalDateTime.now(clock)
        );
    }

    private void requireReadableSubmission(Long submissionId, Long requesterUserId, Set<String> requesterRoles) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        if (!hasReadAccess(submission, requesterUserId, requesterRoles)) {
            throw new SubmissionNotFoundException(submissionId);
        }
    }

    private boolean hasReadAccess(SubmissionEntity submission, Long requesterUserId, Set<String> requesterRoles) {
        if (Objects.equals(submission.getLabelerId(), requesterUserId)) {
            return true;
        }
        if (hasRole(requesterRoles, "REVIEWER")) {
            return true;
        }
        TaskEntity task = taskMapper.selectById(submission.getTaskId());
        return task != null && Objects.equals(task.getOwnerId(), requesterUserId);
    }

    private boolean hasRole(Set<String> roles, String role) {
        return roles != null && (roles.contains(role) || roles.contains("ROLE_" + role));
    }

    private String statusFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return "pending";
        }
        Object verdict = payload.get("verdict");
        if ("approve".equals(verdict)) {
            return ReviewLevels.SENIOR_REVIEWER.equals(payload.get("reviewLevel")) ? "approved" : "pending";
        }
        if ("reject".equals(verdict)) {
            return "rejected";
        }
        return "pending";
    }
}
