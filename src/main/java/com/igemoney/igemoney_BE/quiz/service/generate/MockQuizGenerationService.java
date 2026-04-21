package com.igemoney.igemoney_BE.quiz.service.generate;

import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizCreateRequestException;
import com.igemoney.igemoney_BE.common.exception.topic.TopicNotFoundException;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizSelectCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizSubjectiveCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateRequest;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@RequiredArgsConstructor
public class MockQuizGenerationService implements QuizGenerationService {

    private static final int DEFAULT_COUNT = 1;
    private static final int MAX_COUNT = 5;

    private final TopicRepository topicRepository;

    @Override
    public List<GeneratedQuizDraft> generateDrafts(QuizGenerateRequest request) {
        QuizTopic topic = topicRepository.findById(request.topicId())
            .orElseThrow(() -> new TopicNotFoundException(request.topicId()));

        QuestionType questionType = parseQuestionType(request.questionType());
        int count = normalizeCount(request.count());

        List<GeneratedQuizDraft> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(createDraft(topic, questionType, request.difficultyLevel(), request.additionalPrompt(), i + 1));
        }
        return drafts;
    }

    private GeneratedQuizDraft createDraft(
        QuizTopic topic,
        QuestionType questionType,
        String difficultyLevel,
        String additionalPrompt,
        int sequence
    ) {
        return switch (questionType) {
            case OX -> createOxDraft(topic, difficultyLevel, additionalPrompt, sequence);
            case MULTIPLE_CHOICE -> createMultipleChoiceDraft(topic, difficultyLevel, additionalPrompt, sequence);
            case SUBJECTIVE -> createSubjectiveDraft(topic, difficultyLevel, additionalPrompt, sequence);
        };
    }

    private GeneratedQuizDraft createOxDraft(QuizTopic topic, String difficultyLevel, String additionalPrompt, int sequence) {
        String keyword = buildKeyword(topic.getName(), additionalPrompt);
        String questionTitle = switch (variant(sequence)) {
            case 0 -> keyword + "는 " + topic.getName() + " 토픽의 핵심 개념이다.";
            case 1 -> keyword + "는 " + topic.getName() + "에서 반드시 알아야 하는 개념이다.";
            default -> keyword + "는 " + topic.getName() + "의 기본 개념으로 볼 수 있다.";
        };
        String explanation = switch (variant(sequence)) {
            case 0 -> keyword + "와 관련된 기본 개념을 OX 형태로 확인하는 예시 문제입니다.";
            case 1 -> keyword + "에 대한 핵심 개념 이해를 점검하는 OX 예시 문제입니다.";
            default -> keyword + "의 기초 개념을 짧게 판별하는 OX 예시 문제입니다.";
        };

        return new GeneratedQuizDraft(
            topic.getId(),
            questionTitle,
            QuestionType.OX.name(),
            difficultyLevel,
            explanation,
            List.of(
                new QuizSelectCreateRequest(1, "O", true),
                new QuizSelectCreateRequest(2, "X", false)
            ),
            List.of()
        );
    }

    private GeneratedQuizDraft createMultipleChoiceDraft(
        QuizTopic topic,
        String difficultyLevel,
        String additionalPrompt,
        int sequence
    ) {
        String keyword = buildKeyword(topic.getName(), additionalPrompt);
        String questionTitle = switch (variant(sequence)) {
            case 0 -> topic.getName() + "에서 " + keyword + "에 대한 설명으로 가장 적절한 것은?";
            case 1 -> topic.getName() + " 토픽에서 " + keyword + "를 가장 잘 설명한 보기는?";
            default -> topic.getName() + " 학습 내용 중 " + keyword + "와 가장 관련이 깊은 설명은?";
        };
        String explanation = switch (variant(sequence)) {
            case 0 -> keyword + "의 정의와 혼동하기 쉬운 선택지를 함께 제시하는 예시 객관식 문제입니다.";
            case 1 -> keyword + "를 설명하는 보기와 혼동 보기를 함께 배치한 예시 객관식 문제입니다.";
            default -> keyword + "의 핵심 개념과 주변 개념을 구분하는 예시 객관식 문제입니다.";
        };

        return new GeneratedQuizDraft(
            topic.getId(),
            questionTitle,
            QuestionType.MULTIPLE_CHOICE.name(),
            difficultyLevel,
            explanation,
            List.of(
                new QuizSelectCreateRequest(1, keyword + "와 무관한 설명", false),
                new QuizSelectCreateRequest(2, keyword + "의 핵심 개념을 담은 설명", true),
                new QuizSelectCreateRequest(3, keyword + "와 반대되는 개념 설명", false),
                new QuizSelectCreateRequest(4, keyword + "와 혼동하기 쉬운 주변 개념 설명", false)
            ),
            List.of()
        );
    }

    private GeneratedQuizDraft createSubjectiveDraft(
        QuizTopic topic,
        String difficultyLevel,
        String additionalPrompt,
        int sequence
    ) {
        String keyword = buildKeyword(topic.getName(), additionalPrompt);
        String questionTitle = switch (variant(sequence)) {
            case 0 -> topic.getName() + "에서 " + keyword + "를 한 단어로 적어보세요.";
            case 1 -> topic.getName() + " 토픽의 핵심 용어인 " + keyword + "를 직접 작성해보세요.";
            default -> topic.getName() + " 학습 내용 중 " + keyword + "를 서술형으로 입력해보세요.";
        };
        String explanation = switch (variant(sequence)) {
            case 0 -> keyword + "를 직접 서술하게 만드는 예시 주관식 문제입니다.";
            case 1 -> keyword + "를 스스로 작성하면서 개념을 떠올리도록 유도하는 예시 주관식 문제입니다.";
            default -> keyword + "의 핵심 용어를 직접 입력해보는 예시 주관식 문제입니다.";
        };

        return new GeneratedQuizDraft(
            topic.getId(),
            questionTitle,
            QuestionType.SUBJECTIVE.name(),
            difficultyLevel,
            explanation,
            List.of(),
            List.of(
                new QuizSubjectiveCreateRequest(keyword, true),
                new QuizSubjectiveCreateRequest(keyword.replace(" ", ""), false)
            )
        );
    }

    private QuestionType parseQuestionType(String questionType) {
        try {
            return QuestionType.valueOf(questionType.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new InvalidQuizCreateRequestException("지원하지 않는 퀴즈 유형입니다.");
        }
    }

    private int normalizeCount(Integer count) {
        int normalized = count == null ? DEFAULT_COUNT : count;
        if (normalized <= 0) {
            throw new InvalidQuizCreateRequestException("생성 개수는 1개 이상이어야 합니다.");
        }
        if (normalized > MAX_COUNT) {
            throw new InvalidQuizCreateRequestException("한 번에 생성할 수 있는 퀴즈는 최대 5개입니다.");
        }
        return normalized;
    }

    private String buildKeyword(String topicName, String additionalPrompt) {
        String prompt = additionalPrompt == null ? "" : additionalPrompt.trim();
        if (!prompt.isBlank()) {
            return prompt;
        }
        return topicName + " 핵심 개념";
    }

    private int variant(int sequence) {
        return Math.floorMod(sequence - 1, 3);
    }
}
