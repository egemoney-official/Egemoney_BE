package com.igemoney.igemoney_BE.knowledge.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalEvaluationDatasetLoaderTest {

    @Test
    void loadsAtLeastTwentyGoldenQueriesFromRepositoryDataset() {
        RetrievalEvaluationDatasetLoader loader = new RetrievalEvaluationDatasetLoader(
            Path.of("data", "eval", "retrieval-eval.yml")
        );

        List<RetrievalEvaluationQuery> queries = loader.load();

        assertThat(queries).hasSizeGreaterThanOrEqualTo(20);
        assertThat(queries)
            .extracting(RetrievalEvaluationQuery::relevantSourceFile)
            .contains(
                "economic-basic__interest-and-prices.md",
                "living-economy__budget-credit-protection.md",
                "global-economy__exchange-trade.md",
                "stock-investment__stock-bond-fund.md",
                "real-estate__housing-loan-rent.md"
            );
        assertThat(queries)
            .allSatisfy(query -> {
                assertThat(query.query()).isNotBlank();
                assertThat(query.relevantSourceFile()).isNotBlank();
                assertThat(query.relevantHeading()).isNotBlank();
                assertThat(query.topic()).isNotBlank();
            });
    }
}
