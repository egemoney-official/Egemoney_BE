package com.igemoney.igemoney_BE.common.embedding;

import java.util.List;

public interface EmbeddingClient {

    float[] embedDocument(String text);

    float[] embedQuery(String text);

    List<float[]> embedAllDocuments(List<String> texts);

    int dimension();
}
