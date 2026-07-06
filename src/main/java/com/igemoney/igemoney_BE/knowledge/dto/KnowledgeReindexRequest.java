package com.igemoney.igemoney_BE.knowledge.dto;

import java.util.List;

public record KnowledgeReindexRequest(
    List<String> strategies
) {
}
