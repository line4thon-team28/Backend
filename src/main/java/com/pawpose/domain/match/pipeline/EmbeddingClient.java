package com.pawpose.domain.match.pipeline;

public interface EmbeddingClient {
    double[] embed(String imageUrl);
}
