package com.igemoney.igemoney_BE.quiz.dto.common;

import java.util.List;

public record QuizReviewResponse (
        List<ReviewQuizDetail> reviewQuizzes
){
}

