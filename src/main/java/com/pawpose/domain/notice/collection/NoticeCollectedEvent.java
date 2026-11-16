package com.pawpose.domain.notice.collection;

/** Published inside the upsert transaction; matching input is persisted BEFORE_COMMIT. */
public record NoticeCollectedEvent(Long noticeId) {
}
