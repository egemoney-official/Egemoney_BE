package com.igemoney.igemoney_BE.quiz.service.generate;

import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.dto.generate.QuizGenerateRequest;
import java.util.List;

public interface QuizGenerationService {

    List<GeneratedQuizDraft> generateDrafts(QuizGenerateRequest request);
}
