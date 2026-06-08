package com.labelhub.api.module.session.service.view;

import com.labelhub.api.module.schema.entity.SubmissionEntity;
import java.util.List;

public record BatchSubmitResultView(List<SubmissionEntity> submissions) {

    public int submittedCount() {
        return submissions.size();
    }
}
