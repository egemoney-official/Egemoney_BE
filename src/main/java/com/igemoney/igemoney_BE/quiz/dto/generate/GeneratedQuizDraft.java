package com.igemoney.igemoney_BE.quiz.dto.generate;

import java.util.List;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizSelectCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.create.QuizSubjectiveCreateRequest;

public record GeneratedQuizDraft(
    Long topicId,
    String questionTitle,
    String questionType,
    String difficultyLevel,
    String explanation,
    List<QuizSelectCreateRequest> selects,
    List<QuizSubjectiveCreateRequest> subjectives
) {
    public QuizCreateRequest toCreateRequest(Integer questionOrder) {
        return new QuizCreateRequest(
            questionTitle,
            questionType,
            difficultyLevel,
            explanation,
            questionOrder,
            null,
            topicId,
            selects,
            subjectives
        );
    }
}
