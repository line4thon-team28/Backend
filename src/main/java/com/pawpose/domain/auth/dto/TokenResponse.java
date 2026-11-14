package com.pawpose.domain.auth.dto;

import com.pawpose.domain.user.User;

/**
 * 로그인·가입 공통 응답.
 * newUser: 카카오로 처음 들어온 사용자면 true (FE 가 환영 화면 등을 띄우고 싶을 때 사용)
 */
public record TokenResponse(String accessToken, Long userId, String nickname, boolean newUser) {

    public static TokenResponse of(String accessToken, User user, boolean newUser) {
        return new TokenResponse(accessToken, user.getId(), user.getNickname(), newUser);
    }
}
