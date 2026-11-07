package com.labelhub.api.module.ai.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ADR-021 §1: submissions that can serve as precedents, with the facts their human verdict derives from.
 * Read-only; one query per review instead of a verdict lookup per candidate.
 */
@Mapper
public interface PrecedentCandidateMapper {

    @Select("""
        SELECT s.id AS submissionId,
               CAST(s.answer_payload AS CHAR) AS answerPayloadJson,
               CAST(di.item_payload AS CHAR) AS datasetItemPayloadJson,
               JSON_UNQUOTE(JSON_EXTRACT(v.payload, '$.verdict')) AS verdict,
               JSON_UNQUOTE(JSON_EXTRACT(v.payload, '$.reviewLevel')) AS reviewLevel,
               (SELECT COUNT(*) FROM senior_review_cases c
                 WHERE c.submission_id = s.id AND c.status IN ('pending_reviewer', 'open')) AS openCases,
               (SELECT c.resolution FROM senior_review_cases c
                 WHERE c.submission_id = s.id AND c.status = 'resolved'
                 ORDER BY c.resolved_at DESC, c.id DESC LIMIT 1) AS latestResolution
        FROM submissions s
        JOIN (
            SELECT ranked.submission_id, ranked.payload
            FROM (
                SELECT qle.submission_id, qle.payload,
                       ROW_NUMBER() OVER (PARTITION BY qle.submission_id ORDER BY qle.created_at DESC, qle.id DESC) AS rn
                FROM quality_ledger_entries qle
                WHERE qle.task_id = #{taskId}
                  AND qle.evidence_type = 'reviewer_overall_verdict'
            ) ranked
            WHERE ranked.rn = 1
        ) v ON v.submission_id = s.id
        LEFT JOIN dataset_items di ON di.id = s.dataset_item_id
        WHERE s.task_id = #{taskId}
          AND s.schema_version_id = #{schemaVersionId}
          AND s.id <> #{submissionId}
          AND s.dataset_item_id <> #{datasetItemId}
          AND s.superseded_by_id IS NULL
        ORDER BY s.id DESC
        LIMIT #{limit}
        """)
    List<CandidateRow> selectCandidates(
        @Param("taskId") Long taskId,
        @Param("schemaVersionId") Long schemaVersionId,
        @Param("submissionId") Long submissionId,
        @Param("datasetItemId") Long datasetItemId,
        @Param("limit") int limit
    );

    /** Mutable row so MyBatis auto-maps it by property name (see AiCallMapper.ShadowStatsRow). */
    class CandidateRow {
        private Long submissionId;
        private String answerPayloadJson;
        private String datasetItemPayloadJson;
        private String verdict;
        private String reviewLevel;
        private Long openCases;
        private String latestResolution;

        public Long getSubmissionId() { return submissionId; }
        public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
        public String getAnswerPayloadJson() { return answerPayloadJson; }
        public void setAnswerPayloadJson(String answerPayloadJson) { this.answerPayloadJson = answerPayloadJson; }
        public String getDatasetItemPayloadJson() { return datasetItemPayloadJson; }
        public void setDatasetItemPayloadJson(String datasetItemPayloadJson) { this.datasetItemPayloadJson = datasetItemPayloadJson; }
        public String getVerdict() { return verdict; }
        public void setVerdict(String verdict) { this.verdict = verdict; }
        public String getReviewLevel() { return reviewLevel; }
        public void setReviewLevel(String reviewLevel) { this.reviewLevel = reviewLevel; }
        public Long getOpenCases() { return openCases; }
        public void setOpenCases(Long openCases) { this.openCases = openCases; }
        public String getLatestResolution() { return latestResolution; }
        public void setLatestResolution(String latestResolution) { this.latestResolution = latestResolution; }
    }
}
