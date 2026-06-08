package com.labelhub.api.module.session.service.view;

import com.labelhub.api.module.session.entity.SessionEntity;
import java.util.List;

public record ClaimBatchResultView(int requestedSize, List<SessionEntity> sessions) {

    public int claimedCount() {
        return sessions.size();
    }
}
