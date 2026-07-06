package com.igemoney.igemoney_BE.knowledge.eval;

import java.nio.file.Path;
import java.util.List;

public record RetrievalEvaluationReport(
    Path reportPath,
    String markdown,
    List<RetrievalStrategyMetrics> metrics
) {
}
