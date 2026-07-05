package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FixedChunkingStrategy implements ChunkingStrategy {

    public static final String STRATEGY_NAME = "fixed";

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        return ChunkSupport.splitBySize(
            document.fullText(),
            null,
            ChunkingConstants.TARGET_SIZE,
            ChunkingConstants.TARGET_SIZE
        );
    }
}
