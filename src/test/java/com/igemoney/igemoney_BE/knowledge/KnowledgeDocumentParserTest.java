package com.igemoney.igemoney_BE.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.igemoney.igemoney_BE.knowledge.chunking.ParsedDocument;
import com.igemoney.igemoney_BE.knowledge.chunking.Section;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentParserTest {

    private final KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

    @Test
    void parsesFrontMatterAndMarkdownHeadings() {
        String markdown = """
            ---
            source: 한국은행 경제금융용어 요약
            topic: 경제기초
            ---
            ## 단리와 복리
            단리는 원금에 대해서만 이자를 계산하는 방식이다.

            복리는 이자에도 다시 이자가 붙는 방식이다.

            ## 인플레이션
            인플레이션은 전반적인 물가 수준이 지속적으로 오르는 현상이다.
            """;

        ParsedDocument document = parser.parse("economic-basic__interest.md", markdown);

        assertThat(document.sourceFile()).isEqualTo("economic-basic__interest.md");
        assertThat(document.source()).isEqualTo("한국은행 경제금융용어 요약");
        assertThat(document.topicSlug()).isEqualTo("경제기초");
        assertThat(document.sections()).extracting(Section::heading)
            .containsExactly("단리와 복리", "인플레이션");
        assertThat(document.sections().getFirst().content())
            .contains("단리는 원금")
            .contains("복리는 이자");
    }

    @Test
    void createsSingleSectionWhenDocumentHasNoHeading() {
        String markdown = """
            ---
            source: 공통 자료
            topic: common
            ---
            예산은 한 달 동안 사용할 돈의 항목별 계획이다.
            소비자는 고정 지출과 변동 지출을 구분할 수 있다.
            """;

        ParsedDocument document = parser.parse("common__budget.md", markdown);

        assertThat(document.sections()).hasSize(1);
        assertThat(document.sections().getFirst().heading()).isNull();
        assertThat(document.sections().getFirst().content()).contains("고정 지출");
    }
}
