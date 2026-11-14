package com.pawpose.domain.notice;

import com.pawpose.domain.common.Sex;
import com.pawpose.domain.common.Species;
import com.pawpose.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 보호소 공고. 데이터는 BE-1(F-03 스케줄러)이 채우고, F-05 상세 조회는 읽기만 합니다.
 * 컬럼은 명세서 F-03 "저장 항목" 기준. 이름·타입을 바꿀 때는 BE-1 과 먼저 맞추세요.
 * 임베딩 벡터 컬럼은 BE-2 가 모델 확정 후 추가합니다.
 */
@Getter
@Entity
@Table(name = "notice", uniqueConstraints =
        @UniqueConstraint(name = "uk_notice_desertion_no", columnNames = "desertion_no"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 공공 API 유기번호. upsert 기준 키 */
    @Column(name = "desertion_no", nullable = false, length = 30)
    private String desertionNo;

    /** 공고번호 (예: 서울-강북-2026-00231) */
    @Column(length = 50)
    private String noticeNo;

    private LocalDate noticeStartDate;

    /** 보호 기간 마감일. 카운트다운 기준 */
    private LocalDate noticeEndDate;

    private LocalDate happenDate;

    @Column(length = 200)
    private String happenPlace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Species species;

    @Column(length = 50)
    private String breed;

    @Column(length = 50)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sex sex;

    /** 공공 API 원문 그대로 (예: "2024(년생)") */
    @Column(length = 30)
    private String age;

    /** 공공 API 원문 그대로 (예: "3.8(Kg)") */
    @Column(length = 30)
    private String weight;

    @Column(length = 500)
    private String specialMark;

    @Column(length = 100)
    private String careName;

    @Column(length = 30)
    private String careTel;

    @Column(length = 200)
    private String careAddr;

    /** 담당 시군구 (공공 API orgNm, 예: "서울특별시 강북구") */
    @Column(length = 100)
    private String orgName;

    @Column(length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeStatus status;

    @Builder
    private Notice(String desertionNo, String noticeNo, LocalDate noticeStartDate, LocalDate noticeEndDate,
                   LocalDate happenDate, String happenPlace, Species species, String breed, String color, Sex sex,
                   String age, String weight, String specialMark, String careName, String careTel,
                   String careAddr, String orgName, String imageUrl, NoticeStatus status) {
        this.desertionNo = desertionNo;
        this.noticeNo = noticeNo;
        this.noticeStartDate = noticeStartDate;
        this.noticeEndDate = noticeEndDate;
        this.happenDate = happenDate;
        this.happenPlace = happenPlace;
        this.species = species;
        this.breed = breed;
        this.color = color;
        this.sex = sex;
        this.age = age;
        this.weight = weight;
        this.specialMark = specialMark;
        this.careName = careName;
        this.careTel = careTel;
        this.careAddr = careAddr;
        this.orgName = orgName;
        this.imageUrl = imageUrl;
        this.status = status != null ? status : NoticeStatus.NOTICE;
    }

    /** BE-1 스케줄러가 상태 변경(입양·반환 등)을 반영할 때 사용 */
    public void changeStatus(NoticeStatus status) {
        this.status = status;
    }
}
