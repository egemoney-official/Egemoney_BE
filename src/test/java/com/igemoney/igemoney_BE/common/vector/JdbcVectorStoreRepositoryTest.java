package com.igemoney.igemoney_BE.common.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class JdbcVectorStoreRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertChunksUsesBatchInsertWithVectorLiteral() throws Exception {
        JdbcVectorStoreRepository repository = new JdbcVectorStoreRepository(jdbcTemplate);
        List<DocumentChunkRecord> chunks = List.of(
            new DocumentChunkRecord(
                "structure",
                7L,
                "savings.md",
                "Compound Interest",
                "content",
                "source",
                321,
                new float[] {0.1f, -2.5f, 3.0f}
            )
        );

        repository.insertChunks(chunks);

        ArgumentCaptor<BatchPreparedStatementSetter> captor =
            ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), captor.capture());
        assertThat(captor.getValue().getBatchSize()).isEqualTo(1);

        RecordingPreparedStatement recording = RecordingPreparedStatement.create();
        captor.getValue().setValues(recording.statement(), 0);

        assertThat(recording.parameters()).containsExactly(
            Map.entry(1, "structure"),
            Map.entry(2, 7L),
            Map.entry(3, "savings.md"),
            Map.entry(4, "Compound Interest"),
            Map.entry(5, "content"),
            Map.entry(6, "source"),
            Map.entry(7, 321),
            Map.entry(8, "[0.1,-2.5,3.0]")
        );
    }

    @Test
    void insertChunksSkipsEmptyBatch() {
        JdbcVectorStoreRepository repository = new JdbcVectorStoreRepository(jdbcTemplate);

        repository.insertChunks(List.of());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void searchChunksBindsStrategyTopicAndVectorLiteral() throws Exception {
        JdbcVectorStoreRepository repository = new JdbcVectorStoreRepository(jdbcTemplate);
        when(jdbcTemplate.query(
            anyString(),
            any(PreparedStatementSetter.class),
            anyRetrievedChunkMapper()
        )).thenReturn(List.of());

        repository.searchChunks("hybrid", new float[] {0.25f, 0.5f}, 11L, 5);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> setterCaptor =
            ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).query(
            sqlCaptor.capture(),
            setterCaptor.capture(),
            anyRetrievedChunkMapper()
        );
        assertThat(sqlCaptor.getValue())
            .contains("from document_chunks")
            .contains("chunking_strategy = ?")
            .contains("topic_id = ? or topic_id is null")
            .contains("embedding <=> ?::vector");

        RecordingPreparedStatement recording = RecordingPreparedStatement.create();
        setterCaptor.getValue().setValues(recording.statement());

        assertThat(recording.parameters()).containsExactly(
            Map.entry(1, "[0.25,0.5]"),
            Map.entry(2, "hybrid"),
            Map.entry(3, 11L),
            Map.entry(4, 11L),
            Map.entry(5, "[0.25,0.5]"),
            Map.entry(6, 5)
        );
    }

    @Test
    void deleteChunksWithNullTopicDeletesWholeStrategyScope() throws Exception {
        JdbcVectorStoreRepository repository = new JdbcVectorStoreRepository(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(3);

        int deleted = repository.deleteChunks("fixed", null);

        assertThat(deleted).isEqualTo(3);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> setterCaptor =
            ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), setterCaptor.capture());
        assertThat(sqlCaptor.getValue())
            .contains("delete from document_chunks")
            .contains("chunking_strategy = ?")
            .contains("? is null or topic_id = ?");

        RecordingPreparedStatement recording = RecordingPreparedStatement.create();
        setterCaptor.getValue().setValues(recording.statement());

        assertThat(recording.parameters()).containsExactly(
            Map.entry(1, "fixed"),
            Map.entry(2, new SqlNull(Types.BIGINT)),
            Map.entry(3, new SqlNull(Types.BIGINT))
        );
    }

    @Test
    void upsertQuizEmbeddingBindsConflictKeyAndVectorLiteral() throws Exception {
        JdbcVectorStoreRepository repository = new JdbcVectorStoreRepository(jdbcTemplate);

        repository.upsertQuizEmbedding(42L, 9L, "What is compound interest?", new float[] {1.0f, 0.0f});

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> setterCaptor =
            ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), setterCaptor.capture());
        assertThat(sqlCaptor.getValue())
            .contains("insert into quiz_embeddings")
            .contains("on conflict (quiz_id) do update")
            .contains("?::vector");

        RecordingPreparedStatement recording = RecordingPreparedStatement.create();
        setterCaptor.getValue().setValues(recording.statement());

        assertThat(recording.parameters()).containsExactly(
            Map.entry(1, 42L),
            Map.entry(2, 9L),
            Map.entry(3, "What is compound interest?"),
            Map.entry(4, "[1.0,0.0]")
        );
    }

    @Test
    void searchSimilarQuizzesUsesTopicScopeAndVectorOrdering() throws Exception {
        JdbcVectorStoreRepository repository = new JdbcVectorStoreRepository(jdbcTemplate);
        when(jdbcTemplate.query(
            anyString(),
            any(PreparedStatementSetter.class),
            anySimilarQuizHitMapper()
        )).thenReturn(List.of());

        repository.searchSimilarQuizzes(new float[] {-0.5f, 0.75f}, 3L, 4);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> setterCaptor =
            ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).query(
            sqlCaptor.capture(),
            setterCaptor.capture(),
            anySimilarQuizHitMapper()
        );
        assertThat(sqlCaptor.getValue())
            .contains("from quiz_embeddings")
            .contains("where topic_id = ?")
            .contains("order by embedding <=> ?::vector");

        RecordingPreparedStatement recording = RecordingPreparedStatement.create();
        setterCaptor.getValue().setValues(recording.statement());

        assertThat(recording.parameters()).containsExactly(
            Map.entry(1, "[-0.5,0.75]"),
            Map.entry(2, 3L),
            Map.entry(3, "[-0.5,0.75]"),
            Map.entry(4, 4)
        );
    }

    @Test
    void deleteAllQuizEmbeddingsClearsQuizEmbeddingTable() {
        JdbcVectorStoreRepository repository = new JdbcVectorStoreRepository(jdbcTemplate);
        when(jdbcTemplate.update(anyString())).thenReturn(5);

        int deleted = repository.deleteAllQuizEmbeddings();

        assertThat(deleted).isEqualTo(5);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("delete from quiz_embeddings");
    }

    @Test
    void vectorLiteralRejectsEmptyEmbedding() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> JdbcVectorStoreRepository.toVectorLiteral(new float[0])
        );
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<RetrievedChunk> anyRetrievedChunkMapper() {
        return any(RowMapper.class);
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<SimilarQuizHit> anySimilarQuizHitMapper() {
        return any(RowMapper.class);
    }

    private record SqlNull(int sqlType) {
    }

    private record RecordingPreparedStatement(
        PreparedStatement statement,
        Map<Integer, Object> parameters
    ) {

        private static RecordingPreparedStatement create() {
            Map<Integer, Object> parameters = new LinkedHashMap<>();
            PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if (args != null && args.length >= 2 && args[0] instanceof Integer index) {
                        if ("setNull".equals(methodName)) {
                            parameters.put(index, new SqlNull((Integer) args[1]));
                            return null;
                        }
                        if (methodName.startsWith("set")) {
                            parameters.put(index, args[1]);
                            return null;
                        }
                    }
                    return defaultValue(method.getReturnType());
                }
            );
            return new RecordingPreparedStatement(statement, parameters);
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0.0f;
            }
            if (returnType == double.class) {
                return 0.0d;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
