package com.igemoney.igemoney_BE.knowledge.dto;

public record KnowledgeStrategyIngestionResult(
    String strategy,
    int documentCount,
    int deletedChunks,
    int insertedChunks,
    double averageCharLength,
    int maxCharLength,
    int embeddingBatchCalls
) {
}
