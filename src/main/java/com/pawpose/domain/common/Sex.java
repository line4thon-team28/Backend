package com.pawpose.domain.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 성별. 공공 API sexCd: M / F / Q(미상) */
@Getter
@RequiredArgsConstructor
public enum Sex {
    MALE("수컷"),
    FEMALE("암컷"),
    UNKNOWN("모름");

    private final String label;
}
