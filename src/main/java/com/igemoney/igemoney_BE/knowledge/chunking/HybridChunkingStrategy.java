package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
public class HybridChunkingStrategy implements ChunkingStrategy {

    public static final String STRATEGY_NAME = "hybrid";

    private final StructureChunkingStrategy structureChunkingStrategy;
    private final SemanticChunkingStrategy semanticChunkingStrategy;

    public HybridChunkingStrategy(
        StructureChunkingStrategy structureChunkingStrategy,
        SemanticChunkingStrategy semanticChunkingStrategy
    ) {
        this.structureChunkingStrategy = structureChunkingStrategy;
        this.semanticChunkingStrategy = semanticChunkingStrategy;
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        List<Chunk> chunks = new ArrayList<>();
        for (Section section : document.sections()) {
            if (section.content().length() <= ChunkingConstants.MAX_SIZE) {
                chunks.addAll(structureChunkingStrategy.chunkSection(section));
                continue;
            }
            chunks.addAll(semanticChunkingStrategy.chunkText(section.content(), section.heading()));
        }
        return ChunkSupport.mergeShortChunks(chunks);
    }
}
