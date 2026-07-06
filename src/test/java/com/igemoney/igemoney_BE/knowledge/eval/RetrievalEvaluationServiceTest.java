package com.igemoney.igemoney_BE.knowledge.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.ChunkStats;
import com.igemoney.igemoney_BE.common.vector.DocumentChunkRecord;
import com.igemoney.igemoney_BE.common.vector.RetrievedChunk;
import com.igemoney.igemoney_BE.common.vector.SimilarQuizHit;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetrievalEvaluationServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @Test
    void evaluatesRetrievalMetricsWithStubEmbeddingAndInMemoryVectorStore() throws Exception {
        when(topicRepository.findAll()).thenReturn(List.of(
            QuizTopic.builder().id(7L).name("경제기초").build()
        ));
        RecordingEmbeddingClient embeddingClient = new RecordingEmbeddingClient();
        RecordingVectorStoreRepository vectorStoreRepository = new RecordingVectorStoreRepository();
        Path reportDirectory = Path.of("build", "tmp", "retrieval-evaluation-test", UUID.randomUUID().toString());
        RetrievalEvaluationService service = new RetrievalEvaluationService(
            new RetrievalEvaluationDatasetLoader(Path.of("missing.yml")),
            embeddingClient,
            vectorStoreRepository,
            topicRepository,
            reportDirectory,
            Clock.fixed(Instant.parse("2026-07-05T05:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
        List<RetrievalEvaluationQuery> queries = List.of(
            new RetrievalEvaluationQuery("q1", "target-a.md", "정답 섹션", "경제기초"),
            new RetrievalEvaluationQuery("q2", "target-b.md", "두 번째 정답", "경제기초")
        );

        RetrievalEvaluationReport report = service.evaluateQueries(List.of(), queries);

        assertThat(embeddingClient.queries()).containsExactly("q1", "q2");
        assertThat(vectorStoreRepository.searchCalls())
            .containsExactly(
                new SearchCall("structure", 1.0f, 7L, 5),
                new SearchCall("fixed", 1.0f, 7L, 5),
                new SearchCall("structure", 2.0f, 7L, 5),
                new SearchCall("fixed", 2.0f, 7L, 5)
            );
        assertThat(report.reportPath()).exists();
        assertThat(Files.readString(report.reportPath())).isEqualTo(report.markdown());
        assertThat(report.markdown())
            .contains("청킹 전략 평가 리포트")
            .contains("권장 `rag.retrieval.strategy` 값은 `structure`")
            .contains("정답 판정");

        RetrievalStrategyMetrics structure = metric(report, "structure");
        assertThat(structure.chunkCount()).isEqualTo(12);
        assertThat(structure.averageCharLength()).isEqualTo(450.0d);
        assertThat(structure.hitAt1()).isEqualTo(0.5d);
        assertThat(structure.hitAt3()).isEqualTo(1.0d);
        assertThat(structure.recallAt5()).isEqualTo(1.0d);
        assertThat(structure.mrrAt5()).isEqualTo(0.75d);
        assertThat(structure.averageTop1Similarity()).isEqualTo(0.8d);

        RetrievalStrategyMetrics fixed = metric(report, "fixed");
        assertThat(fixed.chunkCount()).isEqualTo(8);
        assertThat(fixed.averageCharLength()).isEqualTo(500.0d);
        assertThat(fixed.hitAt1()).isEqualTo(0.5d);
        assertThat(fixed.hitAt3()).isEqualTo(0.5d);
        assertThat(fixed.recallAt5()).isEqualTo(0.5d);
        assertThat(fixed.mrrAt5()).isEqualTo(0.5d);
        assertThat(fixed.averageTop1Similarity()).isEqualTo(0.95d);
    }

    private static RetrievalStrategyMetrics metric(RetrievalEvaluationReport report, String strategy) {
        return report.metrics().stream()
            .filter(metric -> metric.strategy().equals(strategy))
            .findFirst()
            .orElseThrow();
    }

    private static final class RecordingEmbeddingClient implements EmbeddingClient {

        private final List<String> queries = new ArrayList<>();

        @Override
        public float[] embedDocument(String text) {
            return vectorFor(text);
        }

        @Override
        public float[] embedQuery(String text) {
            queries.add(text);
            return vectorFor(text);
        }

        @Override
        public List<float[]> embedAllDocuments(List<String> texts) {
            return texts.stream()
                .map(RecordingEmbeddingClient::vectorFor)
                .toList();
        }

        @Override
        public int dimension() {
            return 1;
        }

        private List<String> queries() {
            return queries;
        }

        private static float[] vectorFor(String text) {
            if ("q1".equals(text)) {
                return new float[] {1.0f};
            }
            if ("q2".equals(text)) {
                return new float[] {2.0f};
            }
            return new float[] {0.0f};
        }
    }

    private static final class RecordingVectorStoreRepository implements VectorStoreRepository {

        private final List<SearchCall> searchCalls = new ArrayList<>();

        @Override
        public void insertChunks(List<DocumentChunkRecord> chunks) {
        }

        @Override
        public List<RetrievedChunk> searchChunks(String strategy, float[] queryEmbedding, Long topicId, int topK) {
            searchCalls.add(new SearchCall(strategy, queryEmbedding[0], topicId, topK));
            if ("structure".equals(strategy) && queryEmbedding[0] == 1.0f) {
                return List.of(
                    new RetrievedChunk("관련 없는 첫 번째 청크", "other.md", "다른 섹션", 0.9d),
                    new RetrievedChunk("정답 섹션 내용", "target-a.md", "정답 섹션", 0.8d),
                    new RetrievedChunk("다른 청크", "other.md", "다른 섹션", 0.7d)
                );
            }
            if ("structure".equals(strategy) && queryEmbedding[0] == 2.0f) {
                return List.of(
                    new RetrievedChunk("두 번째 정답 내용", "target-b.md", "두 번째 정답", 0.7d)
                );
            }
            if ("fixed".equals(strategy) && queryEmbedding[0] == 1.0f) {
                return List.of(
                    new RetrievedChunk("heading 없는 fixed 청크는 sourceFile만으로 판정", "target-a.md", null, 0.95d)
                );
            }
            return List.of();
        }

        @Override
        public int deleteChunks(String strategy, Long topicId) {
            return 0;
        }

        @Override
        public Map<String, ChunkStats> chunkStatsByStrategy() {
            Map<String, ChunkStats> stats = new LinkedHashMap<>();
            stats.put("structure", new ChunkStats(12L, 450.0d));
            stats.put("fixed", new ChunkStats(8L, 500.0d));
            return stats;
        }

        @Override
        public void upsertQuizEmbedding(long quizId, long topicId, String questionTitle, float[] embedding) {
        }

        @Override
        public void deleteQuizEmbedding(long quizId) {
        }

        @Override
        public int deleteAllQuizEmbeddings() {
            return 0;
        }

        @Override
        public List<SimilarQuizHit> searchSimilarQuizzes(float[] embedding, long topicId, int topK) {
            return List.of();
        }

        private List<SearchCall> searchCalls() {
            return searchCalls;
        }
    }

    private record SearchCall(
        String strategy,
        float embeddingKey,
        Long topicId,
        int topK
    ) {
    }
}
