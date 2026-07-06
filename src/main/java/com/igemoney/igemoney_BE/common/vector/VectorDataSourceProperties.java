package com.igemoney.igemoney_BE.common.vector;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vector.datasource")
public class VectorDataSourceProperties {

    private String url;
    private String username;
    private String password;
}
