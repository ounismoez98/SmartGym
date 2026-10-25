package com.smartgym.membership.assistant;

public interface LlmClient {
    String complete(String systemPrompt, String userPrompt, boolean jsonResponse);
}
