package com.labelhub.api.module.schema.service.view;

import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import java.util.Map;

public class SubmissionRenderSchemaView {

    private Long submissionId;
    private SchemaVersionEntity schemaVersion;
    private Map<String, Object> answerPayload;
    private Map<String, Object> provenance;

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public SchemaVersionEntity getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(SchemaVersionEntity schemaVersion) { this.schemaVersion = schemaVersion; }
    public Map<String, Object> getAnswerPayload() { return answerPayload; }
    public void setAnswerPayload(Map<String, Object> answerPayload) { this.answerPayload = answerPayload; }
    public Map<String, Object> getProvenance() { return provenance; }
    public void setProvenance(Map<String, Object> provenance) { this.provenance = provenance; }
}
