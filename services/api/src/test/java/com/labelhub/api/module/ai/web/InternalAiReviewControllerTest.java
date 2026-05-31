package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.DimensionScore;
import com.labelhub.api.generated.model.InternalAiReviewContext;
import com.labelhub.api.generated.model.InternalAiReviewResult;
import com.labelhub.api.generated.model.InternalAiReviewResultRequest;
import com.labelhub.api.module.ai.service.AiReviewService;
import com.labelhub.api.module.ai.service.InternalAiReviewResultCommand;
import com.labelhub.api.module.ai.service.view.InternalAiReviewContextView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAiReviewControllerTest {

    private final AiReviewService aiReviewService = mock(AiReviewService.class);
    private final InternalAiReviewController controller = new InternalAiReviewController(aiReviewService);

    @Test
    void getAiReviewContext_maps_internal_context_for_agent_boundary() {
        when(aiReviewService.getInternalContext(300L)).thenReturn(contextView());

        InternalAiReviewContext body = controller.getAiReviewContext(300L).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getIdempotencyKey()).isEqualTo("submission:300:ai_review:promptVersionId:7:adapter:agent-default-v1");
        assertThat(body.getDimensions()).containsExactly("quality");
        assertThat(body.getPassThreshold()).isEqualByComparingTo("0.80");
        assertThat(body.getRejectThreshold()).isEqualByComparingTo("0.20");
        assertThat(body.getRenderedPrompt()).contains("rendered prompt");
    }

    @Test
    void reportAiReviewResult_maps_result_payload_to_command() {
        InternalAiReviewResultRequest request = new InternalAiReviewResultRequest();
        request.setSubmissionId(300L);
        request.setIdempotencyKey("submission:300:ai_review:promptVersionId:7:adapter:agent-default-v1");
        InternalAiReviewResult result = new InternalAiReviewResult();
        result.setOverallSuggestion(InternalAiReviewResult.OverallSuggestionEnum.PASS);
        result.setFinalScore(new BigDecimal("0.90"));
        DimensionScore score = new DimensionScore();
        score.setDimension("quality");
        score.setScore(new BigDecimal("0.90"));
        result.setDimensionScores(List.of(score));
        result.setSummary("ok");
        result.setResponsePayload(Map.of("overallSuggestion", "pass"));
        request.setResult(result);

        controller.reportAiReviewResult(request);

        ArgumentCaptor<InternalAiReviewResultCommand> captor =
            ArgumentCaptor.forClass(InternalAiReviewResultCommand.class);
        verify(aiReviewService).recordInternalResult(captor.capture());
        assertThat(captor.getValue().recommendation()).isEqualTo("pass");
        assertThat(captor.getValue().dimensionScores()).singleElement()
            .satisfies(dimensionScore -> assertThat(dimensionScore.dimension()).isEqualTo("quality"));
    }

    private static InternalAiReviewContextView contextView() {
        return new InternalAiReviewContextView(
            300L,
            "submission:300:ai_review:promptVersionId:7:adapter:agent-default-v1",
            "promptVersion#7",
            7L,
            19L,
            "agent-default-v1",
            Map.of("answerPayload", Map.of()),
            "a".repeat(64),
            List.of("quality"),
            new BigDecimal("0.80"),
            new BigDecimal("0.20"),
            "equal-weight-three-zone-v2",
            "business prompt",
            "rendered prompt"
        );
    }
}
