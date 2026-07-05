package com.igemoney.igemoney_BE.common.embedding;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "embedding.voyage")
public class VoyageEmbeddingProperties {

    private String apiKey;
    private String model = "voyage-3.5";
}
