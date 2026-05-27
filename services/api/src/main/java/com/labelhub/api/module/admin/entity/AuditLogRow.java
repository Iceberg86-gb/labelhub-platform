package com.labelhub.api.module.admin.entity;

public class AuditLogRow extends AuditLogEntity {

    private String actorDisplayName;

    public String getActorDisplayName() { return actorDisplayName; }
    public void setActorDisplayName(String actorDisplayName) { this.actorDisplayName = actorDisplayName; }
}
