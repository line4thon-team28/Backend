package com.pawpose.global.error;

import lombok.Getter;

/** 서비스 코드에서 throw new BusinessException(ErrorCode.XXX) 로 쓰면 공통 실패 응답으로 바뀝니다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
