package com.labelhub.api.module.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "drafts", autoResultMap = true)
public class DraftEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer revisionNo;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> draftPayload;
    private LocalDateTime savedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Map<String, Object> getDraftPayload() { return draftPayload; }
    public void setDraftPayload(Map<String, Object> draftPayload) { this.draftPayload = draftPayload; }
    public LocalDateTime getSavedAt() { return savedAt; }
    public void setSavedAt(LocalDateTime savedAt) { this.savedAt = savedAt; }
}
