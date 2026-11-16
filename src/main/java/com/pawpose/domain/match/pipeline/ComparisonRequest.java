package com.pawpose.domain.match.pipeline;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** Written with the target change; consumed only after that transaction commits. */
@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "matching_comparison_request")
public class ComparisonRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Instant createdAt;

    static ComparisonRequest create() {
        ComparisonRequest request = new ComparisonRequest();
        request.createdAt = Instant.now();
        return request;
    }
}
