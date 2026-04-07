package com.igemoney.igemoney_BE.quiz.service;

import com.igemoney.igemoney_BE.quiz.dto.QuizResponse;
import com.igemoney.igemoney_BE.quiz.dto.QuizReviewResponse;
import com.igemoney.igemoney_BE.quiz.dto.QuizCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.QuizSubmitRequest;
import com.igemoney.igemoney_BE.quiz.dto.QuizSubmitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class QuizService {

    private final QuizQueryService quizQueryService;
    private final QuizAttemptService quizAttemptService;
    private final QuizCreateService quizCreateService;

    public QuizResponse createQuiz(QuizCreateRequest request) {
        return quizCreateService.createQuiz(request);
    }

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

    public QuizSubmitResponse submitQuizResult(Long quizId, QuizSubmitRequest request, Long userId) {
        return quizAttemptService.submitQuizResult(quizId, request, userId);
    }

    public QuizSubmitResponse submitReviewQuiz(Long quizId, QuizSubmitRequest request, Long userId) {
        return quizAttemptService.submitReviewQuiz(quizId, request, userId);
    }
}
