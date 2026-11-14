package com.pawpose.domain.notice.collection;

import com.pawpose.domain.notice.Notice;
import com.pawpose.domain.notice.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticePageStore {
    private final NoticeRepository notices;
    private final CollectionStateStore stateStore;
    private final ApplicationEventPublisher events;

    @Transactional
    public int save(String token, List<CollectedNotice> page, LocalDateTime now) {
        CollectionState state = stateStore.locked();
        state.checkLease(token, now);
        int changed = 0;
        for (CollectedNotice item : page) {
            Notice incoming = item.toEntity();
            Notice notice = notices.findByDesertionNo(item.desertionNo()).orElse(incoming);
            if (notice.applyCollection(incoming, item.sidoCode(), item.sigunguCode(), item.fingerprint(), now)) {
                notices.saveAndFlush(notice);
                events.publishEvent(new NoticeCollectedEvent(notice.getId()));
                changed++;
            }
        }
        state.renew(now);
        return changed;
    }
}
