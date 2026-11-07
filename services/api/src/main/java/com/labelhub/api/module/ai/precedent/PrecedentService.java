package com.labelhub.api.module.ai.precedent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.ai.mapper.PrecedentCandidateMapper;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.ai.mapper.PrecedentCandidateMapper.CandidateRow;
import com.labelhub.api.module.quality.service.HumanVerdicts;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * ADR-021: BM25 retrieval of human-verdict precedents for one submission. The pool is a derived view of the
 * quality ledger (no index table): other submissions of the same task and schema version, not the same
 * dataset item, not superseded, whose current human verdict is approved or rejected.
 */
@Service
public class PrecedentService {

    public static final int MAX_K = 10;

    public record Result(String version, List<Precedent> precedents, String hash, String block) {
    }

    private final PrecedentCandidateMapper candidateMapper;
    private final SubmissionMapper submissionMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final PrecedentProperties properties;
    private final Canonicalizer canonicalizer;
    private final ObjectMapper objectMapper;

    public PrecedentService(
        PrecedentCandidateMapper candidateMapper,
        SubmissionMapper submissionMapper,
        DatasetItemMapper datasetItemMapper,
        PrecedentProperties properties,
        Canonicalizer canonicalizer,
        ObjectMapper objectMapper
    ) {
        this.candidateMapper = candidateMapper;
        this.submissionMapper = submissionMapper;
        this.datasetItemMapper = datasetItemMapper;
        this.properties = properties;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
    }

    public Result forSubmission(Long submissionId, Integer k) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        DatasetItemEntity item = datasetItemMapper.selectById(submission.getDatasetItemId());
        return forSubmission(submission, item == null ? Map.of() : nullToEmpty(item.getItemPayload()), k == null ? properties.getTopK() : k);
    }

    /** {@code datasetItemPayload} is passed in because the caller has usually loaded it already. */
    public Result forSubmission(SubmissionEntity submission, Map<String, Object> datasetItemPayload, int k) {
        int topK = Math.max(1, Math.min(MAX_K, k));
        List<CandidateRow> rows = candidateMapper.selectCandidates(submission.getTaskId(), submission.getSchemaVersionId(),
            submission.getId(), submission.getDatasetItemId(), properties.getPoolLimit());
        Map<Long, Map<String, Object>> contents = new LinkedHashMap<>();
        Map<Long, HumanVerdicts.HumanVerdict> verdicts = new LinkedHashMap<>();
        List<Bm25Index.Document<Long>> docs = new ArrayList<>();
        for (CandidateRow row : rows) {
            Optional<HumanVerdicts.HumanVerdict> verdict = HumanVerdicts.derive(row.getVerdict(), row.getReviewLevel(),
                row.getOpenCases() == null ? 0 : row.getOpenCases(), row.getLatestResolution());
            if (verdict.isEmpty()) {
                continue;  // still pending: not a precedent yet
            }
            Map<String, Object> content = content(parse(row.getAnswerPayloadJson()), parse(row.getDatasetItemPayloadJson()));
            contents.put(row.getSubmissionId(), content);
            verdicts.put(row.getSubmissionId(), verdict.get());
            docs.add(new Bm25Index.Document<>(row.getSubmissionId(), PrecedentText.textOf(content)));
        }
        String query = PrecedentText.textOf(content(nullToEmpty(submission.getAnswerPayload()), datasetItemPayload));
        List<Long> hits = new Bm25Index<>(docs).search(query, topK, Set.of());
        List<Precedent> precedents = new ArrayList<>();
        for (Long id : hits) {
            HumanVerdicts.HumanVerdict verdict = verdicts.get(id);
            precedents.add(new Precedent(precedents.size() + 1, id, verdict.status(),
                verdict.decidedBySenior() ? Precedent.SOURCE_SENIOR : Precedent.SOURCE_REVIEWER,
                PrecedentText.excerpt(PrecedentText.labeledText(contents.get(id)))));
        }
        return new Result(PrecedentBlock.VERSION, List.copyOf(precedents), PrecedentBlock.hash(precedents, canonicalizer),
            precedents.isEmpty() ? null : PrecedentBlock.render(precedents, canonicalizer));
    }

    /**
     * ADR-021 §3 audit: the agent echoes the precedents it was given. Accept them only if they are well formed,
     * hash to the echoed hash, and each id is a different submission of the same task and schema version.
     */
    public void requireAuthentic(SubmissionEntity submission, List<Precedent> precedents, String echoedHash) {
        List<Precedent> list = precedents == null ? List.of() : precedents;
        if (!Objects.equals(PrecedentBlock.hash(list, canonicalizer), echoedHash)) {
            throw new InvalidPrecedentsException("precedentsHash does not match the echoed precedents");
        }
        for (int i = 0; i < list.size(); i++) {
            Precedent p = list.get(i);
            if (p.rank() != i + 1
                || !(Precedent.APPROVED.equals(p.verdict()) || Precedent.REJECTED.equals(p.verdict()))
                || !(Precedent.SOURCE_REVIEWER.equals(p.source()) || Precedent.SOURCE_SENIOR.equals(p.source()))
                || p.submission() == null || p.submissionId() == null) {
                throw new InvalidPrecedentsException("precedent #" + (i + 1) + " is malformed");
            }
            SubmissionEntity other = submissionMapper.selectById(p.submissionId());
            if (other == null || Objects.equals(other.getId(), submission.getId())
                || !Objects.equals(other.getTaskId(), submission.getTaskId())
                || !Objects.equals(other.getSchemaVersionId(), submission.getSchemaVersionId())) {
                throw new InvalidPrecedentsException("precedent #" + (i + 1) + " is not a submission of the same task and schema version");
            }
        }
    }

    public String render(List<Precedent> precedents) {
        return precedents == null || precedents.isEmpty() ? null : PrecedentBlock.render(precedents, canonicalizer);
    }

    static Map<String, Object> content(Map<String, Object> answerPayload, Map<String, Object> datasetItemPayload) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("answerPayload", answerPayload == null ? Map.of() : answerPayload);
        content.put("datasetItemPayload", datasetItemPayload == null ? Map.of() : datasetItemPayload);
        return content;
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }
}
