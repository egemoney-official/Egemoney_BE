package com.igemoney.igemoney_BE.quiz.service;

import com.igemoney.igemoney_BE.common.exception.quiz.DuplicateQuizQuestionException;
import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizCreateRequestException;
import com.igemoney.igemoney_BE.common.exception.quiz.QuizNotFoundException;
import com.igemoney.igemoney_BE.quiz.dto.AdminQuizListResponse;
import com.igemoney.igemoney_BE.quiz.dto.AdminQuizSummaryResponse;
import com.igemoney.igemoney_BE.quiz.dto.QuizCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.QuizResponse;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import com.igemoney.igemoney_BE.quiz.repository.BookmarkRepository;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import com.igemoney.igemoney_BE.quiz.repository.UserQuizAttemptRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class QuizAdminService {

    private static final Sort ADMIN_QUIZ_SORT = Sort.by(
        Sort.Order.asc("topic.id"),
        Sort.Order.asc("questionOrder"),
        Sort.Order.asc("id")
    );

    private final QuizCreateService quizCreateService;
    private final QuizRepository quizRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserQuizAttemptRepository userQuizAttemptRepository;

    public QuizResponse createQuiz(QuizCreateRequest request) {
        validateDuplicateQuestion(request);
        return quizCreateService.createQuiz(request);
    }

    @Transactional(readOnly = true)
    public AdminQuizListResponse getQuizzes(Long topicId, String questionType, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, ADMIN_QUIZ_SORT);
        Page<Quiz> quizzes = findQuizzes(topicId, questionType, pageable);

        Page<AdminQuizSummaryResponse> summaries = quizzes.map(AdminQuizSummaryResponse::from);
        return AdminQuizListResponse.from(summaries);
    }

    @Transactional(readOnly = true)
    public QuizResponse getQuiz(Long quizId) {
        Quiz quiz = quizRepository.findDetailedById(quizId)
            .orElseThrow(QuizNotFoundException::new);
        return QuizResponse.from(quiz, false, false);
    }

    public void deleteQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(QuizNotFoundException::new);

        bookmarkRepository.deleteByQuizId(quizId);
        userQuizAttemptRepository.deleteByQuizId(quizId);
        quizRepository.delete(quiz);
    }

    private Page<Quiz> findQuizzes(Long topicId, String questionType, PageRequest pageable) {
        QuestionType parsedQuestionType = parseQuestionType(questionType);

        if (topicId != null && parsedQuestionType != null) {
            return quizRepository.findAllByTopicIdAndQuestionType(topicId, parsedQuestionType, pageable);
        }
        if (topicId != null) {
            return quizRepository.findAllByTopicId(topicId, pageable);
        }
        if (parsedQuestionType != null) {
            return quizRepository.findAllByQuestionType(parsedQuestionType, pageable);
        }
        return quizRepository.findAll(pageable);
    }

    private QuestionType parseQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return null;
        }
        try {
            return QuestionType.valueOf(questionType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidQuizCreateRequestException("지원하지 않는 퀴즈 유형입니다.");
        }
    }

    private void validateDuplicateQuestion(QuizCreateRequest request) {
        boolean duplicated = quizRepository.findAllByTopicId(request.topicId()).stream()
            .map(Quiz::getQuestionTitle)
            .map(this::normalizeQuestionTitle)
            .anyMatch(normalized -> normalized.equals(normalizeQuestionTitle(request.questionTitle())));

        if (duplicated) {
            throw new DuplicateQuizQuestionException();
        }
    }

    private String normalizeQuestionTitle(String questionTitle) {
        return questionTitle == null
            ? ""
            : questionTitle.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
