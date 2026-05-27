package com.labelhub.api.module.task.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskDeletionMapper {

    @Update("UPDATE tasks SET current_dataset_id = NULL, updated_at = NOW(3) WHERE id = #{taskId}")
    int clearTaskCurrentDataset(@Param("taskId") Long taskId);
    @Update("UPDATE tasks SET current_schema_version_id = NULL, updated_at = NOW(3) WHERE id = #{taskId}")
    int clearTaskCurrentSchemaVersion(@Param("taskId") Long taskId);
    @Update("UPDATE label_schemas SET current_version_id = NULL WHERE task_id = #{taskId}")
    int clearLabelSchemaCurrentVersions(@Param("taskId") Long taskId);
    @Update("UPDATE submissions SET superseded_by_id = NULL WHERE task_id = #{taskId}")
    int clearSubmissionSupersededBy(@Param("taskId") Long taskId);
    @Delete("""
        DELETE acif
        FROM ai_calls_in_field acif
        JOIN submissions s ON s.id = acif.submission_id
        WHERE s.task_id = #{taskId}
        """)
    int deleteAiCallsInField(@Param("taskId") Long taskId);
    @Delete("DELETE FROM current_verdicts WHERE task_id = #{taskId}")
    int deleteCurrentVerdicts(@Param("taskId") Long taskId);
    @Delete("DELETE FROM review_actions WHERE task_id = #{taskId}")
    int deleteReviewActions(@Param("taskId") Long taskId);
    @Delete("DELETE FROM export_snapshots WHERE task_id = #{taskId}")
    int deleteExportSnapshots(@Param("taskId") Long taskId);
    @Delete("DELETE FROM quality_ledger_entries WHERE task_id = #{taskId}")
    int deleteQualityLedgerEntries(@Param("taskId") Long taskId);
    @Delete("""
        DELETE ac
        FROM ai_calls ac
        JOIN submissions s ON s.id = ac.submission_id
        WHERE s.task_id = #{taskId}
        """)
    int deleteAiCalls(@Param("taskId") Long taskId);
    @Delete("""
        DELETE d
        FROM drafts d
        JOIN sessions s ON s.id = d.session_id
        WHERE s.task_id = #{taskId}
        """)
    int deleteDrafts(@Param("taskId") Long taskId);
    @Delete("DELETE FROM submissions WHERE task_id = #{taskId}")
    int deleteSubmissions(@Param("taskId") Long taskId);
    @Delete("DELETE FROM sessions WHERE task_id = #{taskId}")
    int deleteSessions(@Param("taskId") Long taskId);
    @Delete("DELETE FROM dataset_items WHERE task_id = #{taskId}")
    int deleteDatasetItems(@Param("taskId") Long taskId);
    @Delete("DELETE FROM export_jobs WHERE task_id = #{taskId}")
    int deleteExportJobs(@Param("taskId") Long taskId);
    @Delete("DELETE FROM adjudication_rules WHERE task_id = #{taskId}")
    int deleteAdjudicationRules(@Param("taskId") Long taskId);
    @Delete("DELETE FROM datasets WHERE task_id = #{taskId}")
    int deleteDatasets(@Param("taskId") Long taskId);
    @Delete("""
        DELETE sv
        FROM schema_versions sv
        JOIN label_schemas ls ON ls.id = sv.schema_id
        WHERE ls.task_id = #{taskId}
        """)
    int deleteSchemaVersions(@Param("taskId") Long taskId);
    @Delete("DELETE FROM label_schemas WHERE task_id = #{taskId}")
    int deleteLabelSchemas(@Param("taskId") Long taskId);
    @Delete("DELETE FROM task_transitions WHERE task_id = #{taskId}")
    int deleteTaskTransitions(@Param("taskId") Long taskId);
    @Delete("DELETE FROM tasks WHERE id = #{taskId}")
    int deleteTask(@Param("taskId") Long taskId);
}
