package com.igemoney.igemoney_BE.common.vector;

import java.util.List;
import java.util.Map;

public interface VectorStoreRepository {

    void insertChunks(List<DocumentChunkRecord> chunks);

    List<RetrievedChunk> searchChunks(String strategy, float[] queryEmbedding, Long topicId, int topK);

    int deleteChunks(String strategy, Long topicId);

    Map<String, ChunkStats> chunkStatsByStrategy();

    void upsertQuizEmbedding(long quizId, long topicId, String questionTitle, float[] embedding);

    void deleteQuizEmbedding(long quizId);

    int deleteAllQuizEmbeddings();

    List<SimilarQuizHit> searchSimilarQuizzes(float[] embedding, long topicId, int topK);
}
