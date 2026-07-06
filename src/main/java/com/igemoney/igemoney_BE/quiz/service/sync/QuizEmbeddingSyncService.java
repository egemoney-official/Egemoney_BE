package com.igemoney.igemoney_BE.quiz.service.sync;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.exception.quiz.QuizNotFoundException;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.quiz.dto.sync.QuizEmbeddingReindexResponse;
import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
public class QuizEmbeddingSyncService {

    private final EmbeddingClient embeddingClient;
    private final VectorStoreRepository vectorStoreRepository;
    private final QuizRepository quizRepository;

    public void upsert(Quiz quiz) {
        if (quiz == null || quiz.getId() == null) {
            return;
        }

        float[] embedding = embeddingClient.embedDocument(quiz.getQuestionTitle());
        vectorStoreRepository.upsertQuizEmbedding(
            quiz.getId(),
            quiz.getTopic().getId(),
            quiz.getQuestionTitle(),
            embedding
        );
    }

    @Transactional(readOnly = true)
    public void upsertById(Long quizId) {
        if (quizId == null) {
            return;
        }

        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(QuizNotFoundException::new);
        upsert(quiz);
    }

    public void delete(Long quizId) {
        if (quizId == null) {
            return;
        }
        vectorStoreRepository.deleteQuizEmbedding(quizId);
    }

    @Transactional(readOnly = true)
    public QuizEmbeddingReindexResponse reindexAll() {
        int deletedEmbeddings = vectorStoreRepository.deleteAllQuizEmbeddings();
        List<Quiz> quizzes = quizRepository.findAll();
        if (quizzes.isEmpty()) {
            return new QuizEmbeddingReindexResponse(deletedEmbeddings, 0);
        }

        List<String> questionTitles = quizzes.stream()
            .map(Quiz::getQuestionTitle)
            .toList();
        List<float[]> embeddings = embeddingClient.embedAllDocuments(questionTitles);
        if (embeddings.size() != quizzes.size()) {
            throw new IllegalStateException("Quiz embedding response size does not match quiz count.");
        }

        for (int index = 0; index < quizzes.size(); index++) {
            Quiz quiz = quizzes.get(index);
            vectorStoreRepository.upsertQuizEmbedding(
                quiz.getId(),
                quiz.getTopic().getId(),
                quiz.getQuestionTitle(),
                embeddings.get(index)
            );
        }

        return new QuizEmbeddingReindexResponse(deletedEmbeddings, quizzes.size());
    }
}
