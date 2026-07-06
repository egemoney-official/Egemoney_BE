package com.igemoney.igemoney_BE.knowledge.eval;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class RetrievalEvaluationDatasetLoader {

    private static final Path DEFAULT_EVALUATION_FILE = Path.of("data", "eval", "retrieval-eval.yml");

    private final Path evaluationFile;

    public RetrievalEvaluationDatasetLoader() {
        this(DEFAULT_EVALUATION_FILE);
    }

    RetrievalEvaluationDatasetLoader(Path evaluationFile) {
        this.evaluationFile = evaluationFile;
    }

    public List<RetrievalEvaluationQuery> load() {
        if (!Files.isRegularFile(evaluationFile)) {
            throw new IllegalStateException("Retrieval evaluation dataset does not exist: " + evaluationFile);
        }

        try (Reader reader = Files.newBufferedReader(evaluationFile, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (!(loaded instanceof List<?> items)) {
                throw new IllegalStateException("Retrieval evaluation dataset must be a YAML list: " + evaluationFile);
            }
            return parseItems(items);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read retrieval evaluation dataset: " + evaluationFile, exception);
        }
    }

    private static List<RetrievalEvaluationQuery> parseItems(List<?> items) {
        List<RetrievalEvaluationQuery> queries = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Each retrieval evaluation item must be a map.");
            }
            queries.add(new RetrievalEvaluationQuery(
                stringValue(map, "query"),
                stringValue(map, "relevantSourceFile"),
                stringValue(map, "relevantHeading"),
                stringValue(map, "topic")
            ));
        }
        return List.copyOf(queries);
    }

    private static String stringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
