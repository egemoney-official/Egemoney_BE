package com.igemoney.igemoney_BE.quiz.dto.common;

import com.igemoney.igemoney_BE.quiz.entity.Quiz;

import java.math.BigDecimal;

public record QuizResponse(
    Long quizId,
    String questionTitle,
    String questionType,
    QuizQuestionData questionData,
    String difficultyLevel,
    String explanation,
    Integer questionOrder,
    BigDecimal correctRate,

    Long topicId,
    String topicName,

    boolean isSolved,
    boolean isBookmarked
) {
    public static QuizResponse from(Quiz quiz, boolean isBookmarked, boolean isSolved) {
        return new QuizResponse(
            quiz.getId(),
            quiz.getQuestionTitle(),
            quiz.getQuestionType().name(),
            QuizQuestionData.from(quiz),
            quiz.getDifficultyLevel().name(),
            quiz.getExplanation(),
            quiz.getQuestionOrder(),
            quiz.getCorrectRate(),
            quiz.getTopic().getId(),
            quiz.getTopic().getName(),
            isSolved,
            isBookmarked
        );
    }
}
