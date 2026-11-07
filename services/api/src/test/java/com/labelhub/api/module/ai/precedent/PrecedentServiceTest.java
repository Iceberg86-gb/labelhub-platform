package com.labelhub.api.module.ai.precedent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.ai.mapper.PrecedentCandidateMapper;
import com.labelhub.api.module.ai.mapper.PrecedentCandidateMapper.CandidateRow;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ADR-021 §1-§3: who can be a precedent, ranking, and the audit check on echoed precedents. */
class PrecedentServiceTest {

    private static final Long TASK = 5L;
    private static final Long SCHEMA = 9L;

    private final PrecedentCandidateMapper candidateMapper = mock(PrecedentCandidateMapper.class);
    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private final DatasetItemMapper datasetItemMapper = mock(DatasetItemMapper.class);
    private final PrecedentProperties properties = new PrecedentProperties();
    private final Canonicalizer canonicalizer = new Canonicalizer(new ObjectMapper());
    private PrecedentService service;
    private SubmissionEntity target;

    @BeforeEach
    void setUp() {
        service = new PrecedentService(candidateMapper, submissionMapper, datasetItemMapper, properties, canonicalizer, new ObjectMapper());
        target = submission(100L, 1000L, Map.of("label", "positive", "reason", "battery life is great"));
        when(submissionMapper.selectById(100L)).thenReturn(target);
    }

    private static SubmissionEntity submission(Long id, Long item, Map<String, Object> answer) {
        SubmissionEntity s = new SubmissionEntity();
        s.setId(id);
        s.setTaskId(TASK);
        s.setSchemaVersionId(SCHEMA);
        s.setDatasetItemId(item);
        s.setAnswerPayload(answer);
        return s;
    }

    private static CandidateRow row(long id, String answerJson, String verdict, String level, long open, String resolution) {
        CandidateRow r = new CandidateRow();
        r.setSubmissionId(id);
        r.setAnswerPayloadJson(answerJson);
        r.setDatasetItemPayloadJson("{}");
        r.setVerdict(verdict);
        r.setReviewLevel(level);
        r.setOpenCases(open);
        r.setLatestResolution(resolution);
        return r;
    }

    @Test
    void retrieves_human_verdicts_most_similar_first_and_skips_pending() {
        when(candidateMapper.selectCandidates(eq(TASK), eq(SCHEMA), eq(100L), eq(1000L), anyInt())).thenReturn(List.of(
            row(4, "{\"reason\":\"battery life is great, lasts days\"}", "approve", "reviewer", 0, null),
            row(3, "{\"reason\":\"battery life is great\"}", "approve", "reviewer", 1, null),  // open senior case: pending
            row(2, "{\"reason\":\"battery died, life is short\"}", "approve", "reviewer", 0, "overturn_to_reject"),
            row(1, "{\"reason\":\"shipping was slow\"}", "reject", "reviewer", 0, null)
        ));

        PrecedentService.Result result = service.forSubmission(target, Map.of(), 3);

        assertThat(result.version()).isEqualTo(PrecedentBlock.VERSION);
        assertThat(result.precedents()).extracting(Precedent::submissionId).containsExactly(4L, 2L);
        assertThat(result.precedents().get(0)).satisfies(p -> {
            assertThat(p.rank()).isEqualTo(1);
            assertThat(p.verdict()).isEqualTo("approved");
            assertThat(p.source()).isEqualTo("reviewer");
            assertThat(p.submission()).isEqualTo("answerPayload.reason: battery life is great, lasts days");
        });
        assertThat(result.precedents().get(1)).satisfies(p -> {
            assertThat(p.verdict()).isEqualTo("rejected");
            assertThat(p.source()).isEqualTo("senior");
        });
        assertThat(result.block()).startsWith(PrecedentBlock.HEADER + "\n[");
        assertThat(result.hash()).isEqualTo(PrecedentBlock.hash(result.precedents(), canonicalizer));
    }

    @Test
    void no_candidates_means_an_empty_list_and_no_block() {
        when(candidateMapper.selectCandidates(eq(TASK), eq(SCHEMA), eq(100L), eq(1000L), anyInt())).thenReturn(List.of());
        PrecedentService.Result result = service.forSubmission(target, Map.of(), 3);
        assertThat(result.precedents()).isEmpty();
        assertThat(result.block()).isNull();
        assertThat(result.hash()).isEqualTo(PrecedentBlock.hash(List.of(), canonicalizer));
    }

    @Test
    void k_is_clamped_and_pool_limit_is_passed_through() {
        properties.setPoolLimit(7);
        when(candidateMapper.selectCandidates(TASK, SCHEMA, 100L, 1000L, 7)).thenReturn(List.of(
            row(1, "{\"r\":\"battery\"}", "approve", "reviewer", 0, null),
            row(2, "{\"r\":\"battery life\"}", "approve", "reviewer", 0, null)));
        assertThat(service.forSubmission(target, Map.of(), 0).precedents()).hasSize(1);
        assertThat(service.forSubmission(target, Map.of(), 99).precedents()).hasSize(2);
    }

    @Test
    void echoed_precedents_must_hash_and_belong_to_the_same_task_and_schema() {
        Precedent good = new Precedent(1, 7L, "approved", "reviewer", "answerPayload.r: x");
        when(submissionMapper.selectById(7L)).thenReturn(submission(7L, 1007L, Map.of()));
        String hash = PrecedentBlock.hash(List.of(good), canonicalizer);
        service.requireAuthentic(target, List.of(good), hash);
        service.requireAuthentic(target, List.of(), PrecedentBlock.hash(List.of(), canonicalizer));

        assertThatThrownBy(() -> service.requireAuthentic(target, List.of(good), "0".repeat(64)))
            .isInstanceOf(InvalidPrecedentsException.class).hasMessageContaining("precedentsHash");

        Precedent self = new Precedent(1, 100L, "approved", "reviewer", "x");
        assertThatThrownBy(() -> service.requireAuthentic(target, List.of(self), PrecedentBlock.hash(List.of(self), canonicalizer)))
            .isInstanceOf(InvalidPrecedentsException.class);

        SubmissionEntity otherTask = submission(8L, 1008L, Map.of());
        otherTask.setTaskId(6L);
        when(submissionMapper.selectById(8L)).thenReturn(otherTask);
        Precedent foreign = new Precedent(1, 8L, "approved", "reviewer", "x");
        assertThatThrownBy(() -> service.requireAuthentic(target, List.of(foreign), PrecedentBlock.hash(List.of(foreign), canonicalizer)))
            .isInstanceOf(InvalidPrecedentsException.class).hasMessageContaining("same task");

        Precedent aiVerdict = new Precedent(1, 7L, "pass", "reviewer", "x");
        assertThatThrownBy(() -> service.requireAuthentic(target, List.of(aiVerdict), PrecedentBlock.hash(List.of(aiVerdict), canonicalizer)))
            .isInstanceOf(InvalidPrecedentsException.class).hasMessageContaining("malformed");
    }

    @Test
    void adapter_version_marks_precedent_mode() {
        assertThat(PrecedentProperties.adapterVersion(false)).isEqualTo("agent-default-v1");
        assertThat(PrecedentProperties.adapterVersion(true)).isEqualTo("agent-default-v1+h4-precedents-v1");
        assertThat(properties.adapterVersion()).isEqualTo("agent-default-v1");
    }
}
