package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.List;

public interface ChunkingStrategy {

    String name();

    List<Chunk> chunk(ParsedDocument document);
}
