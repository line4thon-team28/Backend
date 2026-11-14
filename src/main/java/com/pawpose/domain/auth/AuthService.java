package com.pawpose.domain.auth;

import com.pawpose.domain.auth.dto.KakaoLoginRequest;
import com.pawpose.domain.auth.dto.LoginRequest;
import com.pawpose.domain.auth.dto.MeResponse;
import com.pawpose.domain.auth.dto.SignupRequest;
import com.pawpose.domain.auth.dto.TokenResponse;
import com.pawpose.domain.auth.kakao.KakaoOAuthClient;
import com.pawpose.domain.auth.kakao.KakaoUserInfo;
import com.pawpose.domain.user.AuthProvider;
import com.pawpose.domain.user.User;
import com.pawpose.domain.user.UserRepository;
import com.pawpose.global.error.BusinessException;
import com.pawpose.global.error.ErrorCode;
import com.pawpose.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_NICKNAME = "보호자";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final KakaoOAuthClient kakaoOAuthClient;

    /** 가입 후 바로 토큰을 돌려줘서 자동 로그인 (명세 F-01 출력) */
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        User user = userRepository.save(
                User.local(email, passwordEncoder.encode(request.password()), request.nickname().trim()));
        return TokenResponse.of(jwtProvider.createAccessToken(user.getId()), user, true);
    }

    /** 이메일·비밀번호 중 어느 쪽이 틀렸는지 알려주지 않음 (명세 F-01 예외) */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .filter(u -> u.getProvider() == AuthProvider.LOCAL)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        return TokenResponse.of(jwtProvider.createAccessToken(user.getId()), user, false);
    }

    /** 카카오 회원번호로 찾고, 없으면 가입시킨 뒤 우리 JWT 발급 */
    @Transactional
    public TokenResponse kakaoLogin(KakaoLoginRequest request) {
        KakaoUserInfo info = kakaoOAuthClient.fetchUserInfo(request.code());

        Optional<User> existing = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, info.kakaoId());
        if (existing.isPresent()) {
            User user = existing.get();
            return TokenResponse.of(jwtProvider.createAccessToken(user.getId()), user, false);
        }

        User user = userRepository.save(User.kakao(info.kakaoId(), normalizeNickname(info.nickname())));
        return TokenResponse.of(jwtProvider.createAccessToken(user.getId()), user, true);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MeResponse.from(user);
    }

    /** 카카오 닉네임이 비었거나 규칙(2~10자)에 안 맞을 때 보정 */
    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() < 2) {
            return trimmed + "님";
        }
        return trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed;
    }
}
