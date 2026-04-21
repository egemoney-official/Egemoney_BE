package com.igemoney.igemoney_BE.quiz.service.generate;

import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizCandidateResponse;
import com.igemoney.igemoney_BE.quiz.dto.generate.GeneratedQuizDraft;
import com.igemoney.igemoney_BE.quiz.dto.generate.SimilarQuizResponse;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SimpleQuizSimilarityService implements QuizSimilarityService {

    private static final double SIMILARITY_THRESHOLD = 0.25d;
    private static final int MAX_SIMILAR_QUIZZES = 3;

    private final QuizRepository quizRepository;

    @Override
    public GeneratedQuizCandidateResponse analyzeCandidate(GeneratedQuizDraft draft) {
        List<SimilarQuizResponse> similarQuizzes = quizRepository.findAllByTopicId(draft.topicId()).stream()
            .map(quiz -> toSimilarityCandidate(quiz, draft))
            .filter(candidate -> candidate.similarityScore() >= SIMILARITY_THRESHOLD)
            .sorted(Comparator.comparing(SimilarQuizResponse::similarityScore).reversed())
            .limit(MAX_SIMILAR_QUIZZES)
            .toList();

        boolean exactDuplicate = similarQuizzes.stream()
            .anyMatch(candidate -> candidate.similarityScore() >= 0.9999d);

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

    private SimilarQuizResponse toSimilarityCandidate(Quiz quiz, GeneratedQuizDraft draft) {
        double similarityScore = calculateSimilarity(quiz, draft);
        String similarityReason = buildReason(quiz, draft, similarityScore);
        return SimilarQuizResponse.from(quiz, similarityScore, similarityReason);
    }

    private double calculateSimilarity(Quiz quiz, GeneratedQuizDraft draft) {
        String normalizedDraftTitle = normalizeText(draft.questionTitle());
        String normalizedQuizTitle = normalizeText(quiz.getQuestionTitle());

        if (normalizedDraftTitle.equals(normalizedQuizTitle)) {
            return 1.0d;
        }

        double titleScore = jaccardSimilarity(tokenize(draft.questionTitle()), tokenize(quiz.getQuestionTitle()));
        double explanationScore = jaccardSimilarity(tokenize(draft.explanation()), tokenize(quiz.getExplanation()));
        double typeScore = draft.questionType().equals(quiz.getQuestionType().name()) ? 0.1d : 0.0d;

        return Math.min(0.99d, (titleScore * 0.7d) + (explanationScore * 0.2d) + typeScore);
    }

    private String buildReason(Quiz quiz, GeneratedQuizDraft draft, double similarityScore) {
        if (similarityScore >= 0.9999d) {
            return "같은 토픽에서 제목이 동일한 문제입니다.";
        }
        if (draft.questionType().equals(quiz.getQuestionType().name()) && similarityScore >= 0.6d) {
            return "같은 유형에서 핵심 키워드가 많이 겹치는 문제입니다.";
        }
        if (similarityScore >= 0.4d) {
            return "같은 토픽에서 비슷한 개념을 묻는 문제입니다.";
        }
        return "같은 토픽 내 유사 문제 후보입니다.";
    }

    private Set<String> tokenize(String text) {
        return Stream.of(normalizeText(text).split(" "))
            .filter(token -> !token.isBlank())
            .collect(Collectors.toSet());
    }

    private String normalizeText(String text) {
        return text == null
            ? ""
            : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-z가-힣\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double jaccardSimilarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0d;
        }

        long intersection = left.stream()
            .filter(right::contains)
            .count();

        long union = left.size() + right.size() - intersection;
        if (union == 0L) {
            return 0.0d;
        }
        return (double) intersection / union;
    }
}
