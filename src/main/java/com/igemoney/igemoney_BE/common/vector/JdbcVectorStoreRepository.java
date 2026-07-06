package com.igemoney.igemoney_BE.common.vector;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
public class JdbcVectorStoreRepository implements VectorStoreRepository {

    private static final String INSERT_CHUNKS_SQL = """
        insert into document_chunks (
          chunking_strategy, topic_id, source_file, heading, content, source, char_length, embedding
        ) values (?, ?, ?, ?, ?, ?, ?, ?::vector)
        """;

    private static final String SEARCH_CHUNKS_SQL = """
        select content, source_file, heading, 1 - (embedding <=> ?::vector) as similarity
        from document_chunks
        where chunking_strategy = ?
          and (? is null or topic_id = ? or topic_id is null)
        order by embedding <=> ?::vector
        limit ?
        """;

    private static final String DELETE_CHUNKS_SQL = """
        delete from document_chunks
        where chunking_strategy = ?
          and (? is null or topic_id = ?)
        """;

    private static final String CHUNK_STATS_SQL = """
        select chunking_strategy, count(*) as chunk_count, coalesce(avg(char_length), 0) as average_char_length
        from document_chunks
        group by chunking_strategy
        order by chunking_strategy
        """;

    private static final String UPSERT_QUIZ_EMBEDDING_SQL = """
        insert into quiz_embeddings (quiz_id, topic_id, question_title, embedding, updated_at)
        values (?, ?, ?, ?::vector, now())
        on conflict (quiz_id) do update set
          topic_id = excluded.topic_id,
          question_title = excluded.question_title,
          embedding = excluded.embedding,
          updated_at = now()
        """;

    private static final String DELETE_QUIZ_EMBEDDING_SQL = """
        delete from quiz_embeddings
        where quiz_id = ?
        """;

    private static final String DELETE_ALL_QUIZ_EMBEDDINGS_SQL = """
        delete from quiz_embeddings
        """;

    private static final String SEARCH_SIMILAR_QUIZZES_SQL = """
        select quiz_id, topic_id, question_title, 1 - (embedding <=> ?::vector) as similarity
        from quiz_embeddings
        where topic_id = ?
        order by embedding <=> ?::vector
        limit ?
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcVectorStoreRepository(@Qualifier("vectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertChunks(List<DocumentChunkRecord> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_CHUNKS_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                DocumentChunkRecord chunk = chunks.get(i);
                ps.setString(1, chunk.chunkingStrategy());
                setNullableLong(ps, 2, chunk.topicId());
                ps.setString(3, chunk.sourceFile());
                ps.setString(4, chunk.heading());
                ps.setString(5, chunk.content());
                ps.setString(6, chunk.source());
                ps.setInt(7, chunk.charLength());
                ps.setString(8, toVectorLiteral(chunk.embedding()));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    @Override
    public List<RetrievedChunk> searchChunks(String strategy, float[] queryEmbedding, Long topicId, int topK) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(
            SEARCH_CHUNKS_SQL,
            ps -> {
                ps.setString(1, vectorLiteral);
                ps.setString(2, strategy);
                setNullableLong(ps, 3, topicId);
                setNullableLong(ps, 4, topicId);
                ps.setString(5, vectorLiteral);
                ps.setInt(6, topK);
            },
            (rs, rowNum) -> new RetrievedChunk(
                rs.getString("content"),
                rs.getString("source_file"),
                rs.getString("heading"),
                rs.getDouble("similarity")
            )
        );
    }

    @Override
    public int deleteChunks(String strategy, Long topicId) {
        return jdbcTemplate.update(
            DELETE_CHUNKS_SQL,
            ps -> {
                ps.setString(1, strategy);
                setNullableLong(ps, 2, topicId);
                setNullableLong(ps, 3, topicId);
            }
        );
    }

    @Override
    public Map<String, ChunkStats> chunkStatsByStrategy() {
        List<ChunkStatsRow> rows = jdbcTemplate.query(
            CHUNK_STATS_SQL,
            (rs, rowNum) -> new ChunkStatsRow(
                rs.getString("chunking_strategy"),
                rs.getLong("chunk_count"),
                rs.getDouble("average_char_length")
            )
        );

        Map<String, ChunkStats> stats = new LinkedHashMap<>();
        for (ChunkStatsRow row : rows) {
            stats.put(row.strategy(), new ChunkStats(row.chunkCount(), row.averageCharLength()));
        }
        return stats;
    }

    @Override
    public void upsertQuizEmbedding(long quizId, long topicId, String questionTitle, float[] embedding) {
        jdbcTemplate.update(
            UPSERT_QUIZ_EMBEDDING_SQL,
            ps -> {
                ps.setLong(1, quizId);
                ps.setLong(2, topicId);
                ps.setString(3, questionTitle);
                ps.setString(4, toVectorLiteral(embedding));
            }
        );
    }

    @Override
    public void deleteQuizEmbedding(long quizId) {
        jdbcTemplate.update(
            DELETE_QUIZ_EMBEDDING_SQL,
            ps -> ps.setLong(1, quizId)
        );
    }

    @Override
    public int deleteAllQuizEmbeddings() {
        return jdbcTemplate.update(DELETE_ALL_QUIZ_EMBEDDINGS_SQL);
    }

    @Override
    public List<SimilarQuizHit> searchSimilarQuizzes(float[] embedding, long topicId, int topK) {
        String vectorLiteral = toVectorLiteral(embedding);
        return jdbcTemplate.query(
            SEARCH_SIMILAR_QUIZZES_SQL,
            ps -> {
                ps.setString(1, vectorLiteral);
                ps.setLong(2, topicId);
                ps.setString(3, vectorLiteral);
                ps.setInt(4, topK);
            },
            (rs, rowNum) -> new SimilarQuizHit(
                rs.getLong("quiz_id"),
                rs.getLong("topic_id"),
                rs.getString("question_title"),
                rs.getDouble("similarity")
            )
        );
    }

    static String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding must not be empty.");
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(embedding[i]));
        }
        return builder.append(']').toString();
    }

    private static void setNullableLong(
        java.sql.PreparedStatement ps,
        int parameterIndex,
        Long value
    ) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(parameterIndex, Types.BIGINT);
            return;
        }
        ps.setLong(parameterIndex, value);
    }

    private record ChunkStatsRow(
        String strategy,
        long chunkCount,
        double averageCharLength
    ) {
    }
}
