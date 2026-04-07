package com.igemoney.igemoney_BE.quiz.dto;

public record QuizSubmitResponse(
    boolean isCorrect,
    String correctAnswer,
    String explanation
) {
}
