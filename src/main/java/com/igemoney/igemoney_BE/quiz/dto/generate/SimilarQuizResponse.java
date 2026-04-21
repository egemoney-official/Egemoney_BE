package com.igemoney.igemoney_BE.quiz.dto.generate;

import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record SimilarQuizResponse(
    Long quizId,
    Long topicId,
    String topicName,
    String questionTitle,
    String questionType,
    Integer questionOrder,
    Double similarityScore,
    String similarityReason
) {
    public static SimilarQuizResponse from(Quiz quiz, double similarityScore, String similarityReason) {
        return new SimilarQuizResponse(
            quiz.getId(),
            quiz.getTopic().getId(),
            quiz.getTopic().getName(),
            quiz.getQuestionTitle(),
            quiz.getQuestionType().name(),
            quiz.getQuestionOrder(),
            roundSimilarity(similarityScore),
            similarityReason
        );
    }

    private static double roundSimilarity(double similarityScore) {
        return BigDecimal.valueOf(similarityScore)
            .setScale(4, RoundingMode.HALF_UP)
            .doubleValue();
    }
}
