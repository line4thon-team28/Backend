package com.pawpose.domain.notice.collection;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pawpose.collection")
public class CollectionProperties {
    private boolean enabled;
    /** Decoding key from data.go.kr; never include this property in logs. */
    private String serviceKey = "";
    private int pageSize = 100;
    private int maxRetries = 3;
    private long retryDelayMillis = 500;
    private int dailyRequestLimit = 10_000;
    private int slowdownThreshold = 9_000;

    public void validate() {
        if (pageSize < 1 || pageSize > 1000 || maxRetries < 0 || maxRetries > 3
                || retryDelayMillis < 0 || retryDelayMillis > 10_000
                || dailyRequestLimit < 1 || slowdownThreshold < 1
                || slowdownThreshold > dailyRequestLimit) {
            throw new IllegalStateException("Invalid public data collection limits");
        }
        if (enabled && (serviceKey == null || serviceKey.isBlank())) {
            throw new IllegalStateException("PUBLIC_DATA_SERVICE_KEY is required when collection is enabled");
        }
    }
}
