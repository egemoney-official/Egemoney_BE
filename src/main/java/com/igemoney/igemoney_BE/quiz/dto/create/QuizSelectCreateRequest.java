package com.igemoney.igemoney_BE.quiz.dto.create;

import com.igemoney.igemoney_BE.quiz.entity.QuizSelect;

public record QuizSelectCreateRequest(
    Integer selectOrder,
    String selectText,
    Boolean isAnswer
) {
    public QuizSelect toEntity() {
        return QuizSelect.builder()
            .selectOrder(selectOrder)
            .selectText(selectText)
            .isAnswer(isAnswer)
            .build();
    }
}
