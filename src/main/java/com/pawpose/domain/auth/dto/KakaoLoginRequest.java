package com.pawpose.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** FE 가 카카오 리다이렉트로 받은 인가 코드(?code=...)를 그대로 보냅니다. */
public record KakaoLoginRequest(
        @NotBlank(message = "인가 코드가 없어요") String code
) {
}
