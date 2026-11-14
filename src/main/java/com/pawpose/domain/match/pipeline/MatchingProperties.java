package com.pawpose.domain.match.pipeline;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "pawpose.matching")
public class MatchingProperties {
    private boolean enabled = false;
    private String embeddingUrl = "http://127.0.0.1:8091/embed";
    private String embeddingKey = "";
    private String modelVersion = "google/siglip-base-patch16-224@7fd15f0689c79d79e38b1c2e2e2370a7bf2761ed:rgb224-v1";
    private int dimensions = 768;
    private double threshold = 0.60;
    private int maxAttempts = 3;
    private List<String> imageHosts = List.of("www.animal.go.kr", "openapi.animal.go.kr");
}
