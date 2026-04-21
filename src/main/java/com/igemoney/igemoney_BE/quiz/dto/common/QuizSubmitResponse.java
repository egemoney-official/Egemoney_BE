package com.igemoney.igemoney_BE.quiz.dto.common;

public record QuizSubmitResponse(
    boolean isCorrect,
    String correctAnswer,
    String explanation
) {
}
