package com.igemoney.igemoney_BE.knowledge.dto;

import java.util.List;

public record KnowledgeEvaluationRequest(
    List<String> strategies
) {
}
