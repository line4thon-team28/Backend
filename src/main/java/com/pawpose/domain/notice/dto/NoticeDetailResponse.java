package com.pawpose.domain.notice.dto;

import com.pawpose.domain.notice.Notice;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * S-06 공고 상세 화면 데이터.
 *
 * deadline.daysLeft : 마감까지 남은 일수 (마감일 당일 = 0, 이미 지났으면 0)
 * deadline.urgent   : 3일 이하면 true → 빨간 배지
 * deadline.closed   : 공고 종료(상태 종료 또는 마감일 지남) → 상단 배너
 * deadline.closedMessage : 배너 문구 그대로 (예: "이 공고는 종료되었어요(반환)")
 * shelter.tel 이 null 이면 전화 버튼 대신 주소만 표시 (명세 F-05 예외)
 */
public record NoticeDetailResponse(
        Long noticeId,
        String noticeNo,
        String imageUrl,
        String species,
        String breed,
        String color,
        String sex,
        String age,
        String weight,
        String specialMark,
        LocalDate happenDate,
        String happenPlace,
        LocalDate noticeStartDate,
        LocalDate noticeEndDate,
        String status,
        Deadline deadline,
        Shelter shelter
) {

    private static final int URGENT_DAYS = 3;

    public record Deadline(long daysLeft, boolean urgent, boolean closed, String closedMessage) {
    }

    public record Shelter(String name, String tel, String address, String mapUrl) {
    }

    public static NoticeDetailResponse of(Notice n, LocalDate today) {
        return new NoticeDetailResponse(
                n.getId(),
                n.getNoticeNo(),
                n.getImageUrl(),
                n.getSpecies().getLabel(),
                n.getBreed(),
                n.getColor(),
                n.getSex() != null ? n.getSex().getLabel() : null,
                n.getAge(),
                n.getWeight(),
                n.getSpecialMark(),
                n.getHappenDate(),
                n.getHappenPlace(),
                n.getNoticeStartDate(),
                n.getNoticeEndDate(),
                n.getStatus().getLabel(),
                deadline(n, today),
                new Shelter(n.getCareName(), blankToNull(n.getCareTel()), n.getCareAddr(), mapUrl(n.getCareAddr()))
        );
    }

    static Deadline deadline(Notice n, LocalDate today) {
        if (!n.getStatus().isOpen()) {
            return new Deadline(0, false, true, "이 공고는 종료되었어요(" + n.getStatus().getLabel() + ")");
        }
        if (n.getNoticeEndDate() == null) {
            return new Deadline(0, false, false, null);
        }
        long days = ChronoUnit.DAYS.between(today, n.getNoticeEndDate());
        if (days < 0) {
            return new Deadline(0, false, true, "보호 기간이 끝난 공고예요");
        }
        return new Deadline(days, days <= URGENT_DAYS, false, null);
    }

    private static String mapUrl(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return "https://map.kakao.com/link/search/" + URLEncoder.encode(address, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
