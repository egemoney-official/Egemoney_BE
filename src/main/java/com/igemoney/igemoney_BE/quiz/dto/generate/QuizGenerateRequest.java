package com.igemoney.igemoney_BE.quiz.dto.generate;

import java.util.List;

public record QuizGenerateRequest(
    Long topicId,
    String questionType,
    String difficultyLevel,
    Integer count,
    List<Long> referenceDocumentIds,
    String additionalPrompt
) {
}
