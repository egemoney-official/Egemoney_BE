package com.igemoney.igemoney_BE.quiz.service.create;

import com.igemoney.igemoney_BE.quiz.dto.common.QuizResponse;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizCreateRequest;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.common.exception.topic.TopicNotFoundException;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class QuizCreateService {

    private static final int FIRST_QUESTION_ORDER = 1;

    private final QuizRepository quizRepository;
    private final TopicRepository topicRepository;
    private final QuizCreateRequestValidator quizCreateRequestValidator;

    public QuizResponse createQuiz(QuizCreateRequest request) {
        quizCreateRequestValidator.validate(request);

        QuizTopic topic = topicRepository.findById(request.topicId())
            .orElseThrow(() -> new TopicNotFoundException(request.topicId()));

        QuizCreateRequest requestWithOrder = request.withQuestionOrder(nextQuestionOrder(topic.getId()));
        Quiz quiz = QuizCreateRequest.toEntity(requestWithOrder, topic);
        Quiz savedQuiz = quizRepository.save(quiz);

        return QuizResponse.from(savedQuiz, false, false);
    }

    private int nextQuestionOrder(Long topicId) {
        Integer currentMax = quizRepository.findMaxQuestionOrderByTopicId(topicId);
        return currentMax == null ? FIRST_QUESTION_ORDER : currentMax + 1;
    }
}
