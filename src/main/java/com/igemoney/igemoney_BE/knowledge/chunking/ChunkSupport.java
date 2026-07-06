package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.ArrayList;
import java.util.List;

final class ChunkSupport {

    private ChunkSupport() {
    }

    static List<Chunk> mergeShortChunks(List<Chunk> chunks) {
        if (chunks.size() <= 1) {
            return List.copyOf(chunks);
        }

        List<Chunk> merged = new ArrayList<>(chunks);
        for (int i = 0; i < merged.size(); i++) {
            Chunk current = merged.get(i);
            if (current.charLength() >= ChunkingConstants.MIN_SIZE) {
                continue;
            }

            if (i > 0) {
                Chunk previous = merged.get(i - 1);
                merged.set(i - 1, combine(previous, current, previous.heading()));
                merged.remove(i);
                i--;
                continue;
            }

            if (merged.size() > 1) {
                Chunk next = merged.get(1);
                merged.set(1, combine(current, next, current.heading() == null ? next.heading() : current.heading()));
                merged.remove(0);
                i--;
            }
        }

        return List.copyOf(merged);
    }

    static List<Chunk> splitBySize(String content, String heading, int windowSize, int stepSize) {
        String normalized = normalizeContent(content);
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + windowSize, normalized.length());
            chunks.add(new Chunk(normalized.substring(start, end), heading));
            if (end == normalized.length()) {
                break;
            }
            start += stepSize;
        }
        return mergeShortChunks(chunks);
    }

    static List<Chunk> splitParagraphs(String content, String heading) {
        String[] paragraphs = normalizeContent(content).split("\\R\\s*\\R");
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String normalizedParagraph = normalizeContent(paragraph);
            if (normalizedParagraph.isEmpty()) {
                continue;
            }

            if (normalizedParagraph.length() > ChunkingConstants.MAX_SIZE) {
                flushCurrent(chunks, current, heading);
                chunks.addAll(splitBySize(
                    normalizedParagraph,
                    heading,
                    ChunkingConstants.TARGET_SIZE,
                    ChunkingConstants.TARGET_SIZE
                ));
                continue;
            }

            int joinedLength = joinedLength(current, normalizedParagraph);
            if (current.length() > 0 && joinedLength > ChunkingConstants.TARGET_SIZE) {
                flushCurrent(chunks, current, heading);
            }
            appendBlock(current, normalizedParagraph);
        }
        flushCurrent(chunks, current, heading);
        return mergeShortChunks(chunks);
    }

    static Chunk combine(Chunk left, Chunk right, String heading) {
        return new Chunk(left.content() + "\n\n" + right.content(), heading);
    }

    static String joinSentences(List<String> sentences, int startInclusive, int endExclusive) {
        return String.join(" ", sentences.subList(startInclusive, endExclusive)).strip();
    }

    static int joinedSentenceLength(List<String> sentences, int startInclusive, int endExclusive) {
        return joinSentences(sentences, startInclusive, endExclusive).length();
    }

    private static void flushCurrent(List<Chunk> chunks, StringBuilder current, String heading) {
        if (current.isEmpty()) {
            return;
        }
        chunks.add(new Chunk(current.toString(), heading));
        current.setLength(0);
    }

    private static int joinedLength(StringBuilder current, String nextBlock) {
        if (current.isEmpty()) {
            return nextBlock.length();
        }
        return current.length() + 2 + nextBlock.length();
    }

    private static void appendBlock(StringBuilder current, String block) {
        if (!current.isEmpty()) {
            current.append("\n\n");
        }
        current.append(block);
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.strip();
    }
}
