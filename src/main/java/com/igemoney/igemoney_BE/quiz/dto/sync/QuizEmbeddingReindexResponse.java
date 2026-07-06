package com.igemoney.igemoney_BE.quiz.dto.sync;

public record QuizEmbeddingReindexResponse(
    int deletedEmbeddings,
    int indexedQuizzes
) {
}
