package com.igemoney.igemoney_BE.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.ChunkStats;
import com.igemoney.igemoney_BE.common.vector.DocumentChunkRecord;
import com.igemoney.igemoney_BE.common.vector.RetrievedChunk;
import com.igemoney.igemoney_BE.common.vector.SimilarQuizHit;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.knowledge.chunking.Chunk;
import com.igemoney.igemoney_BE.knowledge.chunking.ChunkingStrategy;
import com.igemoney.igemoney_BE.knowledge.chunking.ChunkingStrategyRegistry;
import com.igemoney.igemoney_BE.knowledge.chunking.ParsedDocument;
import com.igemoney.igemoney_BE.knowledge.dto.KnowledgeIngestionSummary;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    private Path knowledgeDirectory;

    @Mock
    private TopicRepository topicRepository;

    @BeforeEach
    void setUp() throws Exception {
        knowledgeDirectory = Path.of("build", "tmp", "knowledge-ingestion-test", UUID.randomUUID().toString());
        Files.createDirectories(knowledgeDirectory);
    }

    @Test
    void ingestsEachStrategyByDeletingEmbeddingAndInsertingChunks() throws Exception {
        writeKnowledgeFile(
            "economic-basic__interest.md",
            """
                ---
                source: 한국은행 경제금융용어 요약
                topic: 경제기초
                ---
                ## 단리와 복리
                단리는 원금에 대해서만 이자를 계산한다.
                복리는 이자에도 다시 이자가 붙는 방식이다.
                """
        );
        writeKnowledgeFile(
            "common__budget.md",
            """
                ---
                source: 금융감독원 금융교육 요약
                topic: common
                ---
                ## 예산 관리
                예산은 소득 안에서 저축과 소비 계획을 세우는 일이다.
                """
        );
        when(topicRepository.findAll()).thenReturn(List.of(
            QuizTopic.builder().id(7L).name("경제기초").build()
        ));

        List<String> events = new ArrayList<>();
        RecordingVectorStoreRepository vectorStoreRepository = new RecordingVectorStoreRepository(events);
        RecordingEmbeddingClient embeddingClient = new RecordingEmbeddingClient(events);
        ChunkingStrategyRegistry registry = new ChunkingStrategyRegistry(List.of(
            new EchoStrategy("fixed"),
            new EchoStrategy("semantic")
        ));
        KnowledgeIngestionService service = new KnowledgeIngestionService(
            new KnowledgeDocumentParser(),
            registry,
            embeddingClient,
            vectorStoreRepository,
            topicRepository,
            knowledgeDirectory
        );

        KnowledgeIngestionSummary summary = service.ingest(List.of("fixed", "semantic"));

        assertThat(events).containsExactly(
            "delete:fixed",
            "embed:2",
            "insert:fixed:2",
            "delete:semantic",
            "embed:2",
            "insert:semantic:2"
        );
        assertThat(embeddingClient.batches()).hasSize(2);
        assertThat(vectorStoreRepository.insertedByStrategy().get("fixed"))
            .extracting(DocumentChunkRecord::topicId)
            .containsExactly(null, 7L);
        assertThat(vectorStoreRepository.insertedByStrategy().get("fixed"))
            .extracting(DocumentChunkRecord::sourceFile)
            .containsExactly("common__budget.md", "economic-basic__interest.md");
        assertThat(vectorStoreRepository.insertedByStrategy().get("fixed"))
            .allMatch(record -> record.embedding().length == 2);
        assertThat(summary.documentCount()).isEqualTo(2);
        assertThat(summary.strategies()).containsExactly("fixed", "semantic");
        assertThat(summary.results()).hasSize(2);
        assertThat(summary.results().getFirst().deletedChunks()).isEqualTo(3);
        assertThat(summary.results().getFirst().insertedChunks()).isEqualTo(2);
        assertThat(summary.results().getFirst().embeddingBatchCalls()).isEqualTo(1);
        assertThat(summary.results().getFirst().maxCharLength()).isGreaterThan(0);
    }

    @Test
    void omittedStrategiesIngestsAllRegisteredStrategies() throws Exception {
        writeKnowledgeFile(
            "common__credit.md",
            """
                ---
                source: 공통 자료
                topic: common
                ---
                ## 신용 관리
                신용점수는 대출 상환 이력과 카드 사용 습관의 영향을 받는다.
                """
        );
        when(topicRepository.findAll()).thenReturn(List.of());

        List<String> events = new ArrayList<>();
        ChunkingStrategyRegistry registry = new ChunkingStrategyRegistry(List.of(
            new EchoStrategy("fixed"),
            new EchoStrategy("structure")
        ));
        KnowledgeIngestionService service = new KnowledgeIngestionService(
            new KnowledgeDocumentParser(),
            registry,
            new RecordingEmbeddingClient(events),
            new RecordingVectorStoreRepository(events),
            topicRepository,
            knowledgeDirectory
        );

        KnowledgeIngestionSummary summary = service.ingest(List.of());

        assertThat(summary.strategies()).containsExactly("fixed", "structure");
        assertThat(events).containsExactly(
            "delete:fixed",
            "embed:1",
            "insert:fixed:1",
            "delete:structure",
            "embed:1",
            "insert:structure:1"
        );
    }

    @Test
    void mapsTopicSlugFromFileNameWhenFrontMatterTopicIsAbsent() throws Exception {
        writeKnowledgeFile(
            "economic-basic__slug-topic.md",
            """
                ---
                source: 한국은행 경제금융용어 요약
                ---
                ## 경기와 물가
                경기와 물가는 소비, 투자, 고용 상황을 함께 보며 이해해야 한다.
                """
        );
        when(topicRepository.findAll()).thenReturn(List.of(
            QuizTopic.builder().id(7L).name("경제기초").build()
        ));

        List<String> events = new ArrayList<>();
        RecordingVectorStoreRepository vectorStoreRepository = new RecordingVectorStoreRepository(events);
        KnowledgeIngestionService service = new KnowledgeIngestionService(
            new KnowledgeDocumentParser(),
            new ChunkingStrategyRegistry(List.of(new EchoStrategy("structure"))),
            new RecordingEmbeddingClient(events),
            vectorStoreRepository,
            topicRepository,
            knowledgeDirectory
        );

        service.ingest(List.of("structure"));

        assertThat(vectorStoreRepository.insertedByStrategy().get("structure"))
            .extracting(DocumentChunkRecord::topicId)
            .containsExactly(7L);
    }

    @Test
    void splitsChunkEmbeddingsIntoBatchesAndReportsBatchCalls() throws Exception {
        writeKnowledgeFile(
            "common__many-chunks.md",
            """
                ---
                source: 공통 자료
                topic: common
                ---
                ## 여러 청크
                배치 임베딩 호출 수 검증용 문서이다.
                """
        );
        when(topicRepository.findAll()).thenReturn(List.of());

        List<String> events = new ArrayList<>();
        RecordingEmbeddingClient embeddingClient = new RecordingEmbeddingClient(events);
        KnowledgeIngestionService service = new KnowledgeIngestionService(
            new KnowledgeDocumentParser(),
            new ChunkingStrategyRegistry(List.of(new ManyChunksStrategy("fixed", 129))),
            embeddingClient,
            new RecordingVectorStoreRepository(events),
            topicRepository,
            knowledgeDirectory
        );

        KnowledgeIngestionSummary summary = service.ingest(List.of("fixed"));

        assertThat(embeddingClient.batches()).extracting(List::size)
            .containsExactly(128, 1);
        assertThat(summary.results().getFirst().insertedChunks()).isEqualTo(129);
        assertThat(summary.results().getFirst().embeddingBatchCalls()).isEqualTo(2);
    }

    private void writeKnowledgeFile(String fileName, String content) throws Exception {
        Files.writeString(knowledgeDirectory.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private static final class EchoStrategy implements ChunkingStrategy {

        private final String name;

        private EchoStrategy(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<Chunk> chunk(ParsedDocument document) {
            String heading = document.sections().getFirst().heading();
            String content = name + ":" + document.sections().getFirst().content();
            return List.of(new Chunk(content, heading));
        }
    }

    private static final class ManyChunksStrategy implements ChunkingStrategy {

        private final String name;
        private final int chunkCount;

        private ManyChunksStrategy(String name, int chunkCount) {
            this.name = name;
            this.chunkCount = chunkCount;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<Chunk> chunk(ParsedDocument document) {
            List<Chunk> chunks = new ArrayList<>();
            for (int index = 0; index < chunkCount; index++) {
                chunks.add(new Chunk("청크 " + index + " 내용입니다.", null));
            }
            return chunks;
        }
    }

    private static final class RecordingEmbeddingClient implements EmbeddingClient {

        private final List<String> events;
        private final List<List<String>> batches = new ArrayList<>();

        private RecordingEmbeddingClient(List<String> events) {
            this.events = events;
        }

        @Override
        public float[] embedDocument(String text) {
            return vectorFor(text);
        }

        @Override
        public float[] embedQuery(String text) {
            return vectorFor(text);
        }

        @Override
        public List<float[]> embedAllDocuments(List<String> texts) {
            events.add("embed:" + texts.size());
            batches.add(List.copyOf(texts));
            return texts.stream()
                .map(RecordingEmbeddingClient::vectorFor)
                .toList();
        }

        @Override
        public int dimension() {
            return 2;
        }

        private List<List<String>> batches() {
            return batches;
        }

        private static float[] vectorFor(String text) {
            return new float[] {text.length(), 1.0f};
        }
    }

    private static final class RecordingVectorStoreRepository implements VectorStoreRepository {

        private final List<String> events;
        private final Map<String, List<DocumentChunkRecord>> insertedByStrategy = new LinkedHashMap<>();

        private RecordingVectorStoreRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public void insertChunks(List<DocumentChunkRecord> chunks) {
            String strategy = chunks.isEmpty() ? "unknown" : chunks.getFirst().chunkingStrategy();
            events.add("insert:" + strategy + ":" + chunks.size());
            insertedByStrategy.put(strategy, List.copyOf(chunks));
        }

        @Override
        public List<RetrievedChunk> searchChunks(String strategy, float[] queryEmbedding, Long topicId, int topK) {
            return List.of();
        }

        @Override
        public int deleteChunks(String strategy, Long topicId) {
            events.add("delete:" + strategy);
            return 3;
        }

        @Override
        public Map<String, ChunkStats> chunkStatsByStrategy() {
            return Map.of();
        }

        @Override
        public void upsertQuizEmbedding(long quizId, long topicId, String questionTitle, float[] embedding) {
        }

        @Override
        public void deleteQuizEmbedding(long quizId) {
        }

        @Override
        public List<SimilarQuizHit> searchSimilarQuizzes(float[] embedding, long topicId, int topK) {
            return List.of();
        }

        private Map<String, List<DocumentChunkRecord>> insertedByStrategy() {
            return insertedByStrategy;
        }
    }
}
