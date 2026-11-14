package com.pawpose.domain.auth;

import com.pawpose.domain.auth.dto.KakaoLoginRequest;
import com.pawpose.domain.auth.dto.LoginRequest;
import com.pawpose.domain.auth.dto.MeResponse;
import com.pawpose.domain.auth.dto.SignupRequest;
import com.pawpose.domain.auth.dto.TokenResponse;
import com.pawpose.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "F-01 인증")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "이메일 회원가입", description = "가입 즉시 accessToken 을 돌려줍니다(자동 로그인).")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @Operation(summary = "이메일 로그인")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "카카오 로그인·가입",
            description = "FE 가 카카오 리다이렉트로 받은 code 를 보내면, 처음이면 가입시키고 accessToken 을 돌려줍니다.")
    @PostMapping("/kakao")
    public ApiResponse<TokenResponse> kakao(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.ok(authService.kakaoLogin(request));
    }

    @Operation(summary = "내 정보", description = "새로고침 후 토큰이 살아 있는지 확인할 때 사용. 만료면 401 TOKEN_EXPIRED.")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(authService.me(userId));
    }
}
