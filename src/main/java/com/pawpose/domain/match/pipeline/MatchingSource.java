package com.pawpose.domain.match.pipeline;

import com.pawpose.domain.common.Species;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "matching_source", uniqueConstraints = @UniqueConstraint(name = "uk_matching_source", columnNames = {"kind", "source_id"}),
       indexes = @Index(name = "ix_matching_work", columnList = "status,available_at"))
public class MatchingSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SourceKind kind;
    @Column(name = "source_id", nullable = false) private Long sourceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Species species;
    private LocalDate occurredOn;
    @Column(length = 100) private String sidoCode;
    @Column(length = 100) private String sigunguCode;
    @Column(length = 100) private String breed;
    @Column(length = 100) private String color;
    @Column(length = 500) private String place;
    @Column(columnDefinition = "text", nullable = false) private String imagesJson;
    @Column(columnDefinition = "text") private String vectorsJson;
    @Column(length = 200) private String modelVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProcessingStatus status;
    private boolean active;
    private long generation;
    private int attempts;
    private boolean automaticRetry = true;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    private Instant leaseUntil;
    @Column(length = 36) private String leaseToken;
    @Column(length = 40) private String errorCode;

    static MatchingSource create(SourceKind kind, Long sourceId) {
        MatchingSource s = new MatchingSource(); s.kind = kind; s.sourceId = sourceId; return s;
    }

    void register(Species species, MatchingProfile p, String imagesJson, boolean active) {
        if (!imagesJson.equals(this.imagesJson)) { vectorsJson = null; modelVersion = null; }
        this.species = species; this.occurredOn = p.occurredOn();
        this.sidoCode = RegionPolicy.canonical(p.sidoCode()); this.sigunguCode = p.sigunguCode();
        this.breed = p.breed(); this.color = p.color(); this.place = p.place();
        this.imagesJson = imagesJson; this.active = active;
        enqueue();
        if (!active) status = ProcessingStatus.CANCELLED;
    }

    void enqueue() {
        generation++; attempts = 0; automaticRetry = true; status = ProcessingStatus.PENDING;
        availableAt = Instant.now(); leaseToken = null; leaseUntil = null; errorCode = null;
    }

    boolean claim(String token, Instant now) {
        if (!active || availableAt.isAfter(now)) return false;
        if (status != ProcessingStatus.PENDING && status != ProcessingStatus.FAILED && !(status == ProcessingStatus.PROCESSING && leaseUntil != null && !leaseUntil.isAfter(now))) return false;
        if (status == ProcessingStatus.FAILED) { if (!automaticRetry) return false; attempts = 0; }
        status = ProcessingStatus.PROCESSING; leaseToken = token; leaseUntil = now.plusSeconds(900); attempts++; return true;
    }

    boolean owned(String token, long generation) { return this.generation == generation && token.equals(leaseToken) && status == ProcessingStatus.PROCESSING; }
    void complete(String vectors, String version) {
        vectorsJson = vectors; modelVersion = version; status = ProcessingStatus.READY;
        errorCode = null; leaseToken = null; leaseUntil = null;
    }
    void fail(String code, int maxAttempts, boolean retryable) {
        errorCode = code; leaseToken = null; leaseUntil = null;
        automaticRetry = retryable;
        status = retryable && attempts < maxAttempts ? ProcessingStatus.PENDING : ProcessingStatus.FAILED;
        availableAt = Instant.now().plusSeconds(status == ProcessingStatus.FAILED ? 1800 : 30L * (1L << Math.min(attempts, 5)));
    }
    void cancel() { active = false; generation++; status = ProcessingStatus.CANCELLED; leaseToken = null; leaseUntil = null; }
}
