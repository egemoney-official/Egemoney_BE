package com.igemoney.igemoney_BE.quiz.service;

import com.igemoney.igemoney_BE.quiz.repository.UserQuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizScoringService {

    private final UserQuizAttemptRepository userQuizAttemptRepository;

    public BigDecimal calculateAccuracyRate(Long quizId) {
        long correctAttempts = userQuizAttemptRepository.countByQuizIdAndIsCorrectTrue(quizId);
        long totalAttempts = userQuizAttemptRepository.countByQuizId(quizId);

        if (totalAttempts == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(correctAttempts)
            .divide(BigDecimal.valueOf(totalAttempts), 2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
}
