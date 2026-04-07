package com.igemoney.igemoney_BE.quiz.service;

import com.igemoney.igemoney_BE.common.exception.quiz.QuizAttemptNotFoundException;
import com.igemoney.igemoney_BE.common.exception.quiz.QuizNotFoundException;
import com.igemoney.igemoney_BE.common.exception.user.UserNotFoundException;
import com.igemoney.igemoney_BE.quiz.dto.QuizSubmitRequest;
import com.igemoney.igemoney_BE.quiz.dto.QuizSubmitResponse;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.entity.UserQuizAttempt;
import com.igemoney.igemoney_BE.quiz.entity.enums.DifficultyLevel;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import com.igemoney.igemoney_BE.quiz.repository.UserQuizAttemptRepository;
import com.igemoney.igemoney_BE.user.entity.User;
import com.igemoney.igemoney_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class QuizAttemptService {

    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final UserQuizAttemptRepository userQuizAttemptRepository;
    private final QuizScoringService quizScoringService;

    public QuizSubmitResponse submitQuizResult(Long quizId, QuizSubmitRequest request, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(QuizNotFoundException::new);

        User user = userRepository.findByUserId(userId)
            .orElseThrow(UserNotFoundException::new);

        UserQuizAttempt attempt = userQuizAttemptRepository.findByUserAndQuiz(user, quiz)
            .orElse(null);

        QuizSubmitResponse result = quizScoringService.gradeQuiz(quiz, request);

        if (attempt != null) {
            return result;
        }

        if (result.isCorrect()) {
            UserQuizAttempt userQuizCorrect = UserQuizAttempt.userQuizCorrect(user, quiz);
            userQuizAttemptRepository.save(userQuizCorrect);

            int quizAwardScore = DifficultyLevel.getScore(quiz.getDifficultyLevel());
            user.gainAwardRatingPoint(quizAwardScore);
        } else {
            UserQuizAttempt userQuizWrong = UserQuizAttempt.userQuizInCorrect(user, quiz);
            userQuizAttemptRepository.save(userQuizWrong);
        }

        quiz.updateCorrectRate(quizScoringService.calculateAccuracyRate(quiz.getId()));
        quizRepository.save(quiz);
        return result;
    }

    public QuizSubmitResponse submitReviewQuiz(Long quizId, QuizSubmitRequest request, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(QuizNotFoundException::new);

        UserQuizAttempt reviewQuiz = userQuizAttemptRepository.findByUser_userIdAndQuizId(userId, quizId)
            .orElseThrow(QuizAttemptNotFoundException::new);

        QuizSubmitResponse result = quizScoringService.gradeQuiz(quiz, request);

        if (result.isCorrect()) {
            reviewQuiz.updateOnCorrectReview();
        } else {
            reviewQuiz.updateOnIncorrectReview();
        }
        return result;
    }
}
