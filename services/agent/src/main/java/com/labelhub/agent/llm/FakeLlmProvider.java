package com.labelhub.agent.llm;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class FakeLlmProvider implements LlmProvider {

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public String complete(String prompt) {
        return """
            {"scores":{"accuracy":0.8,"format":1.0},"verdict":"manual","reason":"local fake provider"}
            """;
    }
}
