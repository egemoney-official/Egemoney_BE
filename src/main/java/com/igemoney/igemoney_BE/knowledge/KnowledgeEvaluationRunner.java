package com.igemoney.igemoney_BE.knowledge;

import com.igemoney.igemoney_BE.knowledge.eval.RetrievalEvaluationReport;
import com.igemoney.igemoney_BE.knowledge.eval.RetrievalEvaluationService;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeEvaluationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEvaluationRunner.class);

    private final ObjectProvider<RetrievalEvaluationService> evaluationServiceProvider;
    private final ConfigurableApplicationContext applicationContext;

    public KnowledgeEvaluationRunner(
        ObjectProvider<RetrievalEvaluationService> evaluationServiceProvider,
        ConfigurableApplicationContext applicationContext
    ) {
        this.evaluationServiceProvider = evaluationServiceProvider;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("evaluate-chunking")) {
            return;
        }

        RetrievalEvaluationService evaluationService = evaluationServiceProvider.getIfAvailable();
        if (evaluationService == null) {
            throw new IllegalStateException(
                "Chunking evaluation requires VECTOR_STORE_ENABLED=true and vector datasource configuration."
            );
        }

        RetrievalEvaluationReport report = evaluationService.evaluate(parseStrategies(args.getOptionValues("strategies")));
        log.info("Chunking evaluation report written: {}", report.reportPath());
        applicationContext.close();
    }

    private static List<String> parseStrategies(List<String> optionValues) {
        if (optionValues == null) {
            return List.of();
        }

        return optionValues.stream()
            .filter(value -> value != null)
            .flatMap(value -> Stream.of(value.split(",")))
            .map(String::strip)
            .filter(value -> !value.isEmpty())
            .toList();
    }
}
