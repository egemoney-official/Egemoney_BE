package com.igemoney.igemoney_BE.knowledge.chunking;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
public class SemanticChunkingStrategy implements ChunkingStrategy {

    public static final String STRATEGY_NAME = "semantic";

    private final SentenceSplitter sentenceSplitter;
    private final EmbeddingClient embeddingClient;

    public SemanticChunkingStrategy(SentenceSplitter sentenceSplitter, EmbeddingClient embeddingClient) {
        this.sentenceSplitter = sentenceSplitter;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        return chunkText(document.fullText(), null);
    }

    List<Chunk> chunkText(String text, String heading) {
        List<String> sentences = sentenceSplitter.split(text);
        if (sentences.isEmpty()) {
            return List.of();
        }
        if (sentences.size() == 1) {
            if (sentences.getFirst().length() > ChunkingConstants.MAX_SIZE) {
                return ChunkSupport.splitBySize(
                    sentences.getFirst(),
                    heading,
                    ChunkingConstants.TARGET_SIZE,
                    ChunkingConstants.TARGET_SIZE
                );
            }
            return ChunkSupport.mergeShortChunks(List.of(new Chunk(sentences.getFirst(), heading)));
        }

        List<float[]> embeddings = embeddingClient.embedAllDocuments(sentences);
        boolean[] breakpoints = semanticBreakpoints(embeddings);
        return chunkByBreakpoints(sentences, breakpoints, heading);
    }

    private static boolean[] semanticBreakpoints(List<float[]> embeddings) {
        double[] similarities = new double[embeddings.size() - 1];
        for (int i = 0; i < similarities.length; i++) {
            similarities[i] = VectorMath.cosineSimilarity(embeddings.get(i), embeddings.get(i + 1));
        }

        double mean = mean(similarities);
        double standardDeviation = standardDeviation(similarities, mean);
        double threshold = mean - standardDeviation;

        boolean[] breakpoints = new boolean[similarities.length];
        for (int i = 0; i < similarities.length; i++) {
            breakpoints[i] = similarities[i] < threshold;
        }
        return breakpoints;
    }

    private static List<Chunk> chunkByBreakpoints(List<String> sentences, boolean[] breakpoints, String heading) {
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < sentences.size(); index++) {
            int currentLength = ChunkSupport.joinedSentenceLength(sentences, start, index + 1);
            boolean hasNext = index < sentences.size() - 1;
            boolean nextWouldExceedMax = hasNext
                && ChunkSupport.joinedSentenceLength(sentences, start, index + 2) > ChunkingConstants.MAX_SIZE;
            boolean semanticBreak = hasNext
                && breakpoints[index]
                && currentLength >= ChunkingConstants.MIN_SIZE;
            boolean forceBreak = currentLength >= ChunkingConstants.MAX_SIZE || nextWouldExceedMax;

            if (semanticBreak || forceBreak) {
                chunks.add(new Chunk(ChunkSupport.joinSentences(sentences, start, index + 1), heading));
                start = index + 1;
            }
        }

        if (start < sentences.size()) {
            chunks.add(new Chunk(ChunkSupport.joinSentences(sentences, start, sentences.size()), heading));
        }
        return ChunkSupport.mergeShortChunks(chunks);
    }

    private static double mean(double[] values) {
        double sum = 0.0d;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double standardDeviation(double[] values, double mean) {
        double variance = 0.0d;
        for (double value : values) {
            double delta = value - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / values.length);
    }
}
