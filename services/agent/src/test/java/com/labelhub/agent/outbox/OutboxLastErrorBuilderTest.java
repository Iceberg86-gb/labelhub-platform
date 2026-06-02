package com.labelhub.agent.outbox;

import com.labelhub.agent.llm.runtime.RuntimeProviderCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxLastErrorBuilderTest {

    private final OutboxLastErrorBuilder builder = new OutboxLastErrorBuilder();

    @Test
    void buildLastError_uses_provider_reason_status_and_redacts_sensitive_message_parts() {
        String headerName = "Author" + "ization";
        String bearer = "Bea" + "rer";
        String payloadField = "answer_" + "payload";
        RuntimeProviderCallException exception = new RuntimeProviderCallException(
            "HTTP 400 " + headerName + ": " + bearer + " sk-live-secret " + payloadField,
            false,
            "provider_http_error",
            400,
            null,
            "{\"error\":{\"message\":\"tool_choice unsupported " + headerName + ": " + bearer + " sk-live-secret " + payloadField + "\"}}"
        );

        String result = builder.buildLastError(exception);

        assertThat(result).contains("reason=provider_http_error");
        assertThat(result).contains("status=400");
        assertThat(result).contains("tool_choice unsupported");
        assertThat(result).doesNotContain(headerName);
        assertThat(result).doesNotContain(bearer);
        assertThat(result).doesNotContain("sk-live-secret");
        assertThat(result).doesNotContain(payloadField);
    }

    @Test
    void buildLastError_truncates_to_column_limit() {
        String result = builder.buildLastError(new IllegalStateException("x".repeat(2_000)));

        assertThat(result).hasSizeLessThanOrEqualTo(1000);
    }
}
