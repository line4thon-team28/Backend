package com.pawpose.domain.lostpet;

import com.pawpose.domain.common.Species;
import com.pawpose.domain.common.Sex;
import com.pawpose.domain.lostpet.dto.LostPetCreateRequest;
import com.pawpose.domain.registration.RegistrationReferenceService.Region;
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
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.OrderColumn;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    @Column(length = 100) private String name;
    @Column(length = 100) private String breed;
    @Enumerated(EnumType.STRING) @Column(length = 10) private Sex sex;
    @Column(length = 100) private String color;
    private LocalDate lostDate;
    @Column(length = 20) private String sido;
    @Column(length = 50) private String sidoName;
    @Column(length = 100) private String sigungu;
    @Column(length = 100) private String sigunguCode;
    @Column(length = 300) private String place;
    @Column(length = 1000) private String features;
    @Column(length = 30) private String phone;

    @ElementCollection
    @CollectionTable(name = "lost_pet_photo", joinColumns = @JoinColumn(name = "lost_pet_id"))
    @OrderColumn(name = "photo_order")
    @Column(name = "url", length = 2048, nullable = false)
    private List<String> photos = new ArrayList<>();

    public static LostPet create(User user, Species species) {
        LostPet lostPet = new LostPet();
        lostPet.user = user;
        lostPet.species = species;
        lostPet.status = LostPetStatus.SEARCHING;
        return lostPet;
    }

    public static LostPet register(User user, LostPetCreateRequest request, Region region, List<String> photos) {
        LostPet pet = create(user, request.getSpecies());
        pet.name = request.getName();
        pet.breed = request.getBreed();
        pet.sex = request.getSex() == null ? Sex.UNKNOWN : request.getSex();
        pet.color = request.getColor();
        pet.lostDate = request.getLostDate();
        pet.sido = region.sidoCode();
        pet.sidoName = region.sidoName();
        pet.sigungu = region.sigungu();
        pet.sigunguCode = region.sigunguCode();
        pet.place = request.getPlace();
        pet.features = request.getFeatures();
        pet.phone = request.getPhone();
        pet.photos.addAll(photos);
        return pet;
    }

    public void updateContact(String features, String phone) {
        if (features != null) this.features = features;
        if (phone != null) this.phone = phone;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    /** Invoke through MatchingInputService.markFound so in-flight matching uses the same lock. */
    public void markFound() { this.status = LostPetStatus.FOUND; }
}
