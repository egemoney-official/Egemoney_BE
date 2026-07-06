package com.igemoney.igemoney_BE.knowledge.controller;

import com.igemoney.igemoney_BE.common.annotation.Authenticated;
import com.igemoney.igemoney_BE.knowledge.KnowledgeIngestionService;
import com.igemoney.igemoney_BE.knowledge.dto.KnowledgeEvaluationRequest;
import com.igemoney.igemoney_BE.knowledge.dto.KnowledgeIngestionSummary;
import com.igemoney.igemoney_BE.knowledge.dto.KnowledgeReindexRequest;
import com.igemoney.igemoney_BE.knowledge.eval.RetrievalEvaluationService;
import com.igemoney.igemoney_BE.quiz.dto.sync.QuizEmbeddingReindexResponse;
import com.igemoney.igemoney_BE.quiz.service.sync.QuizEmbeddingSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Authenticated
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
@Tag(name = "Admin Knowledge", description = "관리자 지식 문서 관리 API")
public class KnowledgeAdminController {

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final RetrievalEvaluationService retrievalEvaluationService;
    private final QuizEmbeddingSyncService quizEmbeddingSyncService;

    @PostMapping("/reindex")
    @Operation(summary = "지식 문서 벡터 재색인")
    public KnowledgeIngestionSummary reindex(@RequestBody(required = false) KnowledgeReindexRequest request) {
        List<String> strategies = request == null ? List.of() : request.strategies();
        return knowledgeIngestionService.ingest(strategies);
    }

    @PostMapping(value = "/evaluate", produces = "text/markdown;charset=UTF-8")
    @Operation(summary = "청킹 전략 검색 품질 평가")
    public String evaluate(@RequestBody(required = false) KnowledgeEvaluationRequest request) {
        List<String> strategies = request == null ? List.of() : request.strategies();
        return retrievalEvaluationService.evaluate(strategies).markdown();
    }

    @PostMapping("/reindex-quizzes")
    @Operation(summary = "퀴즈 벡터 임베딩 재색인")
    public QuizEmbeddingReindexResponse reindexQuizzes() {
        return quizEmbeddingSyncService.reindexAll();
    }
}
