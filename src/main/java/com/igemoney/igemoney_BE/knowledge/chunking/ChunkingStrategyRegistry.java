package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import org.springframework.stereotype.Component;

@Component
public class ChunkingStrategyRegistry {

    private final Map<String, ChunkingStrategy> strategiesByName;

    public ChunkingStrategyRegistry(List<ChunkingStrategy> strategies) {
        Map<String, ChunkingStrategy> mapped = new LinkedHashMap<>();
        for (ChunkingStrategy strategy : strategies) {
            ChunkingStrategy previous = mapped.putIfAbsent(strategy.name(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate chunking strategy name: " + strategy.name());
            }
        }
        this.strategiesByName = Collections.unmodifiableMap(mapped);
    }

    public ChunkingStrategy get(String name) {
        ChunkingStrategy strategy = strategiesByName.get(name);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown chunking strategy: " + name);
        }
        return strategy;
    }

    public List<ChunkingStrategy> all() {
        return List.copyOf(strategiesByName.values());
    }

    public Set<String> names() {
        return Set.copyOf(strategiesByName.keySet());
    }
}
