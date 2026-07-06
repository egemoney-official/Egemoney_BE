package com.igemoney.igemoney_BE.common.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VoyageEmbeddingClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VoyageEmbeddingProperties properties = properties();

    @Test
    void embedQuerySendsVoyageJsonAndParsesEmbedding() {
        RestClient.Builder builder = restClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VoyageEmbeddingClient client = new VoyageEmbeddingClient(builder.build(), properties, Duration.ZERO);

        server.expect(once(), requestTo("https://api.voyageai.com/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
            .andExpect(requestJson("query", "compound interest"))
            .andRespond(withSuccess(responseJson(List.of(0.1, 0.2, -0.3)), MediaType.APPLICATION_JSON));

        float[] embedding = client.embedQuery("compound interest");

        assertThat(embedding).containsExactly(0.1f, 0.2f, -0.3f);
        assertThat(client.dimension()).isEqualTo(1024);
        server.verify();
    }

    @Test
    void embedDocumentSendsDocumentInputType() {
        RestClient.Builder builder = restClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VoyageEmbeddingClient client = new VoyageEmbeddingClient(builder.build(), properties, Duration.ZERO);

        server.expect(once(), requestTo("https://api.voyageai.com/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(requestJson("document", "savings document"))
            .andRespond(withSuccess(responseJson(List.of(1.0, 2.0)), MediaType.APPLICATION_JSON));

        float[] embedding = client.embedDocument("savings document");

        assertThat(embedding).containsExactly(1.0f, 2.0f);
        server.verify();
    }

    @Test
    void embedAllDocumentsSplitsBatchesAt128Inputs() {
        RestClient.Builder builder = restClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VoyageEmbeddingClient client = new VoyageEmbeddingClient(builder.build(), properties, Duration.ZERO);
        List<String> texts = documents(129);

        server.expect(once(), requestTo("https://api.voyageai.com/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(requestJsonWithInputSize("document", 128))
            .andRespond(withSuccess(responseBatchJson(repeatedEmbedding(128, 0.5)), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.voyageai.com/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(requestJsonWithInputSize("document", 1))
            .andRespond(withSuccess(responseBatchJson(repeatedEmbedding(1, 0.7)), MediaType.APPLICATION_JSON));

        List<float[]> embeddings = client.embedAllDocuments(texts);

        assertThat(embeddings).hasSize(129);
        assertThat(embeddings.getFirst()).containsExactly(0.5f);
        assertThat(embeddings.getLast()).containsExactly(0.7f);
        server.verify();
    }

    @Test
    void retriesOnceWhenVoyageReturnsTooManyRequests() {
        RestClient.Builder builder = restClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VoyageEmbeddingClient client = new VoyageEmbeddingClient(builder.build(), properties, Duration.ZERO);

        server.expect(once(), requestTo("https://api.voyageai.com/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo("https://api.voyageai.com/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(responseJson(List.of(0.9)), MediaType.APPLICATION_JSON));

        float[] embedding = client.embedQuery("retry me");

        assertThat(embedding).containsExactly(0.9f);
        server.verify();
    }

    private static RestClient.Builder restClientBuilder() {
        return RestClient.builder()
            .baseUrl("https://api.voyageai.com")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-api-key");
    }

    private static VoyageEmbeddingProperties properties() {
        VoyageEmbeddingProperties properties = new VoyageEmbeddingProperties();
        properties.setApiKey("test-api-key");
        properties.setModel("voyage-3.5");
        return properties;
    }

    private static org.springframework.test.web.client.RequestMatcher requestJson(
        String expectedInputType,
        String expectedText
    ) {
        return request -> {
            JsonNode root = requestBody(request);
            assertThat(root.path("model").asText()).isEqualTo("voyage-3.5");
            assertThat(root.path("input_type").asText()).isEqualTo(expectedInputType);
            assertThat(root.path("input")).hasSize(1);
            assertThat(root.path("input").get(0).asText()).isEqualTo(expectedText);
        };
    }

    private static org.springframework.test.web.client.RequestMatcher requestJsonWithInputSize(
        String expectedInputType,
        int expectedInputSize
    ) {
        return request -> {
            JsonNode root = requestBody(request);
            assertThat(root.path("model").asText()).isEqualTo("voyage-3.5");
            assertThat(root.path("input_type").asText()).isEqualTo(expectedInputType);
            assertThat(root.path("input")).hasSize(expectedInputSize);
        };
    }

    private static JsonNode requestBody(org.springframework.http.client.ClientHttpRequest request) throws IOException {
        MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
        String body = mockRequest.getBodyAsString(StandardCharsets.UTF_8);
        return OBJECT_MAPPER.readTree(body);
    }

    private static String responseJson(List<Double> embedding) {
        return "{\"data\":[{\"embedding\":" + embedding + "}]}";
    }

    private static String responseBatchJson(List<List<Double>> embeddings) {
        StringBuilder builder = new StringBuilder("{\"data\":[");
        for (int i = 0; i < embeddings.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{\"embedding\":").append(embeddings.get(i)).append('}');
        }
        return builder.append("]}").toString();
    }

    private static List<List<Double>> repeatedEmbedding(int count, double value) {
        List<List<Double>> embeddings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            embeddings.add(List.of(value));
        }
        return embeddings;
    }

    private static List<String> documents(int count) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            texts.add("document-" + i);
        }
        return texts;
    }
}
