package com.pawpose;

import com.pawpose.global.security.JwtProperties;
import com.pawpose.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** prod 에서 JWT_SECRET 이 빠지면 서버가 뜨지 않아야 함 (공개된 기본값으로 서명되는 것 방지) */
class JwtSecretConfigTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(JwtProvider.class)
    static class JwtOnlyConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer()) // application.yml 로드
            .withUserConfiguration(JwtOnlyConfig.class);

    @Test
    void prod에서_JWT_SECRET이_없으면_기동에_실패한다() {
        runner.withPropertyValues("spring.profiles.active=prod", "JWT_SECRET=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prod에서_JWT_SECRET이_있으면_정상_기동한다() {
        runner.withPropertyValues("spring.profiles.active=prod",
                        "JWT_SECRET=prod-secret-for-test-0123456789-abcdefghij")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtProvider.class));
    }

    @Test
    void 키가_비었거나_짧으면_JwtProvider_생성이_실패한다() {
        assertThatThrownBy(() -> new JwtProvider(new JwtProperties(null, 7)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProvider(new JwtProperties("", 7)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProvider(new JwtProperties("short-key", 7)))
                .isInstanceOf(IllegalStateException.class);
    }
}
