package com.igemoney.igemoney_BE.quiz.service.generate;

import java.util.List;

public interface QuizLlmClient {

    QuizDraftBatch generate(String systemPrompt, String userPrompt);

    record QuizChoiceDraft(
        Integer sequence,
        String content,
        Boolean answer
    ) {
    }

    record QuizDraftItem(
        String questionTitle,
        String questionType,
        String difficultyLevel,
        String explanation,
        List<QuizChoiceDraft> choices,
        List<String> subjectiveAnswers
    ) {
    }

    record QuizDraftBatch(List<QuizDraftItem> quizzes) {
    }
}
