package com.labelhub.agent.llm.runtime;

import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultPayload;
import com.labelhub.agent.llm.AiReviewProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class RegistryBackedAiReviewProvider implements AiReviewProvider {

    private final RuntimeProviderResolver resolver;
    private final OpenAiCompatibleAiReviewRuntimeClient openAiCompatibleClient;

    public RegistryBackedAiReviewProvider(
        RuntimeProviderResolver resolver,
        OpenAiCompatibleAiReviewRuntimeClient openAiCompatibleClient
    ) {
        this.resolver = resolver;
        this.openAiCompatibleClient = openAiCompatibleClient;
    }

    @Override
    public AiReviewResultPayload review(AiReviewContext context) {
        RuntimeProviderSource source = resolver.resolve(context.submissionId());
        if (!"openai-compatible".equalsIgnoreCase(source.providerType())) {
            throw new RuntimeProviderResolutionException(
                "Unsupported runtime provider type: " + source.providerType(),
                "unsupported_provider_type"
            );
        }
        return openAiCompatibleClient.review(context, source);
    }
}
