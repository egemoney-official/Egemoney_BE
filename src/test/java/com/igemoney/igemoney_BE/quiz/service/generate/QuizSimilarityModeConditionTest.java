package com.igemoney.igemoney_BE.quiz.service.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.quiz.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class QuizSimilarityModeConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SimilarityModeTestConfig.class);

    @Test
    void defaultsToSimpleSimilarityServiceWhenModeIsUnset() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QuizSimilarityService.class);
            assertThat(context.getBean(QuizSimilarityService.class)).isInstanceOf(SimpleQuizSimilarityService.class);
            assertThat(context).doesNotHaveBean(VectorQuizSimilarityService.class);
        });
    }

    @Test
    void selectsVectorSimilarityServiceWhenModeIsRag() {
        contextRunner
            .withPropertyValues("quiz.generation.mode=rag")
            .run(context -> {
                assertThat(context).hasSingleBean(QuizSimilarityService.class);
                assertThat(context.getBean(QuizSimilarityService.class)).isInstanceOf(VectorQuizSimilarityService.class);
                assertThat(context).doesNotHaveBean(SimpleQuizSimilarityService.class);
            });
    }

    @Configuration
    @Import({SimpleQuizSimilarityService.class, VectorQuizSimilarityService.class})
    static class SimilarityModeTestConfig {

        @Bean
        QuizRepository quizRepository() {
            return mock(QuizRepository.class);
        }

        @Bean
        EmbeddingClient embeddingClient() {
            return mock(EmbeddingClient.class);
        }

        @Bean
        VectorStoreRepository vectorStoreRepository() {
            return mock(VectorStoreRepository.class);
        }
    }
}
