package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StructureChunkingStrategy implements ChunkingStrategy {

    public static final String STRATEGY_NAME = "structure";

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        List<Chunk> chunks = new ArrayList<>();
        for (Section section : document.sections()) {
            chunks.addAll(chunkSection(section));
        }
        return ChunkSupport.mergeShortChunks(chunks);
    }

    List<Chunk> chunkSection(Section section) {
        if (section.content().length() <= ChunkingConstants.MAX_SIZE) {
            return List.of(new Chunk(section.content(), section.heading()));
        }
        return ChunkSupport.splitParagraphs(section.content(), section.heading());
    }
}
