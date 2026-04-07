package com.igemoney.igemoney_BE.quiz.service;

import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizAnswerException;
import com.igemoney.igemoney_BE.quiz.dto.QuizSubmitRequest;
import com.igemoney.igemoney_BE.quiz.dto.QuizSubmitResponse;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.entity.QuizSelect;
import com.igemoney.igemoney_BE.quiz.entity.QuizSubjective;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import com.igemoney.igemoney_BE.quiz.repository.QuizSelectRepository;
import com.igemoney.igemoney_BE.quiz.repository.QuizSubjectiveRepository;
import com.igemoney.igemoney_BE.quiz.repository.UserQuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizScoringService {

    private final UserQuizAttemptRepository userQuizAttemptRepository;
    private final QuizSelectRepository quizSelectRepository;
    private final QuizSubjectiveRepository quizSubjectiveRepository;

    public QuizSubmitResponse gradeQuiz(Quiz quiz, QuizSubmitRequest request) {
        if (quiz.getQuestionType() == QuestionType.SUBJECTIVE) {
            return gradeSubjectiveQuiz(quiz, request);
        }

        return gradeSelectQuiz(quiz, request);
    }

    private QuizSubmitResponse gradeSelectQuiz(Quiz quiz, QuizSubmitRequest request) {
        if (request.selectedSelectId() == null) {
            throw new InvalidQuizAnswerException("선택형 문제는 선택지 식별자가 필요합니다.");
        }

        QuizSelect selected = quizSelectRepository.findByIdAndQuizId(request.selectedSelectId(), quiz.getId())
            .orElseThrow(() -> new InvalidQuizAnswerException("해당 퀴즈의 선택지가 아닙니다."));

        String correctAnswer = quizSelectRepository.findFirstByQuizIdAndIsAnswerTrueOrderBySelectOrderAsc(quiz.getId())
            .map(QuizSelect::getSelectText)
            .orElse(null);

        return new QuizSubmitResponse(
            selected.getIsAnswer(),
            correctAnswer,
            quiz.getExplanation()
        );
    }

    private QuizSubmitResponse gradeSubjectiveQuiz(Quiz quiz, QuizSubmitRequest request) {
        if (request.subjectiveAnswer() == null || request.subjectiveAnswer().isBlank()) {
            throw new InvalidQuizAnswerException("주관식 문제는 답안 입력값이 필요합니다.");
        }

        String normalizedUserAnswer = normalizeSubjectiveAnswer(request.subjectiveAnswer());
        List<QuizSubjective> answers = quizSubjectiveRepository.findAllByQuizId(quiz.getId());
        boolean isCorrect = answers.stream()
            .map(QuizSubjective::getAnswerText)
            .map(this::normalizeSubjectiveAnswer)
            .anyMatch(normalizedUserAnswer::equals);

        String displayAnswer = answers.stream()
                .filter(QuizSubjective::getIsDisplayAnswer)
                .findFirst()
                .or(() -> answers.stream().findFirst())
                .map(QuizSubjective::getAnswerText)
                .orElse(null);

        return new QuizSubmitResponse(
            isCorrect,
            displayAnswer,
            quiz.getExplanation()
        );
    }

    private String normalizeSubjectiveAnswer(String answer) {
        return answer.trim()
            .toLowerCase()
            .replaceAll("\\s+", "");
    }

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
