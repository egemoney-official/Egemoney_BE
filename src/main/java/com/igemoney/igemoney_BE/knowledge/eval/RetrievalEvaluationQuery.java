package com.igemoney.igemoney_BE.knowledge.eval;

import org.springframework.util.StringUtils;

public record RetrievalEvaluationQuery(
    String query,
    String relevantSourceFile,
    String relevantHeading,
    String topic
) {

    public RetrievalEvaluationQuery {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("Evaluation query must not be blank.");
        }
        if (!StringUtils.hasText(relevantSourceFile)) {
            throw new IllegalArgumentException("Relevant source file must not be blank.");
        }
        query = query.strip();
        relevantSourceFile = relevantSourceFile.strip();
        relevantHeading = normalizeOptional(relevantHeading);
        topic = normalizeOptional(topic);
    }

    private static String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.strip();
    }
}
