package com.pawpose.domain.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawpose.global.error.BusinessException;
import com.pawpose.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 REST API 호출 2번이 전부입니다.
 * 1) 인가 코드 → 카카오 액세스 토큰 (kauth.kakao.com/oauth/token)
 * 2) 카카오 액세스 토큰 → 사용자 정보 (kapi.kakao.com/v2/user/me)
 */
@Slf4j
@Component
public class KakaoOAuthClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final KakaoProperties properties;
    private final RestClient restClient;

    public KakaoOAuthClient(KakaoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public KakaoUserInfo fetchUserInfo(String authorizationCode) {
        String kakaoAccessToken = requestAccessToken(authorizationCode);
        return requestUserInfo(kakaoAccessToken);
    }

    private String requestAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        if (StringUtils.hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }

        try {
            TokenResponse response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new BusinessException(ErrorCode.KAKAO_LOGIN_FAILED);
            }
            return response.accessToken();
        } catch (RestClientException e) {
            // 가장 흔한 원인: redirect_uri 불일치(KOE006), 이미 쓴 코드 재사용(KOE320), REST API 키 오타(KOE101)
            log.warn("카카오 토큰 발급 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.KAKAO_LOGIN_FAILED);
        }
    }

    private KakaoUserInfo requestUserInfo(String kakaoAccessToken) {
        try {
            UserResponse response = restClient.get()
                    .uri(USER_INFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(UserResponse.class);
            if (response == null || response.id() == null) {
                throw new BusinessException(ErrorCode.KAKAO_LOGIN_FAILED);
            }
            return new KakaoUserInfo(String.valueOf(response.id()), response.nickname());
        } catch (RestClientException e) {
            log.warn("카카오 사용자 정보 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.KAKAO_LOGIN_FAILED);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount,
            Properties properties
    ) {
        String nickname() {
            if (kakaoAccount != null && kakaoAccount.profile() != null && kakaoAccount.profile().nickname() != null) {
                return kakaoAccount.profile().nickname();
            }
            return properties != null ? properties.nickname() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAccount(Profile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(String nickname) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(String nickname) {
    }
}
