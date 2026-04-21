package com.igemoney.igemoney_BE.quiz.dto.create;

import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.quiz.entity.enums.DifficultyLevel;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;

import java.math.BigDecimal;
import java.util.List;

public record QuizCreateRequest(
        String questionTitle,
        String questionType,
        String difficultyLevel,
        String explanation,
        Integer questionOrder,
        BigDecimal correctRate,
        Long topicId,
        List<QuizSelectCreateRequest> selects,
        List<QuizSubjectiveCreateRequest> subjectives
) {
    public QuizCreateRequest withQuestionOrder(Integer questionOrder) {
        return new QuizCreateRequest(
            questionTitle,
            questionType,
            difficultyLevel,
            explanation,
            questionOrder,
            correctRate,
            topicId,
            selects,
            subjectives
        );
    }

    public static Quiz toEntity(QuizCreateRequest request, QuizTopic topic) {
        QuestionType parsedQuestionType = QuestionType.valueOf(request.questionType());
        Quiz quiz = Quiz.builder()
                .topic(topic)
                .questionTitle(request.questionTitle())
                .questionType(parsedQuestionType)
                .difficultyLevel(DifficultyLevel.valueOf(request.difficultyLevel()))
                .explanation(request.explanation())
                .questionOrder(request.questionOrder())
                .build();

        if (parsedQuestionType == QuestionType.SUBJECTIVE) {
            addSubjectives(request, quiz);
        } else {
            addSelects(request, quiz);
        }

        return quiz;
    }

    private static void addSelects(QuizCreateRequest request, Quiz quiz) {
        if (request.selects() == null) {
            return;
        }

        request.selects().stream()
            .map(QuizSelectCreateRequest::toEntity)
            .forEach(quiz::addSelect);
    }

    private static void addSubjectives(QuizCreateRequest request, Quiz quiz) {
        if (request.subjectives() == null) {
            return;
        }

        request.subjectives().stream()
            .map(QuizSubjectiveCreateRequest::toEntity)
            .forEach(quiz::addSubjective);
    }
}
