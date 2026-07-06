package com.igemoney.igemoney_BE.common.vector;

public record DocumentChunkRecord(
    String chunkingStrategy,
    Long topicId,
    String sourceFile,
    String heading,
    String content,
    String source,
    int charLength,
    float[] embedding
) {
}
