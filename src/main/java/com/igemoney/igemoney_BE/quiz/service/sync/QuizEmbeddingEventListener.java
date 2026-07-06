package com.igemoney.igemoney_BE.quiz.service.sync;

import com.igemoney.igemoney_BE.quiz.event.QuizEmbeddingDeleteRequestedEvent;
import com.igemoney.igemoney_BE.quiz.event.QuizEmbeddingUpsertRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
@Slf4j
public class QuizEmbeddingEventListener {

    private final QuizEmbeddingSyncService quizEmbeddingSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUpsert(QuizEmbeddingUpsertRequestedEvent event) {
        try {
            quizEmbeddingSyncService.upsertById(event.quizId());
        } catch (RuntimeException exception) {
            log.warn("Failed to sync quiz embedding after quiz save. quizId={}", event.quizId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDelete(QuizEmbeddingDeleteRequestedEvent event) {
        try {
            quizEmbeddingSyncService.delete(event.quizId());
        } catch (RuntimeException exception) {
            log.warn("Failed to delete quiz embedding after quiz delete. quizId={}", event.quizId(), exception);
        }
    }
}
