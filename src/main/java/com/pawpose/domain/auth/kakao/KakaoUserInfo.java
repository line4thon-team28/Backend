package com.pawpose.domain.auth.kakao;

/** 카카오에서 받아온 정보 중 우리가 쓰는 것만. 동의항목은 닉네임만 켜면 됩니다. */
public record KakaoUserInfo(String kakaoId, String nickname) {
}
