package com.pawpose.domain.notice.collection;

import com.pawpose.domain.common.Sex;
import com.pawpose.domain.common.Species;
import com.pawpose.domain.notice.Notice;
import com.pawpose.domain.notice.NoticeStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public record CollectedNotice(
        String desertionNo, String noticeNo, LocalDate noticeStartDate, LocalDate noticeEndDate,
        LocalDate happenDate, String happenPlace, Species species, String breed, String color, Sex sex,
        String age, String weight, String specialMark, String careName, String careTel, String careAddr,
        String orgName, String imageUrl, NoticeStatus status, String sidoCode, String sigunguCode) {

    public Notice toEntity() {
        return Notice.builder().desertionNo(desertionNo).noticeNo(noticeNo)
                .noticeStartDate(noticeStartDate).noticeEndDate(noticeEndDate)
                .happenDate(happenDate).happenPlace(happenPlace).species(species).breed(breed)
                .color(color).sex(sex).age(age).weight(weight).specialMark(specialMark)
                .careName(careName).careTel(careTel).careAddr(careAddr).orgName(orgName)
                .imageUrl(imageUrl).status(status).build();
    }

    public CollectedNotice withSigunguCode(String code) {
        return new CollectedNotice(desertionNo, noticeNo, noticeStartDate, noticeEndDate, happenDate,
                happenPlace, species, breed, color, sex, age, weight, specialMark, careName, careTel,
                careAddr, orgName, imageUrl, status, sidoCode, code);
    }

    public String fingerprint() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
