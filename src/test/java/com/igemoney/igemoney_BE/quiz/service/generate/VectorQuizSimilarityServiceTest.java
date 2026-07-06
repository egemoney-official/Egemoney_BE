package com.igemoney.igemoney_BE.quiz.service.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.SimilarQuizHit;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizCandidateResponse;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.entity.enums.DifficultyLevel;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
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
class VectorQuizSimilarityServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorStoreRepository vectorStoreRepository;

    @Mock
    private QuizRepository quizRepository;

    private VectorQuizSimilarityService service;

    @BeforeEach
    void setUp() {
        service = new VectorQuizSimilarityService(embeddingClient, vectorStoreRepository, quizRepository);
    }

    @Test
    void mapsVectorHitsAboveThresholdAndMarksExactDuplicate() {
        GeneratedQuizDraft draft = draft("복리란 무엇인가?");
        float[] embedding = new float[] {0.3f, 0.7f};
        when(embeddingClient.embedQuery("복리란 무엇인가?")).thenReturn(embedding);
        when(vectorStoreRepository.searchSimilarQuizzes(embedding, 7L, 3)).thenReturn(List.of(
            new SimilarQuizHit(101L, 7L, "복리의 뜻은 무엇인가?", 0.96d),
            new SimilarQuizHit(102L, 7L, "단리와 복리의 차이는?", 0.83d),
            new SimilarQuizHit(103L, 7L, "예금자보호제도란?", 0.79d)
        ));
        when(quizRepository.findAllById(List.of(101L, 102L))).thenReturn(List.of(
            quiz(101L, "복리의 뜻은 무엇인가?", QuestionType.SUBJECTIVE),
            quiz(102L, "단리와 복리의 차이는?", QuestionType.MULTIPLE_CHOICE)
        ));

        GeneratedQuizCandidateResponse response = service.analyzeCandidate(draft);

        verify(embeddingClient).embedQuery("복리란 무엇인가?");
        verify(vectorStoreRepository).searchSimilarQuizzes(embedding, 7L, 3);
        assertThat(response.similarQuizzes()).hasSize(2);
        assertThat(response.exactDuplicate()).isTrue();
        assertThat(response.maxSimilarityScore()).isEqualTo(0.96d);
        assertThat(response.similarQuizzes())
            .extracting(similar -> similar.quizId())
            .containsExactly(101L, 102L);
        assertThat(response.similarQuizzes().getFirst().similarityReason())
            .isEqualTo("같은 토픽에서 제목 의미가 거의 동일한 문제입니다.");
        assertThat(response.similarQuizzes().get(1).similarityReason())
            .isEqualTo("같은 토픽에서 질문 의도가 매우 유사한 문제입니다.");
    }

    @Test
    void ignoresHitsBelowSimilarityThreshold() {
        GeneratedQuizDraft draft = draft("예금자보호 한도는 얼마인가?");
        float[] embedding = new float[] {0.1f};
        when(embeddingClient.embedQuery("예금자보호 한도는 얼마인가?")).thenReturn(embedding);
        when(vectorStoreRepository.searchSimilarQuizzes(embedding, 7L, 3)).thenReturn(List.of(
            new SimilarQuizHit(201L, 7L, "금리란 무엇인가?", 0.79d)
        ));

        GeneratedQuizCandidateResponse response = service.analyzeCandidate(draft);

        assertThat(response.similarQuizzes()).isEmpty();
        assertThat(response.exactDuplicate()).isFalse();
        assertThat(response.maxSimilarityScore()).isNull();
        verifyNoInteractions(quizRepository);
    }

    @Test
    void skipsStaleVectorHitsMissingFromMysql() {
        GeneratedQuizDraft draft = draft("주식과 채권의 차이는?");
        float[] embedding = new float[] {0.2f};
        when(embeddingClient.embedQuery("주식과 채권의 차이는?")).thenReturn(embedding);
        when(vectorStoreRepository.searchSimilarQuizzes(embedding, 7L, 3)).thenReturn(List.of(
            new SimilarQuizHit(301L, 7L, "주식과 채권은 어떻게 다른가?", 0.91d)
        ));
        when(quizRepository.findAllById(anyCollection())).thenReturn(List.of());

        GeneratedQuizCandidateResponse response = service.analyzeCandidate(draft);

        assertThat(response.similarQuizzes()).isEmpty();
        assertThat(response.exactDuplicate()).isFalse();
        assertThat(response.maxSimilarityScore()).isNull();
    }

    private static GeneratedQuizDraft draft(String questionTitle) {
        return new GeneratedQuizDraft(
            7L,
            questionTitle,
            QuestionType.MULTIPLE_CHOICE.name(),
            DifficultyLevel.EASY.name(),
            "해설",
            List.of(),
            List.of()
        );
    }

    private static Quiz quiz(Long id, String questionTitle, QuestionType questionType) {
        QuizTopic topic = QuizTopic.builder()
            .id(7L)
            .name("경제기초")
            .build();
        Quiz quiz = Quiz.builder()
            .topic(topic)
            .questionTitle(questionTitle)
            .questionType(questionType)
            .difficultyLevel(DifficultyLevel.EASY)
            .explanation("해설")
            .questionOrder(1)
            .build();
        ReflectionTestUtils.setField(quiz, "id", id);
        return quiz;
    }
}
