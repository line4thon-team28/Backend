package com.pawpose.domain.lostpet;

import com.pawpose.domain.common.Species;
import com.pawpose.domain.user.User;
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

/**
 * [최소 버전] 실종 등록. F-07 피드백의 "내 것인지" 확인에 필요한 필드만 있습니다.
 * F-02 담당자가 사진·품종·실종 날짜·위치·특징·연락처 등을 이어서 추가하면 됩니다.
 */
@Getter
@Entity
@Table(name = "lost_pet")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LostPet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Species species;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LostPetStatus status;

    public static LostPet create(User user, Species species) {
        LostPet lostPet = new LostPet();
        lostPet.user = user;
        lostPet.species = species;
        lostPet.status = LostPetStatus.SEARCHING;
        return lostPet;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    /** Invoke through MatchingInputService.markFound so in-flight matching uses the same lock. */
    public void markFound() { this.status = LostPetStatus.FOUND; }
}
