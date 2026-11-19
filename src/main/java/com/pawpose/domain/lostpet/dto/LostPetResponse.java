package com.pawpose.domain.lostpet.dto;
import com.pawpose.domain.common.*;
import com.pawpose.domain.lostpet.*;
import java.time.LocalDate;
import java.util.List;
public record LostPetResponse(Long lostPetId, String name, Species species, String breed,
    Sex sex, String color, LocalDate lostDate, String sido, String sidoName, String sigungu,
    String sigunguCode, String place, String features, String phone, LostPetStatus status,
    List<String> photos, String thumbnail, long matchCount) {
    public static LostPetResponse from(LostPet p, long count) {
        List<String> photos=List.copyOf(p.getPhotos());
        return new LostPetResponse(p.getId(),p.getName(),p.getSpecies(),p.getBreed(),p.getSex(),
            p.getColor(),p.getLostDate(),p.getSido(),p.getSidoName(),p.getSigungu(),p.getSigunguCode(),
            p.getPlace(),p.getFeatures(),p.getPhone(),p.getStatus(),photos,
            photos.isEmpty()?null:photos.get(0),count);
    }
}
