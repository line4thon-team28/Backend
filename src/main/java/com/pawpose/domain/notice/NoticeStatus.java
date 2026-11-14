package com.pawpose.domain.notice;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공고 상태. BE-1 이 공공 API processState("보호중", "종료(반환)" 등)를 이 값으로 바꿔 저장합니다.
 * NOTICE 만 "공고중"이고 나머지는 모두 종료입니다.
 */
@Getter
@RequiredArgsConstructor
public enum NoticeStatus {
    NOTICE("공고중"),
    RETURNED("반환"),
    ADOPTED("입양"),
    EUTHANIZED("안락사"),
    NATURAL_DEATH("자연사"),
    DONATED("기증"),
    RELEASED("방사"),
    ENDED("종료");

    private final String label;

    public boolean isOpen() {
        return this == NOTICE;
    }
}
