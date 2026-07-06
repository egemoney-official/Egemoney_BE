package com.igemoney.igemoney_BE.quiz.service.generate;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.SimilarQuizHit;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizCandidateResponse;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.dto.generate.SimilarQuizResponse;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quiz.generation.mode", havingValue = "rag")
@Transactional(readOnly = true)
public class VectorQuizSimilarityService implements QuizSimilarityService {

    private static final double SIMILARITY_THRESHOLD = 0.80d;
    private static final double EXACT_DUPLICATE_THRESHOLD = 0.95d;
    private static final int MAX_SIMILAR_QUIZZES = 3;

    private final EmbeddingClient embeddingClient;
    private final VectorStoreRepository vectorStoreRepository;
    private final QuizRepository quizRepository;

    @Override
    public GeneratedQuizCandidateResponse analyzeCandidate(GeneratedQuizDraft draft) {
        float[] embedding = embeddingClient.embedQuery(draft.questionTitle());
        List<SimilarQuizHit> hits = vectorStoreRepository.searchSimilarQuizzes(
            embedding,
            draft.topicId(),
            MAX_SIMILAR_QUIZZES
        );
        List<SimilarQuizHit> relevantHits = hits.stream()
            .filter(hit -> hit.similarity() >= SIMILARITY_THRESHOLD)
            .toList();

        Map<Long, Quiz> quizzesById = findQuizzesById(relevantHits.stream()
            .map(SimilarQuizHit::quizId)
            .toList());

        List<SimilarQuizResponse> similarQuizzes = relevantHits.stream()
            .map(hit -> toSimilarityResponse(hit, quizzesById.get(hit.quizId())))
            .filter(response -> response != null)
            .toList();

        boolean exactDuplicate = similarQuizzes.stream()
            .anyMatch(candidate -> candidate.similarityScore() >= EXACT_DUPLICATE_THRESHOLD);
        Double maxSimilarityScore = similarQuizzes.stream()
            .map(SimilarQuizResponse::similarityScore)
            .max(Double::compareTo)
            .orElse(null);

        return new GeneratedQuizCandidateResponse(draft, similarQuizzes, exactDuplicate, maxSimilarityScore);
    }

    @Override
    public List<GeneratedQuizCandidateResponse> analyzeCandidates(List<GeneratedQuizDraft> drafts) {
        return drafts.stream()
            .map(this::analyzeCandidate)
            .toList();
    }

    private Map<Long, Quiz> findQuizzesById(Collection<Long> quizIds) {
        if (quizIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Quiz> quizzesById = new LinkedHashMap<>();
        quizRepository.findAllById(quizIds).forEach(quiz -> quizzesById.put(quiz.getId(), quiz));
        return quizzesById;
    }

    private SimilarQuizResponse toSimilarityResponse(SimilarQuizHit hit, Quiz quiz) {
        if (quiz == null) {
            return null;
        }
        return SimilarQuizResponse.from(quiz, hit.similarity(), buildReason(hit.similarity()));
    }

    private String buildReason(double similarityScore) {
        if (similarityScore >= EXACT_DUPLICATE_THRESHOLD) {
            return "같은 토픽에서 제목 의미가 거의 동일한 문제입니다.";
        }
        return "같은 토픽에서 질문 의도가 매우 유사한 문제입니다.";
    }
}
