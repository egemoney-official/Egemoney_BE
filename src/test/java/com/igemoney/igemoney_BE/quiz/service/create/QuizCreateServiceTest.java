package com.igemoney.igemoney_BE.quiz.service.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.igemoney.igemoney_BE.quiz.dto.common.QuizResponse;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizSelectCreateRequest;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
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
class QuizCreateServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private QuizCreateRequestValidator quizCreateRequestValidator;

    @InjectMocks
    private QuizCreateService quizCreateService;

    @Test
    @DisplayName("같은 토픽의 기존 문제가 없으면 questionOrder를 1로 부여한다")
    void createQuizAssignsFirstQuestionOrder() {
        QuizCreateRequest request = createOxRequest(99);
        QuizTopic topic = QuizTopic.builder()
            .id(1L)
            .name("경제기초")
            .build();

        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(quizRepository.findMaxQuestionOrderByTopicId(1L)).thenReturn(null);
        when(quizRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QuizResponse response = quizCreateService.createQuiz(request);

        ArgumentCaptor<com.igemoney.igemoney_BE.quiz.entity.Quiz> captor =
            ArgumentCaptor.forClass(com.igemoney.igemoney_BE.quiz.entity.Quiz.class);
        verify(quizRepository).save(captor.capture());
        verify(quizCreateRequestValidator).validate(request);

        assertThat(captor.getValue().getQuestionOrder()).isEqualTo(1);
        assertThat(response.questionOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("클라이언트가 보낸 순번과 무관하게 같은 토픽의 최대 questionOrder 다음 값을 부여한다")
    void createQuizAssignsNextQuestionOrderFromCurrentMax() {
        QuizCreateRequest request = createOxRequest(42);
        QuizTopic topic = QuizTopic.builder()
            .id(1L)
            .name("경제기초")
            .build();

        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(quizRepository.findMaxQuestionOrderByTopicId(1L)).thenReturn(7);
        when(quizRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QuizResponse response = quizCreateService.createQuiz(request);

        ArgumentCaptor<com.igemoney.igemoney_BE.quiz.entity.Quiz> captor =
            ArgumentCaptor.forClass(com.igemoney.igemoney_BE.quiz.entity.Quiz.class);
        verify(quizRepository).save(captor.capture());

        assertThat(captor.getValue().getQuestionOrder()).isEqualTo(8);
        assertThat(response.questionOrder()).isEqualTo(8);
    }

    private QuizCreateRequest createOxRequest(Integer questionOrder) {
        return new QuizCreateRequest(
            "GDP는 국내총생산이다.",
            "OX",
            "EASY",
            "GDP는 국내총생산을 의미합니다.",
            questionOrder,
            null,
            1L,
            List.of(
                new QuizSelectCreateRequest(1, "O", true),
                new QuizSelectCreateRequest(2, "X", false)
            ),
            List.of()
        );
    }
}
