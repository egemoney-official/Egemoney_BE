package com.igemoney.igemoney_BE.common.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")
@EnableConfigurationProperties(VoyageEmbeddingProperties.class)
public class VoyageEmbeddingClient implements EmbeddingClient {

    private static final String BASE_URL = "https://api.voyageai.com";
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";
    private static final int DIMENSION = 1024;
    private static final int MAX_BATCH_SIZE = 128;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(250);

    private final RestClient restClient;
    private final VoyageEmbeddingProperties properties;
    private final Duration retryBackoff;

    @Autowired
    public VoyageEmbeddingClient(RestClient.Builder restClientBuilder, VoyageEmbeddingProperties properties) {
        this(
            restClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .requestFactory(requestFactory())
                .build(),
            properties,
            DEFAULT_RETRY_BACKOFF
        );
    }

    VoyageEmbeddingClient(RestClient restClient, VoyageEmbeddingProperties properties, Duration retryBackoff) {
        this.restClient = restClient;
        this.properties = properties;
        this.retryBackoff = retryBackoff;
    }

    @Override
    public float[] embedDocument(String text) {
        return embed(List.of(text), "document").getFirst();
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(List.of(text), "query").getFirst();
    }

    @Override
    public List<float[]> embedAllDocuments(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, texts.size());
            embeddings.addAll(embed(texts.subList(start, end), "document"));
        }
        return embeddings;
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    private List<float[]> embed(List<String> texts, String inputType) {
        EmbeddingRequest request = new EmbeddingRequest(properties.getModel(), texts, inputType);
        EmbeddingResponse response = postWithRetry(request);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("Voyage embeddings response is empty.");
        }

        List<float[]> embeddings = response.data().stream()
            .map(EmbeddingData::toFloatArray)
            .toList();
        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException("Voyage embeddings response size does not match request size.");
        }
        return embeddings;
    }

    private EmbeddingResponse postWithRetry(EmbeddingRequest request) {
        try {
            return post(request);
        } catch (RestClientResponseException exception) {
            if (!isRetryable(exception.getStatusCode().value())) {
                throw exception;
            }
            sleepBeforeRetry();
            return post(request);
        }
    }

    private EmbeddingResponse post(EmbeddingRequest request) {
        return restClient.post()
            .uri(EMBEDDINGS_PATH)
            .body(request)
            .retrieve()
            .body(EmbeddingResponse.class);
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before retrying Voyage embeddings request.", exception);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(REQUEST_TIMEOUT);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        return requestFactory;
    }

    private record EmbeddingRequest(
        String model,
        List<String> input,
        @JsonProperty("input_type") String inputType
    ) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Double> embedding) {

        private float[] toFloatArray() {
            if (embedding == null) {
                throw new IllegalStateException("Voyage embedding item is empty.");
            }

            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
        }
    }
}
