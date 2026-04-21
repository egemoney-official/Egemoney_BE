package com.igemoney.igemoney_BE.quiz.dto.common;

import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;

import java.util.List;

public record QuizQuestionData(
    List<QuizChoiceItem> choices,
    String inputType
) {
    private static final String TEXT_INPUT_TYPE = "TEXT";

    public static QuizQuestionData from(Quiz quiz) {
        if (quiz.getQuestionType() == QuestionType.SUBJECTIVE) {
            return new QuizQuestionData(null, TEXT_INPUT_TYPE);
        }

        List<QuizChoiceItem> choices = quiz.getSelects().stream()
            .map(QuizChoiceItem::from)
            .toList();
        return new QuizQuestionData(choices, null);
    }
}
