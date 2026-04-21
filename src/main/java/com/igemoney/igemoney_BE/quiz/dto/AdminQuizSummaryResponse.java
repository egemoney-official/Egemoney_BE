package com.igemoney.igemoney_BE.quiz.dto;

import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import java.math.BigDecimal;

public record AdminQuizSummaryResponse(
    Long quizId,
    Long topicId,
    String topicName,
    Integer questionOrder,
    String questionTitle,
    String questionType,
    String difficultyLevel,
    BigDecimal correctRate
) {
    public static AdminQuizSummaryResponse from(Quiz quiz) {
        return new AdminQuizSummaryResponse(
            quiz.getId(),
            quiz.getTopic().getId(),
            quiz.getTopic().getName(),
            quiz.getQuestionOrder(),
            quiz.getQuestionTitle(),
            quiz.getQuestionType().name(),
            quiz.getDifficultyLevel().name(),
            quiz.getCorrectRate()
        );
    }
}
