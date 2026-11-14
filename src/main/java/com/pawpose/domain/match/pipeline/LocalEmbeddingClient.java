package com.pawpose.domain.match.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Locale;

/** Only exact configured image hosts. The inference process never downloads user-supplied URLs. */
@Component @RequiredArgsConstructor
public class LocalEmbeddingClient implements EmbeddingClient {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final MatchingProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

    @Override public double[] embed(String imageUrl) {
        try {
            if (properties.getEmbeddingKey().isBlank()) throw new AnalysisException("EMBEDDING_NOT_CONFIGURED", false);
            byte[] image = download(imageUrl);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEmbeddingUrl())).timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/octet-stream").header("X-Embedding-Key", properties.getEmbeddingKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(image)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new AnalysisException("EMBEDDING_HTTP_" + response.statusCode(), response.statusCode() >= 500 || response.statusCode() == 408 || response.statusCode() == 429);
            VectorResponse body = mapper.readValue(response.body(), VectorResponse.class);
            if (!properties.getModelVersion().equals(body.modelVersion()) || body.dimensions() != properties.getDimensions()
                    || body.vector() == null || body.vector().length != properties.getDimensions()) throw new AnalysisException("MODEL_MISMATCH", false);
            double norm = 0;
            for (double value : body.vector()) { if (!Double.isFinite(value)) throw new AnalysisException("INVALID_VECTOR", false); norm += value * value; }
            if (norm < 1e-12 || !Double.isFinite(norm)) throw new AnalysisException("INVALID_VECTOR", false);
            norm = Math.sqrt(norm);
            for (int i = 0; i < body.vector().length; i++) body.vector()[i] /= norm;
            return body.vector();
        } catch (AnalysisException e) { throw e;
        } catch (IllegalArgumentException e) { throw new AnalysisException("INVALID_IMAGE_INPUT", false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new AnalysisException("EMBEDDING_INTERRUPTED", true);
        } catch (Exception e) {
            // Do not propagate URLs, signed S3 query parameters or internal credentials to logs/API.
            throw new AnalysisException("IMAGE_ANALYSIS_FAILED", true);
        }
    }

    byte[] download(String imageUrl) throws Exception {
        URI uri = URI.create(imageUrl);
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || (uri.getPort() != -1 && uri.getPort() != ("https".equals(uri.getScheme()) ? 443 : 80))
                || properties.getImageHosts().stream().noneMatch(h -> h.equalsIgnoreCase(host))) throw new IllegalArgumentException("IMAGE_HOST_NOT_ALLOWED");
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress())
                throw new IllegalArgumentException("IMAGE_HOST_NOT_ALLOWED");
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(5000); connection.setReadTimeout(10000);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new AnalysisException("IMAGE_HTTP_" + status, status >= 500 || status == 408 || status == 429);
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IllegalArgumentException("IMAGE_SIZE_EXCEEDED");
            String type = connection.getContentType();
            // The official openapi.animal.go.kr photo host serves JPEG as application/octet-stream.
            // The Python decoder validates actual format/pixels after the bounded download.
            if (type == null || !(type.toLowerCase(Locale.ROOT).startsWith("image/jpeg") || type.toLowerCase(Locale.ROOT).startsWith("image/png")
                    || type.toLowerCase(Locale.ROOT).startsWith("application/octet-stream")))
                throw new IllegalArgumentException("UNSUPPORTED_IMAGE");
            try (var stream = connection.getInputStream()) {
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("IMAGE_SIZE_EXCEEDED");
                return bytes;
            }
        } finally { connection.disconnect(); }
    }

    record VectorResponse(String modelVersion, int dimensions, double[] vector) {}
}
