package com.pawpose.domain.auth.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(String clientId, String clientSecret, String redirectUri) {
}
