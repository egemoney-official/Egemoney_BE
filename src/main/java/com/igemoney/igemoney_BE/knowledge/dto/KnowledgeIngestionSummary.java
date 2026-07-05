package com.igemoney.igemoney_BE.knowledge.dto;

import java.util.List;

public record KnowledgeIngestionSummary(
    int documentCount,
    List<String> strategies,
    List<KnowledgeStrategyIngestionResult> results
) {

    public KnowledgeIngestionSummary {
        strategies = List.copyOf(strategies == null ? List.of() : strategies);
        results = List.copyOf(results == null ? List.of() : results);
    }
}
