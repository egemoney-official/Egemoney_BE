package com.igemoney.igemoney_BE.knowledge;

import com.igemoney.igemoney_BE.knowledge.chunking.ParsedDocument;
import com.igemoney.igemoney_BE.knowledge.chunking.Section;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");

    public ParsedDocument parse(Path path) {
        try {
            return parse(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read knowledge document: " + path, exception);
        }
    }

    public ParsedDocument parse(String sourceFile, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("Knowledge document must not be blank: " + sourceFile);
        }

        ParsedMarkdown parsedMarkdown = parseFrontMatter(markdown);
        List<Section> sections = parseSections(sourceFile, parsedMarkdown.body());
        return new ParsedDocument(
            sourceFile,
            parsedMarkdown.frontMatter().get("source"),
            parsedMarkdown.frontMatter().get("topic"),
            sections
        );
    }

    private static ParsedMarkdown parseFrontMatter(String markdown) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = Arrays.asList(normalized.split("\n", -1));
        if (lines.isEmpty() || !"---".equals(lines.getFirst().strip())) {
            return new ParsedMarkdown(Map.of(), normalized);
        }

        int closingLine = -1;
        for (int index = 1; index < lines.size(); index++) {
            if ("---".equals(lines.get(index).strip())) {
                closingLine = index;
                break;
            }
        }
        if (closingLine < 0) {
            throw new IllegalArgumentException("Knowledge document front matter is not closed.");
        }

        Map<String, String> frontMatter = parseFrontMatterLines(lines.subList(1, closingLine));
        String body = String.join("\n", lines.subList(closingLine + 1, lines.size()));
        return new ParsedMarkdown(frontMatter, body);
    }

    private static Map<String, String> parseFrontMatterLines(List<String> lines) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (String line : lines) {
            String normalized = line.strip();
            if (normalized.isEmpty()) {
                continue;
            }

            int separator = normalized.indexOf(':');
            if (separator < 0) {
                throw new IllegalArgumentException("Invalid front matter line: " + line);
            }

            String key = normalized.substring(0, separator).strip().toLowerCase(Locale.ROOT);
            String value = unquote(normalized.substring(separator + 1).strip());
            if (!key.isEmpty() && !value.isEmpty()) {
                values.put(key, value);
            }
        }
        return Map.copyOf(values);
    }

    private static List<Section> parseSections(String sourceFile, String body) {
        List<Section> sections = new ArrayList<>();
        String currentHeading = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : body.split("\n", -1)) {
            Matcher matcher = HEADING_PATTERN.matcher(line.strip());
            if (matcher.matches()) {
                flushSection(sections, currentHeading, currentContent);
                currentHeading = normalizeHeading(matcher.group(2));
                continue;
            }

            if (!currentContent.isEmpty()) {
                currentContent.append('\n');
            }
            currentContent.append(line);
        }
        flushSection(sections, currentHeading, currentContent);

        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Knowledge document body must contain content: " + sourceFile);
        }
        return sections;
    }

    private static void flushSection(List<Section> sections, String heading, StringBuilder content) {
        String normalized = content.toString().strip();
        if (!normalized.isEmpty()) {
            sections.add(new Section(heading, normalized));
        }
        content.setLength(0);
    }

    private static String normalizeHeading(String heading) {
        return heading.replaceFirst("\\s+#+\\s*$", "").strip();
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).strip();
            }
        }
        return value;
    }

    private record ParsedMarkdown(
        Map<String, String> frontMatter,
        String body
    ) {
    }
}
