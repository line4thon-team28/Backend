package com.pawpose.domain.user;

import com.pawpose.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원. 이메일 가입(LOCAL)과 카카오 가입(KAKAO)을 한 테이블에 둡니다.
 * - LOCAL: email, password 필수 / providerId 없음
 * - KAKAO: providerId(카카오 회원번호) 필수 / email, password 없음
 */
@Getter
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_provider", columnNames = {"provider", "provider_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String email;

    @Column(length = 100)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    public static User local(String email, String encodedPassword, String nickname) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.nickname = nickname;
        user.provider = AuthProvider.LOCAL;
        return user;
    }

    public static User kakao(String kakaoId, String nickname) {
        User user = new User();
        user.providerId = kakaoId;
        user.nickname = nickname;
        user.provider = AuthProvider.KAKAO;
        return user;
    }
}
