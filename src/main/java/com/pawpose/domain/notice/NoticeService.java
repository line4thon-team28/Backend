package com.pawpose.domain.notice;

import com.pawpose.domain.notice.dto.NoticeDetailResponse;
import com.pawpose.global.error.BusinessException;
import com.pawpose.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final NoticeRepository noticeRepository;

    public NoticeDetailResponse getDetail(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        return NoticeDetailResponse.of(notice, LocalDate.now(SEOUL));
    }
}
