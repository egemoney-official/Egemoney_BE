package com.igemoney.igemoney_BE.quiz.service;

import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizCreateRequestException;
import com.igemoney.igemoney_BE.quiz.dto.QuizCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.QuizSelectCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.QuizSubjectiveCreateRequest;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class QuizCreateRequestValidator {

    public void validate(QuizCreateRequest request) {
        QuestionType questionType = parseQuestionType(request.questionType());

        if (questionType == QuestionType.SUBJECTIVE) {
            validateSubjectiveQuiz(request);
            return;
        }

        validateSelectQuiz(questionType, request);
    }

    private QuestionType parseQuestionType(String questionType) {
        try {
            return QuestionType.valueOf(questionType);
        } catch (RuntimeException e) {
            throw new InvalidQuizCreateRequestException("지원하지 않는 퀴즈 유형입니다.");
        }
    }

    private void validateSelectQuiz(QuestionType questionType, QuizCreateRequest request) {
        List<QuizSelectCreateRequest> selects = request.selects();
        List<QuizSubjectiveCreateRequest> subjectives = request.subjectives();

        if (selects == null || selects.isEmpty()) {
            throw new InvalidQuizCreateRequestException("선택형 문제는 선택지가 필요합니다.");
        }
        if (subjectives != null && !subjectives.isEmpty()) {
            throw new InvalidQuizCreateRequestException("선택형 문제에는 주관식 답안을 등록할 수 없습니다.");
        }
        if (questionType == QuestionType.OX && selects.size() != 2) {
            throw new InvalidQuizCreateRequestException("OX 문제는 선택지가 2개여야 합니다.");
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE && selects.size() != 4) {
            throw new InvalidQuizCreateRequestException("객관식 문제는 선택지가 4개여야 합니다.");
        }
        if (selects.stream().filter(select -> Boolean.TRUE.equals(select.isAnswer())).count() != 1) {
            throw new InvalidQuizCreateRequestException("선택형 문제는 정답 선택지가 정확히 1개여야 합니다.");
        }
        if (hasDuplicateSelectOrder(selects)) {
            throw new InvalidQuizCreateRequestException("선택지 순서는 중복될 수 없습니다.");
        }
    }

    private void validateSubjectiveQuiz(QuizCreateRequest request) {
        List<QuizSelectCreateRequest> selects = request.selects();
        List<QuizSubjectiveCreateRequest> subjectives = request.subjectives();

        if (subjectives == null || subjectives.isEmpty()) {
            throw new InvalidQuizCreateRequestException("주관식 문제는 정답 후보가 필요합니다.");
        }
        if (selects != null && !selects.isEmpty()) {
            throw new InvalidQuizCreateRequestException("주관식 문제에는 선택지를 등록할 수 없습니다.");
        }
        if (subjectives.stream().filter(answer -> Boolean.TRUE.equals(answer.isDisplayAnswer())).count() != 1) {
            throw new InvalidQuizCreateRequestException("주관식 문제는 대표 정답이 정확히 1개여야 합니다.");
        }
        if (hasDuplicateAnswerText(subjectives)) {
            throw new InvalidQuizCreateRequestException("주관식 정답 후보는 중복될 수 없습니다.");
        }
    }

    private boolean hasDuplicateSelectOrder(List<QuizSelectCreateRequest> selects) {
        Set<Integer> orders = new HashSet<>();
        return selects.stream()
            .map(QuizSelectCreateRequest::selectOrder)
            .anyMatch(order -> order == null || !orders.add(order));
    }

    private boolean hasDuplicateAnswerText(List<QuizSubjectiveCreateRequest> subjectives) {
        Set<String> answers = new HashSet<>();
        return subjectives.stream()
            .map(QuizSubjectiveCreateRequest::answerText)
            .map(this::normalizeAnswer)
            .anyMatch(answer -> answer.isBlank() || !answers.add(answer));
    }

    private String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.trim().toLowerCase().replaceAll("\\s+", "");
    }
}
