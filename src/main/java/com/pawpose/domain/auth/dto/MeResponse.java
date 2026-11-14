package com.pawpose.domain.auth.dto;

import com.pawpose.domain.user.AuthProvider;
import com.pawpose.domain.user.User;

/** 새로고침 후 로그인 유지 확인용 (F-01 완료 조건). FE 가 앱 시작 시 호출. */
public record MeResponse(Long userId, String nickname, String email, AuthProvider provider) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getNickname(), user.getEmail(), user.getProvider());
    }
}
