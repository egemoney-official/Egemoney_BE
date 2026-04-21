package com.igemoney.igemoney_BE.quiz.dto.admin;

import java.util.List;
import org.springframework.data.domain.Page;

public record AdminQuizListResponse(
    List<AdminQuizSummaryResponse> quizzes,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
    public static AdminQuizListResponse from(Page<AdminQuizSummaryResponse> pageResult) {
        return new AdminQuizListResponse(
            pageResult.getContent(),
            pageResult.getNumber(),
            pageResult.getSize(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages(),
            pageResult.hasNext()
        );
    }
}
