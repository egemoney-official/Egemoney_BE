package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.List;

public record ParsedDocument(
    String sourceFile,
    String source,
    String topicSlug,
    List<Section> sections
) {

    public ParsedDocument {
        sourceFile = requireText(sourceFile, "sourceFile");
        source = normalizeOptional(source);
        topicSlug = normalizeOptional(topicSlug);
        sections = List.copyOf(sections == null ? List.of() : sections);
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("sections must not be empty.");
        }
    }

    String fullText() {
        return sections.stream()
            .map(Section::content)
            .filter(content -> !content.isBlank())
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
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
