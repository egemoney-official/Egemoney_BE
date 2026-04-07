package com.igemoney.igemoney_BE.quiz.dto;

import com.igemoney.igemoney_BE.quiz.entity.QuizSelect;

public record QuizChoiceItem(
    Long selectId,
    Integer order,
    String text
) {
    public static QuizChoiceItem from(QuizSelect select) {
        return new QuizChoiceItem(
            select.getId(),
            select.getSelectOrder(),
            select.getSelectText()
        );
    }
}
