package com.igemoney.igemoney_BE.quiz.service.generate;

import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizCreateRequestException;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizCandidateResponse;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateRequest;
import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateResponse;
import com.igemoney.igemoney_BE.quiz.entity.enums.DifficultyLevel;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultAdminQuizGenerationService implements AdminQuizGenerationService {

    private final QuizGenerationService quizGenerationService;
    private final QuizSimilarityService quizSimilarityService;

    @Override
    public QuizGenerateResponse generate(QuizGenerateRequest request) {
        validate(request);

        List<GeneratedQuizDraft> drafts = quizGenerationService.generateDrafts(request);
        List<GeneratedQuizCandidateResponse> candidates = quizSimilarityService.analyzeCandidates(drafts);

        return new QuizGenerateResponse(candidates, LocalDateTime.now());
    }

    private void validate(QuizGenerateRequest request) {
        if (request == null) {
            throw new InvalidQuizCreateRequestException("퀴즈 생성 요청이 비어 있습니다.");
        }
        if (request.topicId() == null) {
            throw new InvalidQuizCreateRequestException("토픽 ID는 필수입니다.");
        }
        parseQuestionType(request.questionType());
        parseDifficultyLevel(request.difficultyLevel());
    }

    private void parseQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            throw new InvalidQuizCreateRequestException("퀴즈 유형은 필수입니다.");
        }
        try {
            QuestionType.valueOf(questionType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidQuizCreateRequestException("지원하지 않는 퀴즈 유형입니다.");
        }
    }

    private void parseDifficultyLevel(String difficultyLevel) {
        if (difficultyLevel == null || difficultyLevel.isBlank()) {
            throw new InvalidQuizCreateRequestException("난이도는 필수입니다.");
        }
        try {
            DifficultyLevel.valueOf(difficultyLevel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidQuizCreateRequestException("지원하지 않는 난이도입니다.");
        }
    }
}
