package com.labelhub.api.module.agentaccess.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** ADR-022 §3 list_submissions: read-only queries over submissions for MCP tools. */
@Mapper
public interface McpSubmissionQueryMapper {

    @Select("""
        SELECT s.id AS submissionId, s.dataset_item_id AS datasetItemId, s.created_at AS createdAt
        FROM submissions s
        WHERE s.task_id = #{taskId}
          AND s.superseded_by_id IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ai_calls ac
              WHERE ac.submission_id = s.id AND ac.purpose = 'submission_review' AND ac.status = 'completed')
        ORDER BY s.id ASC
        LIMIT #{limit}
        """)
    List<OpenSubmissionRow> selectOpenSubmissions(@Param("taskId") Long taskId, @Param("limit") int limit);

    @Select("SELECT task_id FROM submissions WHERE id = #{submissionId}")
    Long selectTaskIdBySubmissionId(@Param("submissionId") Long submissionId);

    @Select("""
        SELECT id FROM ai_calls
        WHERE idempotency_key = #{idempotencyKey} AND submission_id = #{submissionId}
        """)
    Long selectAiCallIdByIdempotencyKey(@Param("submissionId") Long submissionId, @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT model_provider FROM ai_calls WHERE id = #{aiCallId}")
    String selectAiCallProvider(@Param("aiCallId") Long aiCallId);

    class OpenSubmissionRow {
        private Long submissionId;
        private Long datasetItemId;
        private LocalDateTime createdAt;

        public Long getSubmissionId() { return submissionId; }
        public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
        public Long getDatasetItemId() { return datasetItemId; }
        public void setDatasetItemId(Long datasetItemId) { this.datasetItemId = datasetItemId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
