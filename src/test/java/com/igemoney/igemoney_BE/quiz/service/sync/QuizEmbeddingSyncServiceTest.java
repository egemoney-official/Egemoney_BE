package com.igemoney.igemoney_BE.quiz.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.quiz.dto.sync.QuizEmbeddingReindexResponse;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.entity.enums.DifficultyLevel;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import com.igemoney.igemoney_BE.quiz.event.QuizEmbeddingDeleteRequestedEvent;
import com.igemoney.igemoney_BE.quiz.event.QuizEmbeddingUpsertRequestedEvent;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizEmbeddingSyncServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorStoreRepository vectorStoreRepository;

    @Mock
    private QuizRepository quizRepository;

    private QuizEmbeddingSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new QuizEmbeddingSyncService(embeddingClient, vectorStoreRepository, quizRepository);
    }

    @Test
    void upsertEmbedsQuestionTitleAndUpsertsVectorRecord() {
        Quiz quiz = quiz(10L, 3L, "복리란 무엇인가?");
        float[] embedding = new float[] {0.4f, 0.6f};
        when(embeddingClient.embedDocument("복리란 무엇인가?")).thenReturn(embedding);

        syncService.upsert(quiz);

        verify(embeddingClient).embedDocument("복리란 무엇인가?");
        verify(vectorStoreRepository).upsertQuizEmbedding(10L, 3L, "복리란 무엇인가?", embedding);
    }

    @Test
    void upsertSkipsUnsavedQuiz() {
        syncService.upsert(quiz(null, 3L, "저장 전 문제"));

        verifyNoInteractions(embeddingClient, vectorStoreRepository);
    }

    @Test
    void deleteRemovesVectorRecord() {
        syncService.delete(10L);

        verify(vectorStoreRepository).deleteQuizEmbedding(10L);
    }

    @Test
    void reindexAllRebuildsQuizEmbeddingsFromMysqlQuizzes() {
        Quiz first = quiz(10L, 3L, "복리란 무엇인가?");
        Quiz second = quiz(11L, 4L, "예금자보호 한도는?");
        float[] firstEmbedding = new float[] {0.1f};
        float[] secondEmbedding = new float[] {0.2f};
        when(vectorStoreRepository.deleteAllQuizEmbeddings()).thenReturn(5);
        when(quizRepository.findAll()).thenReturn(List.of(first, second));
        when(embeddingClient.embedAllDocuments(List.of("복리란 무엇인가?", "예금자보호 한도는?")))
            .thenReturn(List.of(firstEmbedding, secondEmbedding));

        QuizEmbeddingReindexResponse response = syncService.reindexAll();

        assertThat(response.deletedEmbeddings()).isEqualTo(5);
        assertThat(response.indexedQuizzes()).isEqualTo(2);
        verify(vectorStoreRepository).upsertQuizEmbedding(10L, 3L, "복리란 무엇인가?", firstEmbedding);
        verify(vectorStoreRepository).upsertQuizEmbedding(11L, 4L, "예금자보호 한도는?", secondEmbedding);
    }

    @Test
    void reindexAllSkipsEmbeddingCallWhenThereAreNoQuizzes() {
        when(vectorStoreRepository.deleteAllQuizEmbeddings()).thenReturn(2);
        when(quizRepository.findAll()).thenReturn(List.of());

        QuizEmbeddingReindexResponse response = syncService.reindexAll();

        assertThat(response.deletedEmbeddings()).isEqualTo(2);
        assertThat(response.indexedQuizzes()).isZero();
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void eventListenerDoesNotPropagateSyncFailures() {
        QuizEmbeddingEventListener listener = new QuizEmbeddingEventListener(syncService);
        doThrow(new IllegalStateException("vector store down"))
            .when(vectorStoreRepository).deleteQuizEmbedding(10L);

        assertThatCode(() -> listener.handleDelete(new QuizEmbeddingDeleteRequestedEvent(10L)))
            .doesNotThrowAnyException();
    }

    @Test
    void eventListenerDelegatesUpsertById() {
        QuizEmbeddingEventListener listener = new QuizEmbeddingEventListener(syncService);
        Quiz quiz = quiz(10L, 3L, "복리란 무엇인가?");
        float[] embedding = new float[] {0.5f};
        when(quizRepository.findById(10L)).thenReturn(java.util.Optional.of(quiz));
        when(embeddingClient.embedDocument("복리란 무엇인가?")).thenReturn(embedding);

        listener.handleUpsert(new QuizEmbeddingUpsertRequestedEvent(10L));

        verify(vectorStoreRepository).upsertQuizEmbedding(10L, 3L, "복리란 무엇인가?", embedding);
    }

    private static Quiz quiz(Long id, Long topicId, String questionTitle) {
        QuizTopic topic = QuizTopic.builder()
            .id(topicId)
            .name("경제기초")
            .build();
        Quiz quiz = Quiz.builder()
            .topic(topic)
            .questionTitle(questionTitle)
            .questionType(QuestionType.MULTIPLE_CHOICE)
            .difficultyLevel(DifficultyLevel.EASY)
            .explanation("해설")
            .questionOrder(1)
            .build();
        ReflectionTestUtils.setField(quiz, "id", id);
        return quiz;
    }
}
