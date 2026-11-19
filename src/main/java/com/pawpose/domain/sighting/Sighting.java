package com.pawpose.domain.sighting;
import com.pawpose.domain.common.Species;
import com.pawpose.domain.sighting.dto.SightingCreateRequest;
import com.pawpose.domain.registration.RegistrationReferenceService.Region;
import com.pawpose.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Getter @Entity @Table(name="sighting") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Sighting extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Species species;
    @Column(nullable=false) private LocalDateTime seenAt;
    @Column(nullable=false,length=20) private String sido;
    @Column(nullable=false,length=50) private String sidoName;
    @Column(nullable=false,length=100) private String sigungu;
    @Column(length=100) private String sigunguCode;
    @Column(length=300) private String place;
    @Column(length=1000) private String memo;
    @Column(length=100) private String breed;
    @Column(length=100) private String color;

    @ElementCollection
    @CollectionTable(name="sighting_photo",joinColumns=@JoinColumn(name="sighting_id"))
    @OrderColumn(name="photo_order") @Column(name="url",nullable=false,length=2048)
    private List<String> photos=new ArrayList<>();

    public static Sighting create(SightingCreateRequest form,Region region,List<String> photos) {
        Sighting s=new Sighting();
        s.species=form.getSpecies(); s.seenAt=form.getSeenAt();
        s.sido=region.sidoCode(); s.sidoName=region.sidoName();
        s.sigungu=region.sigungu(); s.sigunguCode=region.sigunguCode();
        s.place=form.getPlace(); s.memo=form.getMemo(); s.breed=form.getBreed(); s.color=form.getColor();
        s.photos.addAll(photos);
        return s;
    }
}
