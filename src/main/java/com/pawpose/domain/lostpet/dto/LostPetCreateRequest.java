package com.pawpose.domain.lostpet.dto;
import com.pawpose.domain.common.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Data
public class LostPetCreateRequest {
    @Size(max=100) private String name;
    @NotNull private Species species;
    @Size(max=100) private String breed;
    private Sex sex = Sex.UNKNOWN;
    @Size(max=100) private String color;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate lostDate;
    @NotBlank @Size(max=100) private String sido;
    @NotBlank @Size(max=100) private String sigungu;
    @Size(max=300) private String place;
    @Size(max=1000) private String features;
    @Size(max=30) private String phone;
}
