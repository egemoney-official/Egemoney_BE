package com.igemoney.igemoney_BE.quiz.dto.generate;

import java.util.List;

public record GeneratedQuizCandidateResponse(
    GeneratedQuizDraft draft,
    List<SimilarQuizResponse> similarQuizzes,
    boolean exactDuplicate,
    Double maxSimilarityScore
) {
}
