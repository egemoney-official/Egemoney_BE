package com.igemoney.igemoney_BE.common.vector;

public record SimilarQuizHit(
    long quizId,
    long topicId,
    String questionTitle,
    double similarity
) {
}
