package com.igemoney.igemoney_BE.quiz.service.generate;

import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateRequest;
import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateResponse;

public interface AdminQuizGenerationService {

    QuizGenerateResponse generate(QuizGenerateRequest request);
}
