package com.igemoney.igemoney_BE.knowledge.chunking;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SentenceSplitter {

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> sentences = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char currentChar = normalized.charAt(i);
            current.append(currentChar);

            if (isSentenceBoundary(currentChar) || currentChar == '\n') {
                addSentence(sentences, current);
            }
        }
        addSentence(sentences, current);

        return List.copyOf(sentences);
    }

    private static boolean isSentenceBoundary(char value) {
        return value == '.' || value == '!' || value == '?' || value == '。' || value == '！' || value == '？';
    }

    private static void addSentence(List<String> sentences, StringBuilder current) {
        String sentence = current.toString()
            .replace('\n', ' ')
            .replaceAll("\\s+", " ")
            .strip();
        if (!sentence.isEmpty()) {
            sentences.add(sentence);
        }
        current.setLength(0);
    }
}
