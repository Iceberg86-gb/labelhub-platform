package com.labelhub.agent.llm;

public interface LlmProvider {

    String name();

    String complete(String prompt);
}
