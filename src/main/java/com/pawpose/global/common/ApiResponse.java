package com.pawpose.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pawpose.global.error.ErrorCode;

/**
 * 명세서 6장 공통 응답 형식.
 * 성공: { "data": ..., "message": null }
 * 실패: { "data": null, "message": "사용자에게 보여줄 문구", "code": "LOST_PET_NOT_FOUND" }
 */
public record ApiResponse<T>(
        T data,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(null, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(null, errorCode.getMessage(), errorCode.name());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(null, message, errorCode.name());
    }
}
