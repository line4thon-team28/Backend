package com.pawpose.domain.sighting.dto;
import com.pawpose.domain.common.Species;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@Data
public class SightingCreateRequest {
    @NotNull private Species species;
    @NotBlank @Size(max=100) private String sido;
    @NotBlank @Size(max=100) private String sigungu;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private LocalDateTime seenAt;
    @Size(max=300) private String place;
    @Size(max=1000) private String memo;
    @Size(max=100) private String breed;
    @Size(max=100) private String color;
}
