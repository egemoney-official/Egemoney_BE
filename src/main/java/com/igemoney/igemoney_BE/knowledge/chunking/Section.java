package com.igemoney.igemoney_BE.knowledge.chunking;

public record Section(
    String heading,
    String content
) {

    public Section {
        heading = normalizeOptional(heading);
        content = requireText(content, "content");
    }

    private static String requireText(String value, String fieldName) {
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
}
