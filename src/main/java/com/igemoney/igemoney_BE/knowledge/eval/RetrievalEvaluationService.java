package com.igemoney.igemoney_BE.knowledge.eval;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.ChunkStats;
import com.igemoney.igemoney_BE.common.vector.RetrievedChunk;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
public class RetrievalEvaluationService {

    private static final int TOP_K = 5;
    private static final int DETAIL_TOP_K = 3;
    private static final Path DEFAULT_REPORT_DIRECTORY = Path.of("docs", "experiments");
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<String, String> TOPIC_ALIASES = topicAliases();

    private final RetrievalEvaluationDatasetLoader datasetLoader;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreRepository vectorStoreRepository;
    private final TopicRepository topicRepository;
    private final Path reportDirectory;
    private final Clock clock;

    public RetrievalEvaluationService(
        RetrievalEvaluationDatasetLoader datasetLoader,
        EmbeddingClient embeddingClient,
        VectorStoreRepository vectorStoreRepository,
        TopicRepository topicRepository
    ) {
        this(
            datasetLoader,
            embeddingClient,
            vectorStoreRepository,
            topicRepository,
            DEFAULT_REPORT_DIRECTORY,
            Clock.systemDefaultZone()
        );
    }

    RetrievalEvaluationService(
        RetrievalEvaluationDatasetLoader datasetLoader,
        EmbeddingClient embeddingClient,
        VectorStoreRepository vectorStoreRepository,
        TopicRepository topicRepository,
        Path reportDirectory,
        Clock clock
    ) {
        this.datasetLoader = datasetLoader;
        this.embeddingClient = embeddingClient;
        this.vectorStoreRepository = vectorStoreRepository;
        this.topicRepository = topicRepository;
        this.reportDirectory = reportDirectory;
        this.clock = clock;
    }

    public RetrievalEvaluationReport evaluate(List<String> strategyNames) {
        List<RetrievalEvaluationQuery> queries = datasetLoader.load();
        if (queries.size() < 20) {
            throw new IllegalStateException("Retrieval evaluation dataset must contain at least 20 queries.");
        }
        return evaluateQueries(strategyNames, queries);
    }

    RetrievalEvaluationReport evaluateQueries(
        List<String> strategyNames,
        List<RetrievalEvaluationQuery> queries
    ) {
        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("Evaluation queries must not be empty.");
        }

        Map<String, ChunkStats> chunkStats = vectorStoreRepository.chunkStatsByStrategy();
        List<String> strategies = resolveStrategies(strategyNames, chunkStats);
        Map<String, Long> topicIdsByKey = loadTopicIdsByKey();

        Map<String, StrategyAccumulator> accumulators = new LinkedHashMap<>();
        for (String strategy : strategies) {
            ChunkStats stats = chunkStats.getOrDefault(strategy, new ChunkStats(0L, 0.0d));
            accumulators.put(strategy, new StrategyAccumulator(strategy, stats));
        }

        List<QueryDetail> queryDetails = new ArrayList<>();
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            RetrievalEvaluationQuery query = queries.get(queryIndex);
            float[] queryEmbedding = embeddingClient.embedQuery(query.query());
            Long topicId = resolveTopicId(query.topic(), topicIdsByKey);

            for (String strategy : strategies) {
                List<RetrievedChunk> hits = vectorStoreRepository.searchChunks(strategy, queryEmbedding, topicId, TOP_K);
                QueryMetrics queryMetrics = calculateQueryMetrics(query, hits);
                accumulators.get(strategy).add(queryMetrics);
                queryDetails.add(new QueryDetail(queryIndex + 1, query, strategy, hits, queryMetrics));
            }
        }

        List<RetrievalStrategyMetrics> metrics = accumulators.values().stream()
            .map(accumulator -> accumulator.toMetrics(queries.size()))
            .toList();
        String markdown = buildMarkdown(metrics, queryDetails, queries.size(), LocalDateTime.now(clock));
        Path reportPath = writeReport(markdown, LocalDateTime.now(clock));
        return new RetrievalEvaluationReport(reportPath, markdown, metrics);
    }

    private static List<String> resolveStrategies(
        List<String> strategyNames,
        Map<String, ChunkStats> chunkStats
    ) {
        List<String> normalized = normalizeStrategyNames(strategyNames);
        if (!normalized.isEmpty()) {
            return normalized;
        }

        List<String> strategies = chunkStats.entrySet().stream()
            .filter(entry -> entry.getValue().chunkCount() > 0)
            .map(Map.Entry::getKey)
            .toList();
        if (strategies.isEmpty()) {
            throw new IllegalStateException("No stored document chunks found for retrieval evaluation.");
        }
        return strategies;
    }

    private Map<String, Long> loadTopicIdsByKey() {
        Map<String, Long> topicIdsByKey = new LinkedHashMap<>();
        for (QuizTopic topic : topicRepository.findAll()) {
            topicIdsByKey.putIfAbsent(topicKey(topic.getName()), topic.getId());
        }
        return topicIdsByKey;
    }

    private static Long resolveTopicId(String topic, Map<String, Long> topicIdsByKey) {
        if (!StringUtils.hasText(topic) || isCommonTopic(topic)) {
            return null;
        }

        String key = topicKey(topic);
        String mappedKey = TOPIC_ALIASES.getOrDefault(key, key);
        Long topicId = topicIdsByKey.get(mappedKey);
        if (topicId == null) {
            throw new IllegalArgumentException("Unknown retrieval evaluation topic: " + topic);
        }
        return topicId;
    }

    private static QueryMetrics calculateQueryMetrics(
        RetrievalEvaluationQuery query,
        List<RetrievedChunk> hits
    ) {
        int firstRelevantRank = 0;
        int limit = Math.min(TOP_K, hits.size());
        for (int index = 0; index < limit; index++) {
            if (isRelevant(query, hits.get(index))) {
                firstRelevantRank = index + 1;
                break;
            }
        }

        boolean hitAt1 = firstRelevantRank == 1;
        boolean hitAt3 = firstRelevantRank > 0 && firstRelevantRank <= 3;
        boolean recallAt5 = firstRelevantRank > 0 && firstRelevantRank <= TOP_K;
        double reciprocalRank = firstRelevantRank == 0 ? 0.0d : 1.0d / firstRelevantRank;
        Double top1Similarity = hits.isEmpty() ? null : hits.getFirst().similarity();
        return new QueryMetrics(hitAt1, hitAt3, recallAt5, reciprocalRank, top1Similarity);
    }

    private static boolean isRelevant(RetrievalEvaluationQuery query, RetrievedChunk hit) {
        if (!query.relevantSourceFile().equals(hit.sourceFile())) {
            return false;
        }
        return query.relevantHeading() == null
            || hit.heading() == null
            || query.relevantHeading().equals(hit.heading());
    }

    private String buildMarkdown(
        List<RetrievalStrategyMetrics> metrics,
        List<QueryDetail> queryDetails,
        int queryCount,
        LocalDateTime now
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 청킹 전략 평가 리포트 (")
            .append(REPORT_TIMESTAMP_FORMATTER.format(now))
            .append(", 질의 ")
            .append(queryCount)
            .append("개, top-k=")
            .append(TOP_K)
            .append(")\n\n");
        builder.append("정답 판정은 `source_file` 일치를 기본으로 하며, 라벨에 heading이 있고 검색 청크에도 heading이 있으면 heading까지 일치해야 합니다. heading이 없는 fixed 계열 청크는 sourceFile 일치만 적용합니다.\n\n");
        builder.append("| 전략 | 청크수 | 평균길이 | Hit@1 | Hit@3 | Recall@5 | MRR@5 | Top-1 유사도 |\n");
        builder.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (RetrievalStrategyMetrics metric : metrics) {
            builder.append("| ")
                .append(metric.strategy())
                .append(" | ")
                .append(metric.chunkCount())
                .append(" | ")
                .append(formatNumber(metric.averageCharLength()))
                .append(" | ")
                .append(formatNumber(metric.hitAt1()))
                .append(" | ")
                .append(formatNumber(metric.hitAt3()))
                .append(" | ")
                .append(formatNumber(metric.recallAt5()))
                .append(" | ")
                .append(formatNumber(metric.mrrAt5()))
                .append(" | ")
                .append(formatNumber(metric.averageTop1Similarity()))
                .append(" |\n");
        }

        builder.append("\n## 질의별 상세 (전략별 top-3 청크와 정답 여부)\n\n");
        int currentQuestion = 0;
        for (QueryDetail detail : queryDetails) {
            if (detail.queryIndex() != currentQuestion) {
                currentQuestion = detail.queryIndex();
                RetrievalEvaluationQuery query = detail.query();
                builder.append("### Q")
                    .append(currentQuestion)
                    .append(". ")
                    .append(query.query())
                    .append("\n\n");
                builder.append("- 정답 라벨: `")
                    .append(query.relevantSourceFile())
                    .append("`");
                if (query.relevantHeading() != null) {
                    builder.append(" / `").append(query.relevantHeading()).append("`");
                }
                if (query.topic() != null) {
                    builder.append(" / topic `").append(query.topic()).append("`");
                }
                builder.append("\n");
            }

            builder.append("- ")
                .append(detail.strategy())
                .append(": ");
            if (detail.hits().isEmpty()) {
                builder.append("검색 결과 없음\n");
                continue;
            }
            builder.append("\n");
            int detailCount = Math.min(DETAIL_TOP_K, detail.hits().size());
            for (int index = 0; index < detailCount; index++) {
                RetrievedChunk hit = detail.hits().get(index);
                builder.append("  ")
                    .append(index + 1)
                    .append(". ")
                    .append(isRelevant(detail.query(), hit) ? "정답" : "오답")
                    .append(" `")
                    .append(hit.sourceFile())
                    .append("`");
                if (hit.heading() != null) {
                    builder.append(" / `").append(hit.heading()).append("`");
                }
                builder.append(" (similarity=")
                    .append(formatNumber(hit.similarity()))
                    .append(") ")
                    .append(preview(hit.content()))
                    .append("\n");
            }
        }

        builder.append("\n## 실험 절차\n\n");
        builder.append("1. `--ingest-knowledge`로 6개 전략 전체를 적재합니다.\n");
        builder.append("2. `--evaluate-chunking`을 실행해 이 리포트를 생성합니다.\n");
        builder.append("3. 최고 전략을 `RAG_CHUNKING_STRATEGY` 값으로 채택합니다.\n");
        builder.append("4. 선택적으로 상위 2개 전략의 실제 퀴즈 품질을 팀원이 루브릭으로 블라인드 평가합니다.\n\n");

        builder.append("## 결론\n\n");
        RetrievalStrategyMetrics best = selectBestMetric(metrics);
        if (best == null) {
            builder.append("평가 가능한 전략이 없습니다.\n");
        } else {
            builder.append("권장 `rag.retrieval.strategy` 값은 `")
                .append(best.strategy())
                .append("`입니다. 선정 기준은 MRR@5, Recall@5, Hit@3, Hit@1 순 정렬입니다.\n");
        }
        return builder.toString();
    }

    private Path writeReport(String markdown, LocalDateTime now) {
        try {
            Files.createDirectories(reportDirectory);
            Path reportPath = reportDirectory.resolve(
                "chunking-eval-" + FILE_TIMESTAMP_FORMATTER.format(now) + ".md"
            );
            Files.writeString(reportPath, markdown, StandardCharsets.UTF_8);
            return reportPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write retrieval evaluation report.", exception);
        }
    }

    private static RetrievalStrategyMetrics selectBestMetric(List<RetrievalStrategyMetrics> metrics) {
        return metrics.stream()
            .max(Comparator
                .comparingDouble(RetrievalStrategyMetrics::mrrAt5)
                .thenComparingDouble(RetrievalStrategyMetrics::recallAt5)
                .thenComparingDouble(RetrievalStrategyMetrics::hitAt3)
                .thenComparingDouble(RetrievalStrategyMetrics::hitAt1)
                .thenComparing(RetrievalStrategyMetrics::strategy))
            .orElse(null);
    }

    private static List<String> normalizeStrategyNames(Collection<String> strategyNames) {
        if (strategyNames == null) {
            return List.of();
        }
        return strategyNames.stream()
            .filter(value -> value != null)
            .flatMap(value -> Stream.of(value.split(",")))
            .map(String::strip)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
    }

    private static boolean isCommonTopic(String topic) {
        String key = topicKey(topic);
        return key.isEmpty() || "common".equals(key) || "공통".equals(key);
    }

    private static Map<String, String> topicAliases() {
        Map<String, String> aliases = new HashMap<>();
        registerAlias(aliases, "economic-basic", "경제기초");
        registerAlias(aliases, "economy-basic", "경제기초");
        registerAlias(aliases, "economics-basic", "경제기초");
        registerAlias(aliases, "stock-investment", "주식투자");
        registerAlias(aliases, "stocks", "주식투자");
        registerAlias(aliases, "stock", "주식투자");
        registerAlias(aliases, "global-economy", "글로벌 경제");
        registerAlias(aliases, "global", "글로벌 경제");
        registerAlias(aliases, "living-economy", "생활 경제");
        registerAlias(aliases, "life-economy", "생활 경제");
        registerAlias(aliases, "real-estate", "부동산");
        registerAlias(aliases, "realestate", "부동산");
        return Map.copyOf(aliases);
    }

    private static void registerAlias(Map<String, String> aliases, String alias, String topicName) {
        aliases.put(topicKey(alias), topicKey(topicName));
    }

    private static String topicKey(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        value.strip().toLowerCase(Locale.ROOT).codePoints()
            .filter(Character::isLetterOrDigit)
            .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "...";
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record QueryMetrics(
        boolean hitAt1,
        boolean hitAt3,
        boolean recallAt5,
        double reciprocalRank,
        Double top1Similarity
    ) {
    }

    private record QueryDetail(
        int queryIndex,
        RetrievalEvaluationQuery query,
        String strategy,
        List<RetrievedChunk> hits,
        QueryMetrics metrics
    ) {
    }

    private static final class StrategyAccumulator {

        private final String strategy;
        private final ChunkStats stats;
        private int hitAt1Count;
        private int hitAt3Count;
        private int recallAt5Count;
        private double reciprocalRankSum;
        private double top1SimilaritySum;
        private int top1SimilarityCount;

        private StrategyAccumulator(String strategy, ChunkStats stats) {
            this.strategy = strategy;
            this.stats = stats;
        }

        private void add(QueryMetrics metrics) {
            if (metrics.hitAt1()) {
                hitAt1Count++;
            }
            if (metrics.hitAt3()) {
                hitAt3Count++;
            }
            if (metrics.recallAt5()) {
                recallAt5Count++;
            }
            reciprocalRankSum += metrics.reciprocalRank();
            if (metrics.top1Similarity() != null) {
                top1SimilaritySum += metrics.top1Similarity();
                top1SimilarityCount++;
            }
        }

        private RetrievalStrategyMetrics toMetrics(int queryCount) {
            return new RetrievalStrategyMetrics(
                strategy,
                stats.chunkCount(),
                stats.averageCharLength(),
                ratio(hitAt1Count, queryCount),
                ratio(hitAt3Count, queryCount),
                ratio(recallAt5Count, queryCount),
                ratio(reciprocalRankSum, queryCount),
                top1SimilarityCount == 0 ? 0.0d : top1SimilaritySum / top1SimilarityCount
            );
        }

        private static double ratio(double numerator, int denominator) {
            if (denominator == 0) {
                return 0.0d;
            }
            return numerator / denominator;
        }
    }
}
