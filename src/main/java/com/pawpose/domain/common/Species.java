package com.pawpose.domain.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 종. 실종 등록·공고·제보가 같이 씁니다. 공공 API upKindCd: 417000=개, 422400=고양이, 429900=기타 */
@Getter
@RequiredArgsConstructor
public enum Species {
    DOG("개"),
    CAT("고양이"),
    ETC("기타");

    private final String label;
}
