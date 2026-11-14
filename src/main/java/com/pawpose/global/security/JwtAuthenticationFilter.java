package com.pawpose.global.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer <JWT> 헤더가 있으면 검증해서 SecurityContext 에 userId 를 넣습니다.
 * 컨트롤러에서는 @AuthenticationPrincipal Long userId 로 꺼내 쓰면 됩니다.
 * 토큰이 없거나 잘못돼도 여기서 막지 않고, 인증이 필요한 API 에서만 401 이 나갑니다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_EXPIRED_ATTR = "TOKEN_EXPIRED";
    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            try {
                Long userId = jwtProvider.parseUserId(token);
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                // 엔트리포인트에서 TOKEN_EXPIRED 코드로 응답하도록 표시만 해 둠
                request.setAttribute(TOKEN_EXPIRED_ATTR, true);
            } catch (JwtException | IllegalArgumentException e) {
                // 잘못된 토큰은 비로그인으로 취급
            }
        }
        chain.doFilter(request, response);
    }
}
