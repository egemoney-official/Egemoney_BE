package com.igemoney.igemoney_BE.quiz.dto.create;

import com.igemoney.igemoney_BE.quiz.entity.QuizSubjective;

public record QuizSubjectiveCreateRequest(
    String answerText,
    Boolean isDisplayAnswer
) {
    public QuizSubjective toEntity() {
        return QuizSubjective.builder()
            .answerText(answerText)
            .isDisplayAnswer(isDisplayAnswer)
            .build();
    }
}
