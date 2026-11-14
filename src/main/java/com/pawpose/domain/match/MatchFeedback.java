package com.pawpose.domain.match;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * F-07 피드백.
 * YES  = 맞아요   → 목록 맨 위 고정
 * NO   = 아니에요 → 목록에서 숨김 (삭제 안 함)
 * NONE = 아직 안 누름 (잘못 눌렀을 때 되돌리기에도 사용)
 */
public enum MatchFeedback {
    NONE,
    YES,
    NO;

    /** "yes", "Yes", "YES" 모두 허용 */
    @JsonCreator
    public static MatchFeedback from(String value) {
        if (value == null) {
            return null;
        }
        return MatchFeedback.valueOf(value.trim().toUpperCase());
    }
}
