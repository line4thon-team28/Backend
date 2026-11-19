package com.pawpose.domain.lostpet.dto;
import com.pawpose.domain.lostpet.LostPetStatus;
import jakarta.validation.constraints.Size;
public record LostPetUpdateRequest(LostPetStatus status,
    @Size(max=1000) String features, @Size(max=30) String phone) {}
