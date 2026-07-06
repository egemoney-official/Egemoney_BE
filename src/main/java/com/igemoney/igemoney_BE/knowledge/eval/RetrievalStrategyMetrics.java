package com.igemoney.igemoney_BE.knowledge.eval;

public record RetrievalStrategyMetrics(
    String strategy,
    long chunkCount,
    double averageCharLength,
    double hitAt1,
    double hitAt3,
    double recallAt5,
    double mrrAt5,
    double averageTop1Similarity
) {
}
