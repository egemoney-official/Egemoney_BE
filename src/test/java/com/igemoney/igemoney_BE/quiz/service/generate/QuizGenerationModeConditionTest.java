package com.igemoney.igemoney_BE.quiz.service.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.igemoney.igemoney_BE.common.embedding.EmbeddingClient;
import com.igemoney.igemoney_BE.common.vector.VectorStoreRepository;
import com.igemoney.igemoney_BE.topic.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class QuizGenerationModeConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(GenerationModeTestConfig.class);

    @Test
    void defaultsToMockQuizGenerationServiceWhenModeIsUnset() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QuizGenerationService.class);
            assertThat(context.getBean(QuizGenerationService.class)).isInstanceOf(MockQuizGenerationService.class);
            assertThat(context).doesNotHaveBean(RagQuizGenerationService.class);
        });
    }

    @Test
    void selectsRagQuizGenerationServiceWhenModeIsRag() {
        contextRunner
            .withPropertyValues("quiz.generation.mode=rag")
            .run(context -> {
                assertThat(context).hasSingleBean(QuizGenerationService.class);
                assertThat(context.getBean(QuizGenerationService.class)).isInstanceOf(RagQuizGenerationService.class);
                assertThat(context).doesNotHaveBean(MockQuizGenerationService.class);
            });
    }

    @Configuration
    @Import({MockQuizGenerationService.class, RagQuizGenerationService.class})
    static class GenerationModeTestConfig {

        @Bean
        TopicRepository topicRepository() {
            return mock(TopicRepository.class);
        }

        @Bean
        EmbeddingClient embeddingClient() {
            return mock(EmbeddingClient.class);
        }

        @Bean
        VectorStoreRepository vectorStoreRepository() {
            return mock(VectorStoreRepository.class);
        }

        @Bean
        QuizLlmClient quizLlmClient() {
            return mock(QuizLlmClient.class);
        }
    }
}
