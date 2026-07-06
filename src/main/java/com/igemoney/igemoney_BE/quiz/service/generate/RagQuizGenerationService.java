package com.igemoney.igemoney_BE.quiz.service.generate;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizCreateRequestException;
import com.igemoney.igemoney_BE.common.exception.topic.TopicNotFoundException;
import com.igemoney.igemoney_BE.common.vector.RetrievedChunk;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizSelectCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizSubjectiveCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateRequest;
import com.igemoney.igemoney_BE.quiz.entity.enums.DifficultyLevel;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "quiz.generation.mode", havingValue = "rag")
public class RagQuizGenerationService implements QuizGenerationService {

    private static final int DEFAULT_COUNT = 1;
    private static final int MAX_COUNT = 5;

    private final TopicRepository topicRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreRepository vectorStoreRepository;
    private final QuizLlmClient quizLlmClient;
    private final String retrievalStrategy;
    private final int retrievalTopK;

    public RagQuizGenerationService(
        TopicRepository topicRepository,
        EmbeddingClient embeddingClient,
        VectorStoreRepository vectorStoreRepository,
        QuizLlmClient quizLlmClient,
        @Value("${rag.retrieval.strategy:structure}") String retrievalStrategy,
        @Value("${rag.retrieval.top-k:6}") int retrievalTopK
    ) {
        this.topicRepository = topicRepository;
        this.embeddingClient = embeddingClient;
        this.vectorStoreRepository = vectorStoreRepository;
        this.quizLlmClient = quizLlmClient;
        this.retrievalStrategy = retrievalStrategy;
        this.retrievalTopK = retrievalTopK;
    }

    @Override
    public List<GeneratedQuizDraft> generateDrafts(QuizGenerateRequest request) {
        QuizTopic topic = topicRepository.findById(request.topicId())
            .orElseThrow(() -> new TopicNotFoundException(request.topicId()));
        QuestionType requestedQuestionType = parseQuestionType(request.questionType());
        DifficultyLevel requestedDifficultyLevel = parseDifficultyLevel(request.difficultyLevel());
        int requestedCount = normalizeCount(request.count());

        String queryText = buildQueryText(topic.getName(), request.additionalPrompt());
        float[] queryEmbedding = embeddingClient.embedQuery(queryText);
        List<RetrievedChunk> chunks = vectorStoreRepository.searchChunks(
            retrievalStrategy,
            queryEmbedding,
            topic.getId(),
            retrievalTopK
        );
        if (chunks.isEmpty()) {
            log.warn(
                "no-context: no RAG chunks found for topicId={}, strategy={}, topK={}",
                topic.getId(),
                retrievalStrategy,
                retrievalTopK
            );
        }

        QuizLlmClient.QuizDraftBatch batch = quizLlmClient.generate(
            buildSystemPrompt(chunks.isEmpty()),
            buildUserPrompt(topic, requestedQuestionType, requestedDifficultyLevel, requestedCount, request.additionalPrompt(), chunks)
        );
        return mapDrafts(batch, topic.getId(), requestedQuestionType, requestedDifficultyLevel, requestedCount);
    }

    private String buildSystemPrompt(boolean noContext) {
        String contextRule = noContext
            ? "검색 컨텍스트가 비어 있으면 토픽의 일반 금융 지식을 사용하되, 추측성 세부 수치나 출처 없는 정책명은 만들지 않는다."
            : "제공된 검색 컨텍스트의 내용만 근거로 문제를 만든다. 컨텍스트에 없는 내용은 지어내지 않는다.";

        return """
            당신은 금융문맹 해소를 위한 한국어 금융 퀴즈 출제자입니다.
            %s
            설명은 정답 근거가 드러나도록 2~3문장으로 작성합니다.
            요청한 문제 수만큼 서로 겹치지 않는 개념으로 출제합니다.
            """.formatted(contextRule);
    }

    private String buildUserPrompt(
        QuizTopic topic,
        QuestionType questionType,
        DifficultyLevel difficultyLevel,
        int count,
        String additionalPrompt,
        List<RetrievedChunk> chunks
    ) {
        return """
            토픽 ID: %d
            토픽명: %s
            추가 요청: %s
            문제 유형: %s
            난이도: %s
            생성 개수: %d

            유형별 규칙:
            - OX: choices는 O/X 두 개이며 정답은 정확히 하나입니다.
            - MULTIPLE_CHOICE: choices는 네 개이며 정답은 정확히 하나입니다.
            - SUBJECTIVE: choices는 비우고 subjectiveAnswers에 정답 문자열을 하나 이상 둡니다.

            검색 컨텍스트:
            %s
            """.formatted(
            topic.getId(),
            topic.getName(),
            blankToDefault(additionalPrompt, "(없음)"),
            questionType.name(),
            difficultyLevel.name(),
            count,
            formatChunks(chunks)
        );
    }

    private String formatChunks(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "[no-context] 검색된 청크가 없습니다.";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            builder.append("[").append(i + 1).append("] ")
                .append("sourceFile=").append(chunk.sourceFile())
                .append(", heading=").append(blankToDefault(chunk.heading(), "(없음)"))
                .append(", similarity=").append(String.format(Locale.ROOT, "%.4f", chunk.similarity()))
                .append(System.lineSeparator())
                .append(chunk.content())
                .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private List<GeneratedQuizDraft> mapDrafts(
        QuizLlmClient.QuizDraftBatch batch,
        Long topicId,
        QuestionType requestedQuestionType,
        DifficultyLevel requestedDifficultyLevel,
        int requestedCount
    ) {
        if (batch == null || batch.quizzes() == null) {
            throw new IllegalStateException("Claude quiz generation response has no quizzes.");
        }
        if (batch.quizzes().size() < requestedCount) {
            throw new IllegalStateException("Claude returned fewer quizzes than requested.");
        }

        return batch.quizzes().stream()
            .limit(requestedCount)
            .map(item -> mapDraft(item, topicId, requestedQuestionType, requestedDifficultyLevel))
            .toList();
    }

    private GeneratedQuizDraft mapDraft(
        QuizLlmClient.QuizDraftItem item,
        Long topicId,
        QuestionType requestedQuestionType,
        DifficultyLevel requestedDifficultyLevel
    ) {
        validateRequiredText(item.questionTitle(), "생성된 문제 제목이 비어 있습니다.");
        validateRequiredText(item.explanation(), "생성된 해설이 비어 있습니다.");
        validateResponseType(item.questionType(), requestedQuestionType);
        validateResponseDifficulty(item.difficultyLevel(), requestedDifficultyLevel);

        return switch (requestedQuestionType) {
            case OX -> new GeneratedQuizDraft(
                topicId,
                item.questionTitle(),
                requestedQuestionType.name(),
                requestedDifficultyLevel.name(),
                item.explanation(),
                mapChoices(item.choices(), 2, true),
                List.of()
            );
            case MULTIPLE_CHOICE -> new GeneratedQuizDraft(
                topicId,
                item.questionTitle(),
                requestedQuestionType.name(),
                requestedDifficultyLevel.name(),
                item.explanation(),
                mapChoices(item.choices(), 4, false),
                List.of()
            );
            case SUBJECTIVE -> new GeneratedQuizDraft(
                topicId,
                item.questionTitle(),
                requestedQuestionType.name(),
                requestedDifficultyLevel.name(),
                item.explanation(),
                List.of(),
                mapSubjectives(item.subjectiveAnswers())
            );
        };
    }

    private List<QuizSelectCreateRequest> mapChoices(
        List<QuizLlmClient.QuizChoiceDraft> choices,
        int expectedSize,
        boolean requireOxLabels
    ) {
        if (choices == null || choices.size() != expectedSize) {
            throw new IllegalStateException("Claude returned an invalid choice count.");
        }

        List<QuizLlmClient.QuizChoiceDraft> normalizedChoices = choices.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(choice -> choice.sequence() == null ? Integer.MAX_VALUE : choice.sequence()))
            .toList();
        if (normalizedChoices.size() != expectedSize) {
            throw new IllegalStateException("Claude returned an invalid choice.");
        }

        long answerCount = normalizedChoices.stream()
            .filter(choice -> Boolean.TRUE.equals(choice.answer()))
            .count();
        if (answerCount != 1L) {
            throw new IllegalStateException("Claude returned choices without exactly one answer.");
        }
        if (requireOxLabels && !hasOxLabels(normalizedChoices)) {
            throw new IllegalStateException("Claude returned OX choices without O/X labels.");
        }

        return IntStream.range(0, normalizedChoices.size())
            .mapToObj(index -> {
                QuizLlmClient.QuizChoiceDraft choice = normalizedChoices.get(index);
                validateRequiredText(choice.content(), "생성된 보기가 비어 있습니다.");
                return new QuizSelectCreateRequest(index + 1, choice.content().trim(), Boolean.TRUE.equals(choice.answer()));
            })
            .toList();
    }

    private List<QuizSubjectiveCreateRequest> mapSubjectives(List<String> subjectiveAnswers) {
        if (subjectiveAnswers == null || subjectiveAnswers.stream().allMatch(answer -> answer == null || answer.isBlank())) {
            throw new IllegalStateException("Claude returned no subjective answers.");
        }

        List<String> normalizedAnswers = subjectiveAnswers.stream()
            .filter(answer -> answer != null && !answer.isBlank())
            .map(String::trim)
            .toList();

        return IntStream.range(0, normalizedAnswers.size())
            .mapToObj(index -> new QuizSubjectiveCreateRequest(normalizedAnswers.get(index), index == 0))
            .toList();
    }

    private void validateResponseType(String responseQuestionType, QuestionType requestedQuestionType) {
        QuestionType parsedResponseType = parseQuestionType(responseQuestionType);
        if (parsedResponseType != requestedQuestionType) {
            throw new IllegalStateException("Claude returned a quiz type different from the request.");
        }
    }

    private void validateResponseDifficulty(String responseDifficultyLevel, DifficultyLevel requestedDifficultyLevel) {
        DifficultyLevel parsedResponseDifficulty = parseDifficultyLevel(responseDifficultyLevel);
        if (parsedResponseDifficulty != requestedDifficultyLevel) {
            throw new IllegalStateException("Claude returned a difficulty different from the request.");
        }
    }

    private QuestionType parseQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            throw new InvalidQuizCreateRequestException("퀴즈 유형은 필수입니다.");
        }
        try {
            return QuestionType.valueOf(questionType.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new InvalidQuizCreateRequestException("지원하지 않는 퀴즈 유형입니다.");
        }
    }

    private DifficultyLevel parseDifficultyLevel(String difficultyLevel) {
        if (difficultyLevel == null || difficultyLevel.isBlank()) {
            throw new InvalidQuizCreateRequestException("난이도는 필수입니다.");
        }
        try {
            return DifficultyLevel.valueOf(difficultyLevel.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new InvalidQuizCreateRequestException("지원하지 않는 난이도입니다.");
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

    private String buildQueryText(String topicName, String additionalPrompt) {
        return (topicName + " " + blankToDefault(additionalPrompt, "")).trim();
    }

    private boolean hasOxLabels(List<QuizLlmClient.QuizChoiceDraft> choices) {
        List<String> labels = choices.stream()
            .map(QuizLlmClient.QuizChoiceDraft::content)
            .filter(Objects::nonNull)
            .map(label -> label.trim().toUpperCase(Locale.ROOT))
            .toList();
        return labels.contains("O") && labels.contains("X");
    }

    private void validateRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
