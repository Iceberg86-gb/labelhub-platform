package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.entity.SeniorReviewCaseEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.mapper.SeniorReviewCaseMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VerdictService {

    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;
    private final SeniorReviewCaseMapper seniorReviewCaseMapper;
    private final Clock clock;

    @Autowired
    public VerdictService(
        SubmissionMapper submissionMapper,
        TaskMapper taskMapper,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        SeniorReviewCaseMapper seniorReviewCaseMapper,
        Clock clock
    ) {
        this.submissionMapper = submissionMapper;
        this.taskMapper = taskMapper;
        this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
        this.seniorReviewCaseMapper = seniorReviewCaseMapper;
        this.clock = clock;
    }

    public VerdictService(
        SubmissionMapper submissionMapper,
        TaskMapper taskMapper,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        Clock clock
    ) {
        this(submissionMapper, taskMapper, qualityLedgerEntryMapper, null, clock);
    }

    public VerdictView deriveCurrentVerdict(Long submissionId, Long requesterUserId, Set<String> requesterRoles) {
        requireReadableSubmission(submissionId, requesterUserId, requesterRoles);
        QualityLedgerEntryEntity latest = qualityLedgerEntryMapper.selectLatestReviewerOverallVerdict(submissionId);
        if (latest == null) {
            return new VerdictView(submissionId, "pending", null, LocalDateTime.now(clock));
        }
        return new VerdictView(
            submissionId,
            statusFromLatestEntry(submissionId, latest),
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
        if (hasRole(requesterRoles, "REVIEWER") || hasRole(requesterRoles, "SENIOR_REVIEWER")) {
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

    private String statusFromLatestEntry(Long submissionId, QualityLedgerEntryEntity latest) {
        Map<String, Object> payload = latest.getPayload();
        if (payload == null) {
            return "pending";
        }
        Object verdict = payload.get("verdict");
        if ("reject".equals(verdict)) {
            return "rejected";
        }
        if (!"approve".equals(verdict)) {
            return "pending";
        }
        if (ReviewLevels.SENIOR_REVIEWER.equals(payload.get("reviewLevel"))) {
            return "approved";
        }
        if (seniorReviewCaseMapper == null) {
            return "approved";
        }
        Long openCount = seniorReviewCaseMapper.selectOpenCountBySubmissionId(submissionId);
        if (openCount != null && openCount > 0) {
            return "pending";
        }
        SeniorReviewCaseEntity resolved = seniorReviewCaseMapper.selectLatestResolvedBySubmissionId(submissionId);
        if (resolved != null && seniorResolutionRejects(resolved.getResolution())) {
            return "rejected";
        }
        return "approved";
    }

    private boolean seniorResolutionRejects(String resolution) {
        return SeniorReviewCaseService.RESOLUTION_OVERTURN_TO_REJECT.equals(resolution)
            || SeniorReviewCaseService.RESOLUTION_BOUNDARY_REJECTED.equals(resolution);
    }
}
