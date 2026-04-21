package com.igemoney.igemoney_BE.quiz.service.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.igemoney.igemoney_BE.quiz.dto.common.QuizResponse;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateRequest;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import com.igemoney.igemoney_BE.quiz.service.create.QuizCreateRequestValidator;
import com.igemoney.igemoney_BE.quiz.service.create.QuizCreateService;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizGenerateCreateContractTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizCreateRequestValidator quizCreateRequestValidator;

    @InjectMocks
    private MockQuizGenerationService mockQuizGenerationService;

    @InjectMocks
    private QuizCreateService quizCreateService;

    @Test
    @DisplayName("generate draft는 create 요청으로 변환되어 별도 confirm API 없이 저장 흐름에 재사용된다")
    void generatedDraftCanBeReusedByCreateApi() {
        QuizTopic topic = QuizTopic.builder()
            .id(1L)
            .name("경제기초")
            .build();

        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(quizRepository.findMaxQuestionOrderByTopicId(1L)).thenReturn(3);
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuizGenerateRequest generateRequest = new QuizGenerateRequest(
            1L,
            "OX",
            "EASY",
            1,
            List.of(),
            "GDP"
        );

        GeneratedQuizDraft draft = mockQuizGenerationService.generateDrafts(generateRequest).getFirst();
        QuizCreateRequest createRequest = draft.toCreateRequest(null);

        QuizResponse response = quizCreateService.createQuiz(createRequest);

        ArgumentCaptor<Quiz> captor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(captor.capture());
        verify(quizCreateRequestValidator).validate(createRequest);

        Quiz savedQuiz = captor.getValue();
        assertThat(savedQuiz.getQuestionTitle()).isEqualTo(draft.questionTitle());
        assertThat(savedQuiz.getQuestionType().name()).isEqualTo(draft.questionType());
        assertThat(savedQuiz.getDifficultyLevel().name()).isEqualTo(draft.difficultyLevel());
        assertThat(savedQuiz.getExplanation()).isEqualTo(draft.explanation());
        assertThat(savedQuiz.getSelects()).hasSize(2);
        assertThat(savedQuiz.getQuestionOrder()).isEqualTo(4);

        assertThat(response.questionTitle()).isEqualTo(draft.questionTitle());
        assertThat(response.questionType()).isEqualTo(draft.questionType());
        assertThat(response.questionOrder()).isEqualTo(4);
    }
}
