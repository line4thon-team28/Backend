package com.pawpose.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해주세요")
        @Email(message = "이메일 형식을 확인해주세요")
        @Size(max = 100, message = "이메일은 100자 이하로 입력해주세요")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해주세요")
        String password,

        @NotBlank(message = "닉네임을 입력해주세요")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자로 입력해주세요")
        String nickname
) {

    /** BCrypt 는 72바이트까지만 처리함. 한글·이모지는 글자당 3~4바이트라 64자 안에서도 넘을 수 있음 */
    public static final int BCRYPT_MAX_BYTES = 72;

    @JsonIgnore
    @AssertTrue(message = "비밀번호가 너무 길어요. 조금 더 짧게 입력해주세요")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }
}
