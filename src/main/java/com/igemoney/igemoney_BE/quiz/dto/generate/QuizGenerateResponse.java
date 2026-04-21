package com.igemoney.igemoney_BE.quiz.dto.generate;

import java.time.LocalDateTime;
import java.util.List;

public record QuizGenerateResponse(
    List<GeneratedQuizCandidateResponse> candidates,
    LocalDateTime generatedAt
) {
}
