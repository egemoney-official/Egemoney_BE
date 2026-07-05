package com.igemoney.igemoney_BE.knowledge;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.DocumentChunkRecord;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.knowledge.chunking.Chunk;
import com.igemoney.igemoney_BE.knowledge.chunking.ChunkingStrategy;
import com.igemoney.igemoney_BE.knowledge.chunking.ChunkingStrategyRegistry;
import com.igemoney.igemoney_BE.knowledge.chunking.ParsedDocument;
import com.igemoney.igemoney_BE.knowledge.dto.KnowledgeIngestionSummary;
import com.igemoney.igemoney_BE.knowledge.dto.KnowledgeStrategyIngestionResult;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final Path DEFAULT_KNOWLEDGE_DIRECTORY = Path.of("data", "knowledge");
    private static final int EMBEDDING_BATCH_SIZE = 128;
    private static final Map<String, String> TOPIC_ALIASES = topicAliases();

    private final KnowledgeDocumentParser documentParser;
    private final ChunkingStrategyRegistry strategyRegistry;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreRepository vectorStoreRepository;
    private final TopicRepository topicRepository;
    private final Path knowledgeDirectory;

    public KnowledgeIngestionService(
        KnowledgeDocumentParser documentParser,
        ChunkingStrategyRegistry strategyRegistry,
        EmbeddingClient embeddingClient,
        VectorStoreRepository vectorStoreRepository,
        TopicRepository topicRepository
    ) {
        this(
            documentParser,
            strategyRegistry,
            embeddingClient,
            vectorStoreRepository,
            topicRepository,
            DEFAULT_KNOWLEDGE_DIRECTORY
        );
    }

    KnowledgeIngestionService(
        KnowledgeDocumentParser documentParser,
        ChunkingStrategyRegistry strategyRegistry,
        EmbeddingClient embeddingClient,
        VectorStoreRepository vectorStoreRepository,
        TopicRepository topicRepository,
        Path knowledgeDirectory
    ) {
        this.documentParser = documentParser;
        this.strategyRegistry = strategyRegistry;
        this.embeddingClient = embeddingClient;
        this.vectorStoreRepository = vectorStoreRepository;
        this.topicRepository = topicRepository;
        this.knowledgeDirectory = knowledgeDirectory;
    }

    public KnowledgeIngestionSummary ingest(List<String> strategyNames) {
        List<ParsedDocument> documents = loadDocuments();
        Map<String, Long> topicIdsByKey = loadTopicIdsByKey();
        List<ChunkingStrategy> strategies = resolveStrategies(strategyNames);

        List<KnowledgeStrategyIngestionResult> results = new ArrayList<>();
        for (ChunkingStrategy strategy : strategies) {
            results.add(ingestStrategy(strategy, documents, topicIdsByKey));
        }

        return new KnowledgeIngestionSummary(
            documents.size(),
            strategies.stream().map(ChunkingStrategy::name).toList(),
            results
        );
    }

    private KnowledgeStrategyIngestionResult ingestStrategy(
        ChunkingStrategy strategy,
        List<ParsedDocument> documents,
        Map<String, Long> topicIdsByKey
    ) {
        int deletedChunks = vectorStoreRepository.deleteChunks(strategy.name(), null);
        List<ChunkDraft> drafts = chunkDocuments(strategy, documents, topicIdsByKey);
        EmbeddingResult embeddingResult = embedChunks(drafts);
        List<DocumentChunkRecord> records = toRecords(strategy.name(), drafts, embeddingResult.embeddings());

        vectorStoreRepository.insertChunks(records);

        KnowledgeStrategyIngestionResult result = summarize(
            strategy.name(),
            documents.size(),
            deletedChunks,
            drafts,
            embeddingResult.batchCalls()
        );
        log.info(
            "Knowledge ingestion completed. strategy={}, documents={}, deletedChunks={}, insertedChunks={}, averageCharLength={}, maxCharLength={}, embeddingBatchCalls={}",
            result.strategy(),
            result.documentCount(),
            result.deletedChunks(),
            result.insertedChunks(),
            result.averageCharLength(),
            result.maxCharLength(),
            result.embeddingBatchCalls()
        );
        return result;
    }

    private List<ParsedDocument> loadDocuments() {
        if (!Files.isDirectory(knowledgeDirectory)) {
            throw new IllegalStateException("Knowledge document directory does not exist: " + knowledgeDirectory);
        }

        try (Stream<Path> paths = Files.list(knowledgeDirectory)) {
            List<Path> markdownFiles = paths
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
            if (markdownFiles.isEmpty()) {
                throw new IllegalStateException("No knowledge markdown files found in: " + knowledgeDirectory);
            }
            return markdownFiles.stream()
                .map(documentParser::parse)
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list knowledge documents: " + knowledgeDirectory, exception);
        }
    }

    private Map<String, Long> loadTopicIdsByKey() {
        Map<String, Long> topicIdsByKey = new LinkedHashMap<>();
        for (QuizTopic topic : topicRepository.findAll()) {
            topicIdsByKey.putIfAbsent(topicKey(topic.getName()), topic.getId());
        }
        return topicIdsByKey;
    }

    private List<ChunkingStrategy> resolveStrategies(List<String> strategyNames) {
        List<String> names = normalizeStrategyNames(strategyNames);
        if (names.isEmpty()) {
            return strategyRegistry.all();
        }
        return names.stream()
            .map(strategyRegistry::get)
            .toList();
    }

    private List<ChunkDraft> chunkDocuments(
        ChunkingStrategy strategy,
        List<ParsedDocument> documents,
        Map<String, Long> topicIdsByKey
    ) {
        List<ChunkDraft> drafts = new ArrayList<>();
        for (ParsedDocument document : documents) {
            Long topicId = resolveTopicId(document, topicIdsByKey);
            for (Chunk chunk : strategy.chunk(document)) {
                drafts.add(new ChunkDraft(
                    topicId,
                    document.sourceFile(),
                    chunk.heading(),
                    chunk.content(),
                    document.source(),
                    chunk.charLength()
                ));
            }
        }
        return drafts;
    }

    private EmbeddingResult embedChunks(List<ChunkDraft> drafts) {
        if (drafts.isEmpty()) {
            return new EmbeddingResult(List.of(), 0);
        }

        List<String> contents = drafts.stream().map(ChunkDraft::content).toList();
        List<float[]> embeddings = new ArrayList<>();
        int batchCalls = 0;
        for (int start = 0; start < contents.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, contents.size());
            embeddings.addAll(embeddingClient.embedAllDocuments(contents.subList(start, end)));
            batchCalls++;
        }

        if (embeddings.size() != drafts.size()) {
            throw new IllegalStateException("Embedding count does not match chunk count.");
        }
        return new EmbeddingResult(embeddings, batchCalls);
    }

    private static List<DocumentChunkRecord> toRecords(
        String strategyName,
        List<ChunkDraft> drafts,
        List<float[]> embeddings
    ) {
        List<DocumentChunkRecord> records = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            ChunkDraft draft = drafts.get(index);
            records.add(new DocumentChunkRecord(
                strategyName,
                draft.topicId(),
                draft.sourceFile(),
                draft.heading(),
                draft.content(),
                draft.source(),
                draft.charLength(),
                embeddings.get(index)
            ));
        }
        return records;
    }

    private static KnowledgeStrategyIngestionResult summarize(
        String strategyName,
        int documentCount,
        int deletedChunks,
        List<ChunkDraft> drafts,
        int embeddingBatchCalls
    ) {
        double averageCharLength = drafts.stream()
            .mapToInt(ChunkDraft::charLength)
            .average()
            .orElse(0.0d);
        int maxCharLength = drafts.stream()
            .mapToInt(ChunkDraft::charLength)
            .max()
            .orElse(0);
        return new KnowledgeStrategyIngestionResult(
            strategyName,
            documentCount,
            deletedChunks,
            drafts.size(),
            averageCharLength,
            maxCharLength,
            embeddingBatchCalls
        );
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

    private static Long resolveTopicId(ParsedDocument document, Map<String, Long> topicIdsByKey) {
        String topic = topicCandidate(document);
        if (topic == null || isCommonTopic(topic)) {
            return null;
        }

        String key = topicKey(topic);
        String mappedKey = TOPIC_ALIASES.getOrDefault(key, key);
        Long topicId = topicIdsByKey.get(mappedKey);
        if (topicId == null) {
            throw new IllegalArgumentException(
                "Unknown knowledge topic '" + topic + "' in " + document.sourceFile()
            );
        }
        return topicId;
    }

    private static String topicCandidate(ParsedDocument document) {
        if (document.topicSlug() != null) {
            return document.topicSlug();
        }

        String sourceFile = document.sourceFile();
        int separator = sourceFile.indexOf("__");
        if (separator <= 0) {
            return null;
        }
        return sourceFile.substring(0, separator);
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

    private record ChunkDraft(
        Long topicId,
        String sourceFile,
        String heading,
        String content,
        String source,
        int charLength
    ) {
    }

    private record EmbeddingResult(
        List<float[]> embeddings,
        int batchCalls
    ) {
    }
}
