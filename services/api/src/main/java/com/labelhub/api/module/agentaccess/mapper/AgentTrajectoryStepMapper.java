package com.labelhub.api.module.agentaccess.mapper;

import com.labelhub.api.module.agentaccess.entity.AgentTrajectoryStepEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** ADR-022 §4, conventions §4: agent_trajectory_steps is append-only — insert and select only. */
@Mapper
public interface AgentTrajectoryStepMapper {

    String COLUMNS = """
        id, run_id, step_no, task_id, submission_id, token_id, client_name, client_version, tool_name, input_hash,
        input_summary, output_hash, output_summary, status, policy_decision, ai_call_id, latency_ms, created_at""";

    @Insert("""
        INSERT INTO agent_trajectory_steps(run_id, step_no, task_id, submission_id, token_id, client_name, client_version,
            tool_name, input_hash, input_summary, output_hash, output_summary, status, policy_decision, ai_call_id,
            latency_ms, created_at)
        VALUES (#{runId}, #{stepNo}, #{taskId}, #{submissionId}, #{tokenId}, #{clientName}, #{clientVersion},
            #{toolName}, #{inputHash}, #{inputSummary}, #{outputHash}, #{outputSummary}, #{status}, #{policyDecision},
            #{aiCallId}, #{latencyMs}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentTrajectoryStepEntity step);

    @Select("SELECT " + COLUMNS + " FROM agent_trajectory_steps WHERE run_id = #{runId} ORDER BY step_no ASC")
    List<AgentTrajectoryStepEntity> selectByRunId(@Param("runId") String runId);

    @Select("SELECT " + COLUMNS + " FROM agent_trajectory_steps WHERE run_id = #{runId} ORDER BY step_no ASC LIMIT 1")
    AgentTrajectoryStepEntity selectFirstByRunId(@Param("runId") String runId);

    @Select("""
        SELECT s.run_id AS runId, MIN(s.token_id) AS tokenId, MAX(t.name) AS tokenName,
               MIN(s.client_name) AS clientName, MIN(s.client_version) AS clientVersion,
               COUNT(*) AS steps,
               SUM(CASE WHEN s.policy_decision = 'proposal_recorded' THEN 1 ELSE 0 END) AS proposals,
               MIN(s.created_at) AS startedAt, MAX(s.created_at) AS lastStepAt,
               SUBSTRING_INDEX(GROUP_CONCAT(s.status ORDER BY s.step_no DESC), ',', 1) AS lastStatus
        FROM agent_trajectory_steps s
        JOIN mcp_access_tokens t ON t.id = s.token_id
        WHERE s.task_id = #{taskId}
        GROUP BY s.run_id
        ORDER BY lastStepAt DESC, s.run_id
        LIMIT 200
        """)
    List<RunSummaryRow> selectRunSummariesByTaskId(@Param("taskId") Long taskId);

    /** Mutable row so MyBatis auto-maps it by property name (see AiCallMapper.ShadowStatsRow). */
    class RunSummaryRow {
        private String runId;
        private Long tokenId;
        private String tokenName;
        private String clientName;
        private String clientVersion;
        private Long steps;
        private Long proposals;
        private java.time.LocalDateTime startedAt;
        private java.time.LocalDateTime lastStepAt;
        private String lastStatus;

        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }
        public Long getTokenId() { return tokenId; }
        public void setTokenId(Long tokenId) { this.tokenId = tokenId; }
        public String getTokenName() { return tokenName; }
        public void setTokenName(String tokenName) { this.tokenName = tokenName; }
        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }
        public String getClientVersion() { return clientVersion; }
        public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }
        public Long getSteps() { return steps; }
        public void setSteps(Long steps) { this.steps = steps; }
        public Long getProposals() { return proposals; }
        public void setProposals(Long proposals) { this.proposals = proposals; }
        public java.time.LocalDateTime getStartedAt() { return startedAt; }
        public void setStartedAt(java.time.LocalDateTime startedAt) { this.startedAt = startedAt; }
        public java.time.LocalDateTime getLastStepAt() { return lastStepAt; }
        public void setLastStepAt(java.time.LocalDateTime lastStepAt) { this.lastStepAt = lastStepAt; }
        public String getLastStatus() { return lastStatus; }
        public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    }
}
