package com.labelhub.api.module.quality.mapper;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualityLedgerEntryMapperContractTest {

    @Test
    void quality_ledger_entry_mapper_has_no_parent_interfaces() {
        assertThat(QualityLedgerEntryMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void quality_ledger_entry_mapper_exposes_only_insert_and_select_methods() {
        for (Method method : QualityLedgerEntryMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates append-only QualityLedgerEntryMapper contract")
                .doesNotStartWith("update")
                .doesNotStartWith("delete")
                .doesNotStartWith("remove")
                .doesNotStartWith("save");
            assertThat(name.startsWith("insert") || name.startsWith("select"))
                .as("Method " + name + " must be insert/select only")
                .isTrue();
        }
    }

    @Test
    void latest_reviewer_verdict_query_tie_breaks_by_id_desc() throws NoSuchMethodException {
        Method method = QualityLedgerEntryMapper.class
            .getDeclaredMethod("selectLatestReviewerOverallVerdict", Long.class);
        Select select = method.getAnnotation(Select.class);

        assertThat(String.join(" ", select.value()))
            .contains("ORDER BY created_at DESC, id DESC");
    }
}
