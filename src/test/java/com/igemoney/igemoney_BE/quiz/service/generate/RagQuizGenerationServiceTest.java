package com.igemoney.igemoney_BE.quiz.service.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizCreateRequestException;
import com.igemoney.igemoney_BE.common.vector.RetrievedChunk;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateRequest;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RagQuizGenerationServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorStoreRepository vectorStoreRepository;

    private RecordingQuizLlmClient quizLlmClient;
    private RagQuizGenerationService service;

    @BeforeEach
    void setUp() {
        quizLlmClient = new RecordingQuizLlmClient();
        service = new RagQuizGenerationService(
            topicRepository,
            embeddingClient,
            vectorStoreRepository,
            quizLlmClient,
            "hybrid",
            3
        );
    }

    @Test
    void includesRetrievedChunksInPromptAndSearchesWithConfiguredStrategy() {
        QuizTopic topic = QuizTopic.builder().id(7L).name("경제기초").build();
        float[] embedding = new float[] {0.25f};
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));
        when(embeddingClient.embedQuery("경제기초 복리")).thenReturn(embedding);
        when(vectorStoreRepository.searchChunks("hybrid", embedding, 7L, 3)).thenReturn(List.of(
            new RetrievedChunk(
                "복리는 원금과 이전에 붙은 이자에 다시 이자가 붙는 방식입니다.",
                "economic-basic__interest-and-prices.md",
                "단리와 복리",
                0.91d
            )
        ));
        quizLlmClient.nextBatch = multipleChoiceBatch("복리에 대한 설명으로 맞는 것은?");

        List<GeneratedQuizDraft> drafts = service.generateDrafts(new QuizGenerateRequest(
            7L,
            "MULTIPLE_CHOICE",
            "EASY",
            1,
            List.of(),
            "복리"
        ));

        verify(embeddingClient).embedQuery("경제기초 복리");
        verify(vectorStoreRepository).searchChunks("hybrid", embedding, 7L, 3);
        assertThat(quizLlmClient.systemPrompt).contains("제공된 검색 컨텍스트");
        assertThat(quizLlmClient.userPrompt)
            .contains("economic-basic__interest-and-prices.md")
            .contains("단리와 복리")
            .contains("복리는 원금과 이전에 붙은 이자")
            .contains("생성 개수: 1");

        assertThat(drafts).hasSize(1);
        GeneratedQuizDraft draft = drafts.getFirst();
        assertThat(draft.topicId()).isEqualTo(7L);
        assertThat(draft.questionType()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(draft.difficultyLevel()).isEqualTo("EASY");
        assertThat(draft.selects()).hasSize(4);
        assertThat(draft.selects()).filteredOn(select -> Boolean.TRUE.equals(select.isAnswer())).hasSize(1);
        assertThat(draft.subjectives()).isEmpty();
    }

    @Test
    void generatesWithNoContextPromptWhenSearchReturnsNoChunks() {
        QuizTopic topic = QuizTopic.builder().id(11L).name("생활경제").build();
        float[] embedding = new float[] {0.7f};
        when(topicRepository.findById(11L)).thenReturn(Optional.of(topic));
        when(embeddingClient.embedQuery("생활경제")).thenReturn(embedding);
        when(vectorStoreRepository.searchChunks("hybrid", embedding, 11L, 3)).thenReturn(List.of());
        quizLlmClient.nextBatch = new QuizLlmClient.QuizDraftBatch(List.of(
            new QuizLlmClient.QuizDraftItem(
                "예금자보호제도란 무엇인가?",
                "SUBJECTIVE",
                "MEDIUM",
                "예금자보호제도는 금융회사가 영업정지나 파산 등으로 예금을 돌려주지 못할 때 예금자를 보호하는 장치입니다.",
                List.of(),
                List.of("예금자보호제도", "예금보험")
            )
        ));

        List<GeneratedQuizDraft> drafts = service.generateDrafts(new QuizGenerateRequest(
            11L,
            "SUBJECTIVE",
            "MEDIUM",
            null,
            List.of(),
            null
        ));

        assertThat(quizLlmClient.systemPrompt).contains("검색 컨텍스트가 비어");
        assertThat(quizLlmClient.userPrompt).contains("[no-context]");
        assertThat(drafts).hasSize(1);
        assertThat(drafts.getFirst().selects()).isEmpty();
        assertThat(drafts.getFirst().subjectives())
            .extracting(subjective -> subjective.answerText())
            .containsExactly("예금자보호제도", "예금보험");
        assertThat(drafts.getFirst().subjectives().getFirst().isDisplayAnswer()).isTrue();
    }

    @Test
    void rejectsCountOverMockLimitBeforeCallingExternalServices() {
        QuizTopic topic = QuizTopic.builder().id(7L).name("경제기초").build();
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> service.generateDrafts(new QuizGenerateRequest(
            7L,
            "OX",
            "EASY",
            6,
            List.of(),
            null
        )))
            .isInstanceOf(InvalidQuizCreateRequestException.class)
            .hasMessageContaining("최대 5개");

        verifyNoInteractions(embeddingClient, vectorStoreRepository);
        assertThat(quizLlmClient.callCount).isZero();
    }

    @Test
    void rejectsLlmResponseWithDifferentQuestionType() {
        QuizTopic topic = QuizTopic.builder().id(7L).name("경제기초").build();
        float[] embedding = new float[] {0.25f};
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));
        when(embeddingClient.embedQuery("경제기초 복리")).thenReturn(embedding);
        when(vectorStoreRepository.searchChunks("hybrid", embedding, 7L, 3)).thenReturn(List.of());
        quizLlmClient.nextBatch = new QuizLlmClient.QuizDraftBatch(List.of(
            new QuizLlmClient.QuizDraftItem(
                "복리는 이자에 다시 이자가 붙는 방식이다.",
                "OX",
                "EASY",
                "복리는 원금뿐 아니라 이미 발생한 이자에도 다시 이자가 붙는 방식입니다.",
                List.of(
                    new QuizLlmClient.QuizChoiceDraft(1, "O", true),
                    new QuizLlmClient.QuizChoiceDraft(2, "X", false)
                ),
                List.of()
            )
        ));

        assertThatThrownBy(() -> service.generateDrafts(new QuizGenerateRequest(
            7L,
            "MULTIPLE_CHOICE",
            "EASY",
            1,
            List.of(),
            "복리"
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different from the request");
    }

    private static QuizLlmClient.QuizDraftBatch multipleChoiceBatch(String questionTitle) {
        return new QuizLlmClient.QuizDraftBatch(List.of(
            new QuizLlmClient.QuizDraftItem(
                questionTitle,
                "MULTIPLE_CHOICE",
                "EASY",
                "복리는 원금과 이미 붙은 이자에 다시 이자가 붙는 방식입니다. 단리와 달리 시간이 지날수록 이자 계산 기준이 커집니다.",
                List.of(
                    new QuizLlmClient.QuizChoiceDraft(1, "원금에만 이자가 붙는 방식", false),
                    new QuizLlmClient.QuizChoiceDraft(2, "원금과 이전 이자에 다시 이자가 붙는 방식", true),
                    new QuizLlmClient.QuizChoiceDraft(3, "물가가 계속 하락하는 현상", false),
                    new QuizLlmClient.QuizChoiceDraft(4, "주식 가격이 하루 동안 변하는 폭", false)
                ),
                List.of()
            )
        ));
    }

    private static final class RecordingQuizLlmClient implements QuizLlmClient {

        private QuizDraftBatch nextBatch;
        private String systemPrompt;
        private String userPrompt;
        private int callCount;

        @Override
        public QuizDraftBatch generate(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            callCount++;
            return nextBatch;
        }
    }
}
