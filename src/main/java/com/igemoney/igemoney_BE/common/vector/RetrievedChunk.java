package com.igemoney.igemoney_BE.common.vector;

public record RetrievedChunk(
    String content,
    String sourceFile,
    String heading,
    double similarity
) {
}
