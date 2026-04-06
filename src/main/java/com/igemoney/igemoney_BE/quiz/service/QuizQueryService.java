package com.igemoney.igemoney_BE.quiz.service;

import com.igemoney.igemoney_BE.common.exception.quiz.QuizNotFoundException;
import com.igemoney.igemoney_BE.common.exception.user.UserNotFoundException;
import com.igemoney.igemoney_BE.quiz.dto.QuizResponse;
import com.igemoney.igemoney_BE.quiz.dto.QuizReviewResponse;
import com.igemoney.igemoney_BE.quiz.dto.ReviewQuizDetail;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.entity.UserQuizAttempt;
import com.igemoney.igemoney_BE.quiz.repository.BookmarkRepository;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import com.igemoney.igemoney_BE.quiz.repository.UserQuizAttemptRepository;
import com.igemoney.igemoney_BE.user.entity.User;
import com.igemoney.igemoney_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizQueryService {

    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final UserQuizAttemptRepository userQuizAttemptRepository;
    private final BookmarkRepository bookmarkRepository;

    public QuizResponse getQuizInfo(Long quizId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(QuizNotFoundException::new);

        User user = userRepository.findByUserId(userId)
            .orElseThrow(UserNotFoundException::new);

        boolean isBookmarked = bookmarkRepository.existsByUserAndQuiz(user, quiz);
        boolean isSolved = userQuizAttemptRepository.existsByUserAndQuizAndIsCompletedTrue(user, quiz);

        return QuizResponse.from(quiz, isBookmarked, isSolved);
    }

    public QuizReviewResponse getWrongQuiz(Long userId) {
        List<UserQuizAttempt> attempts = userQuizAttemptRepository.findByUser_userIdAndIsCorrectFalse(userId);

        List<ReviewQuizDetail> quizDetails = attempts.stream()
            .map(ReviewQuizDetail::from)
            .toList();

        return new QuizReviewResponse(quizDetails);
    }

    public QuizReviewResponse getQuizReview(Long userId) {
        List<UserQuizAttempt> attempts = userQuizAttemptRepository.findByUser_userIdAndIsCorrectFalse(userId);
        List<ReviewQuizDetail> quizDetails = attempts.stream()
            .filter(attempt -> attempt.getNextReviewDate() != null &&
                (attempt.getNextReviewDate().isEqual(LocalDate.now()) ||
                    attempt.getNextReviewDate().isBefore(LocalDate.now())))
            .map(ReviewQuizDetail::from)
            .toList();

        return new QuizReviewResponse(quizDetails);
    }
}
