package com.igemoney.igemoney_BE.quiz.service.generate;

import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizCandidateResponse;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import java.util.List;

public interface QuizSimilarityService {

    GeneratedQuizCandidateResponse analyzeCandidate(GeneratedQuizDraft draft);

    List<GeneratedQuizCandidateResponse> analyzeCandidates(List<GeneratedQuizDraft> drafts);
}
