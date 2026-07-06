package com.igemoney.igemoney_BE.knowledge.chunking;

public record Chunk(
    String content,
    String heading,
    int charLength
) {

    public Chunk {
        content = normalizeRequired(content, "content");
        heading = normalizeOptional(heading);
        charLength = content.length();
    }

    public Chunk(String content, String heading) {
        this(content, heading, normalizedLength(content));
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static int normalizedLength(String value) {
        String normalized = normalizeRequired(value, "content");
        return normalized.length();
    }
}
