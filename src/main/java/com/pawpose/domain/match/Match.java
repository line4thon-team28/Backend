package com.pawpose.domain.match;

import com.pawpose.domain.lostpet.LostPet;
import com.pawpose.domain.notice.Notice;
import com.pawpose.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 매칭 후보 (명세 F-04 "match 테이블": 실종 ID, 공고 ID, 점수, 생성 시각, 피드백).
 * 행 생성·점수 갱신은 BE-2(F-04), 목격 후보는 F-06 담당이 만듭니다. 여기서는 피드백만 바꿉니다.
 */
@Getter
@Entity
@Table(name = "matches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_pet_id", nullable = false)
    private LostPet lostPet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MatchType type;

    /** type = NOTICE 일 때 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    private Notice notice;

    /** type = SIGHTING 일 때. Sighting 엔티티가 생기면 F-06 담당이 @ManyToOne 으로 바꾸면 됩니다 */
    @Column(name = "sighting_id")
    private Long sightingId;

    /** 최종 유사도 0~100 (%) */
    @Column(nullable = false)
    private double similarity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MatchFeedback feedback;

    private LocalDateTime feedbackAt;

    public static Match ofNotice(LostPet lostPet, Notice notice, double similarity) {
        Match match = new Match();
        match.lostPet = lostPet;
        match.type = MatchType.NOTICE;
        match.notice = notice;
        match.similarity = similarity;
        match.feedback = MatchFeedback.NONE;
        return match;
    }

    public static Match ofSighting(LostPet lostPet, Long sightingId, double similarity) {
        Match match = new Match();
        match.lostPet = lostPet;
        match.type = MatchType.SIGHTING;
        match.sightingId = sightingId;
        match.similarity = similarity;
        match.feedback = MatchFeedback.NONE;
        return match;
    }

    /** BE-2: 재매칭 시 점수만 갱신 (피드백은 유지) */
    public void updateSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public void applyFeedback(MatchFeedback feedback) {
        this.feedback = feedback;
        this.feedbackAt = feedback == MatchFeedback.NONE ? null : LocalDateTime.now();
    }
}
