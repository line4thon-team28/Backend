package com.pawpose.domain.match.pipeline;

import java.time.LocalDate;
import java.util.List;

/** F-02/F-06 call the matching input service after persisting their own registration. */
public record MatchingProfile(List<String> imageUrls, LocalDate occurredOn, String sidoCode,
                              String sigunguCode, String breed, String color, String place) {
    public MatchingProfile {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
