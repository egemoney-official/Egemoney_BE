package com.igemoney.igemoney_BE.knowledge.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkingStrategyTest {

    private final SentenceSplitter sentenceSplitter = new SentenceSplitter();

    @Test
    void fixedCutsTextIntoExactTargetSizedChunksAndKeepsRemainder() {
        FixedChunkingStrategy strategy = new FixedChunkingStrategy();
        ParsedDocument document = document(repeat("가", 1_200));

        List<Chunk> chunks = strategy.chunk(document);

        assertThat(chunks).extracting(Chunk::charLength)
            .containsExactly(500, 500, 200);
        assertThat(chunks).allMatch(chunk -> chunk.heading() == null);
    }

    @Test
    void fixedOverlapSharesOneHundredCharactersBetweenAdjacentChunks() {
        FixedOverlapChunkingStrategy strategy = new FixedOverlapChunkingStrategy();
        ParsedDocument document = document(patternedText(1_100));

        List<Chunk> chunks = strategy.chunk(document);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(Chunk::charLength)
            .containsExactly(500, 500, 300);
        assertThat(chunks.get(0).content().substring(400, 500))
            .isEqualTo(chunks.get(1).content().substring(0, 100));
        assertThat(chunks.get(1).content().substring(400, 500))
            .isEqualTo(chunks.get(2).content().substring(0, 100));
    }

    @Test
    void sentenceWindowKeepsSentenceBoundariesAndSharesPreviousSentences() {
        SentenceWindowChunkingStrategy strategy = new SentenceWindowChunkingStrategy(sentenceSplitter);
        String sentence1 = sentence("첫번째", 145);
        String sentence2 = sentence("두번째", 145);
        String sentence3 = sentence("세번째", 145);
        String sentence4 = sentence("네번째", 145);
        String sentence5 = sentence("다섯번째", 145);
        String sentence6 = sentence("여섯번째", 145);
        ParsedDocument document = document(String.join(" ", List.of(
            sentence1,
            sentence2,
            sentence3,
            sentence4,
            sentence5,
            sentence6
        )));

        List<Chunk> chunks = strategy.chunk(document);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.content().endsWith("."));
        assertThat(chunks.get(0).content()).contains(sentence2, sentence3);
        assertThat(chunks.get(1).content()).contains(sentence2, sentence3);
    }

    @Test
    void semanticSplitsWhereAdjacentSimilarityDrops() {
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(
            sentenceSplitter,
            new KeywordEmbeddingClient()
        );
        String alpha1 = sentence("alpha 첫번째", 145);
        String alpha2 = sentence("alpha 두번째", 145);
        String beta1 = sentence("beta 첫번째", 145);
        String beta2 = sentence("beta 두번째", 145);
        ParsedDocument document = document(String.join(" ", List.of(alpha1, alpha2, beta1, beta2)));

        List<Chunk> chunks = strategy.chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).contains(alpha1, alpha2).doesNotContain(beta1);
        assertThat(chunks.get(1).content()).contains(beta1, beta2).doesNotContain(alpha2);
    }

    @Test
    void structureSplitsByHeadingAndParagraphsWhenSectionExceedsMaxSize() {
        StructureChunkingStrategy strategy = new StructureChunkingStrategy();
        String shortContent = sentence("짧은 섹션", 140);
        Section shortSection = new Section("짧은 제목", shortContent);
        Section longSection = new Section("긴 제목", String.join("\n\n", List.of(
            paragraph("첫 문단", 360),
            paragraph("둘 문단", 360),
            paragraph("셋 문단", 360),
            paragraph("넷 문단", 360)
        )));
        ParsedDocument document = new ParsedDocument("test.md", "source", "topic", List.of(shortSection, longSection));

        List<Chunk> chunks = strategy.chunk(document);

        assertThat(chunks).hasSize(5);
        assertThat(chunks.get(0).heading()).isEqualTo("짧은 제목");
        assertThat(chunks.get(0).content()).isEqualTo(shortContent);
        assertThat(chunks.subList(1, chunks.size()))
            .allMatch(chunk -> "긴 제목".equals(chunk.heading()))
            .allMatch(chunk -> chunk.charLength() <= ChunkingConstants.MAX_SIZE);
    }

    @Test
    void hybridKeepsShortSectionsAndSemanticallySplitsOnlyLongSections() {
        KeywordEmbeddingClient embeddingClient = new KeywordEmbeddingClient();
        SemanticChunkingStrategy semantic = new SemanticChunkingStrategy(sentenceSplitter, embeddingClient);
        HybridChunkingStrategy strategy = new HybridChunkingStrategy(new StructureChunkingStrategy(), semantic);
        String shortContent = sentence("짧은 구조 섹션", 150);
        Section shortSection = new Section("짧은 제목", shortContent);
        Section longSection = new Section("긴 제목", String.join(" ", List.of(
            sentence("alpha 하나", 155),
            sentence("alpha 둘", 155),
            sentence("alpha 셋", 155),
            sentence("alpha 넷", 155),
            sentence("beta 하나", 155),
            sentence("beta 둘", 155),
            sentence("beta 셋", 155),
            sentence("beta 넷", 155)
        )));
        ParsedDocument document = new ParsedDocument("test.md", "source", "topic", List.of(shortSection, longSection));

        List<Chunk> chunks = strategy.chunk(document);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).content()).isEqualTo(shortContent);
        assertThat(chunks.get(0).heading()).isEqualTo("짧은 제목");
        assertThat(chunks.subList(1, chunks.size()))
            .allMatch(chunk -> "긴 제목".equals(chunk.heading()));
        assertThat(chunks.get(1).content()).contains("alpha").doesNotContain("beta 하나");
        assertThat(chunks.get(2).content()).contains("beta");
        assertThat(embeddingClient.documentBatchCallCount).isEqualTo(1);
    }

    @Test
    void allStrategiesMergeChunksShorterThanMinimumWhenThereIsANeighbor() {
        List<ChunkingStrategy> strategies = List.of(
            new FixedChunkingStrategy(),
            new FixedOverlapChunkingStrategy(),
            new SentenceWindowChunkingStrategy(sentenceSplitter),
            new SemanticChunkingStrategy(sentenceSplitter, new KeywordEmbeddingClient()),
            new StructureChunkingStrategy(),
            new HybridChunkingStrategy(
                new StructureChunkingStrategy(),
                new SemanticChunkingStrategy(sentenceSplitter, new KeywordEmbeddingClient())
            )
        );
        ParsedDocument document = document(repeat("가", 1_050));

        for (ChunkingStrategy strategy : strategies) {
            assertThat(strategy.chunk(document))
                .as(strategy.name())
                .allMatch(chunk -> chunk.charLength() >= ChunkingConstants.MIN_SIZE);
        }
    }

    @Test
    void registryLooksUpStrategiesByNameAndReturnsAll() {
        FixedChunkingStrategy fixed = new FixedChunkingStrategy();
        FixedOverlapChunkingStrategy fixedOverlap = new FixedOverlapChunkingStrategy();
        ChunkingStrategyRegistry registry = new ChunkingStrategyRegistry(List.of(fixed, fixedOverlap));

        assertThat(registry.get("fixed")).isSameAs(fixed);
        assertThat(registry.names()).containsExactlyInAnyOrder("fixed", "fixed_overlap");
        assertThat(registry.all()).containsExactly(fixed, fixedOverlap);
    }

    private static ParsedDocument document(String content) {
        return new ParsedDocument("test.md", "source", "topic", List.of(new Section(null, content)));
    }

    private static String repeat(String value, int count) {
        return value.repeat(count);
    }

    private static String patternedText(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append((char) ('A' + (i % 26)));
        }
        return builder.toString();
    }

    private static String sentence(String prefix, int targetLength) {
        String suffix = "입니다.";
        int bodyLength = Math.max(0, targetLength - prefix.length() - suffix.length() - 1);
        return prefix + " " + "가".repeat(bodyLength) + suffix;
    }

    private static String paragraph(String prefix, int targetLength) {
        return sentence(prefix, targetLength);
    }

    private static final class KeywordEmbeddingClient implements EmbeddingClient {

        private int documentBatchCallCount;

        @Override
        public float[] embedDocument(String text) {
            return vectorFor(text);
        }

        @Override
        public float[] embedQuery(String text) {
            return vectorFor(text);
        }

        @Override
        public List<float[]> embedAllDocuments(List<String> texts) {
            documentBatchCallCount++;
            List<float[]> embeddings = new ArrayList<>();
            for (String text : texts) {
                embeddings.add(vectorFor(text));
            }
            return embeddings;
        }

        @Override
        public int dimension() {
            return 2;
        }

        private static float[] vectorFor(String text) {
            if (text.contains("beta")) {
                return new float[] {0.0f, 1.0f};
            }
            return new float[] {1.0f, 0.0f};
        }
    }
}
