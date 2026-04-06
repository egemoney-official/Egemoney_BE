package com.igemoney.igemoney_BE.quiz.service;

import com.igemoney.igemoney_BE.quiz.dto.QuizResponse;
import com.igemoney.igemoney_BE.quiz.dto.QuizReviewResponse;
import com.igemoney.igemoney_BE.quiz.dto.QuizSubmitRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class QuizService {

    private final QuizQueryService quizQueryService;
    private final QuizAttemptService quizAttemptService;

    @Transactional(readOnly = true)
    public QuizResponse getQuizInfo(Long quizId, Long userId) {
        return quizQueryService.getQuizInfo(quizId, userId);
    }

    @Transactional(readOnly = true)
    public QuizReviewResponse getWrongQuiz(Long userId) {
        return quizQueryService.getWrongQuiz(userId);
    }

    @Transactional(readOnly = true)
    public QuizReviewResponse getQuizReview(Long userId) {
        return quizQueryService.getQuizReview(userId);
    }

    public void submitQuizResult(Long quizId, QuizSubmitRequest request, Long userId) {
        quizAttemptService.submitQuizResult(quizId, request, userId);
    }

    public void submitReviewQuiz(Long quizId, QuizSubmitRequest request, Long userId) {
        quizAttemptService.submitReviewQuiz(quizId, request, userId);
    }
}
