package com.igemoney.igemoney_BE.knowledge;

import com.igemoney.igemoney_BE.knowledge.dto.KnowledgeIngestionSummary;
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
public class KnowledgeIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionRunner.class);

    private final ObjectProvider<KnowledgeIngestionService> ingestionServiceProvider;
    private final ConfigurableApplicationContext applicationContext;

    public KnowledgeIngestionRunner(
        ObjectProvider<KnowledgeIngestionService> ingestionServiceProvider,
        ConfigurableApplicationContext applicationContext
    ) {
        this.ingestionServiceProvider = ingestionServiceProvider;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("ingest-knowledge")) {
            return;
        }

        KnowledgeIngestionService ingestionService = ingestionServiceProvider.getIfAvailable();
        if (ingestionService == null) {
            throw new IllegalStateException(
                "Knowledge ingestion requires VECTOR_STORE_ENABLED=true and vector datasource configuration."
            );
        }

        KnowledgeIngestionSummary summary = ingestionService.ingest(parseStrategies(args.getOptionValues("strategies")));
        log.info("Knowledge ingestion summary: {}", summary);
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
