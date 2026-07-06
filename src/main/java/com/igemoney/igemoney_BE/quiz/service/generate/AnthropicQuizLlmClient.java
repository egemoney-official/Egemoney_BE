package com.igemoney.igemoney_BE.quiz.service.generate;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quiz.generation.mode", havingValue = "rag")
public class AnthropicQuizLlmClient implements QuizLlmClient {

    private static final String MODEL = "claude-opus-4-8";
    private static final long MAX_TOKENS = 8192L;

    private final AnthropicClient anthropicClient;

    @Override
    public QuizDraftBatch generate(String systemPrompt, String userPrompt) {
        StructuredMessageCreateParams<QuizDraftBatch> params = StructuredMessageCreateParams
            .<QuizDraftBatch>builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(QuizDraftBatch.class)
            .system(systemPrompt)
            .addUserMessage(userPrompt)
            .build();

        return anthropicClient.messages()
            .create(params)
            .content()
            .stream()
            .flatMap(contentBlock -> contentBlock.text().stream())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Claude quiz generation response is empty."))
            .text();
    }
}
