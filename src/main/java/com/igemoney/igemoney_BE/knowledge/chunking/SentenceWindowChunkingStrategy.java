package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SentenceWindowChunkingStrategy implements ChunkingStrategy {

    public static final String STRATEGY_NAME = "sentence_window";

    private final SentenceSplitter sentenceSplitter;

    public SentenceWindowChunkingStrategy(SentenceSplitter sentenceSplitter) {
        this.sentenceSplitter = sentenceSplitter;
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        List<String> sentences = sentenceSplitter.split(document.fullText());
        if (sentences.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < sentences.size()) {
            int end = findWindowEnd(sentences, start);
            chunks.add(new Chunk(ChunkSupport.joinSentences(sentences, start, end), null));
            if (end == sentences.size()) {
                break;
            }

            int overlapCount = Math.min(2, end - start);
            start = Math.max(start + 1, end - overlapCount);
        }

        return ChunkSupport.mergeShortChunks(chunks);
    }

    private static int findWindowEnd(List<String> sentences, int start) {
        int end = start;
        int length = 0;
        while (end < sentences.size()) {
            int nextLength = sentences.get(end).length();
            int joinedLength = length == 0 ? nextLength : length + 1 + nextLength;
            if (length > 0 && joinedLength > ChunkingConstants.TARGET_SIZE) {
                break;
            }
            length = joinedLength;
            end++;
        }
        return Math.max(start + 1, end);
    }
}
