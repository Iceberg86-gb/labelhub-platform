package com.labelhub.api.module.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/** LH-3 / conventions §4: ai_review_auto_policy_events is append-only. */
class AiReviewAutoPolicyEventMapperContractTest {

    @Test
    void mapper_has_no_parent_interfaces_so_no_inherited_update_or_delete() {
        assertThat(AiReviewAutoPolicyEventMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void mapper_exposes_only_insert_and_select_methods() {
        for (Method method : AiReviewAutoPolicyEventMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name.startsWith("insert") || name.startsWith("select"))
                .as("Method " + name + " must be insert/select only")
                .isTrue();
        }
    }

    @Test
    void latest_event_query_tie_breaks_by_id_desc() throws NoSuchMethodException {
        Select select = AiReviewAutoPolicyEventMapper.class.getDeclaredMethod("selectLatestByRuleId", Long.class).getAnnotation(Select.class);
        assertThat(String.join(" ", select.value())).contains("ORDER BY created_at DESC, id DESC");
    }
}
