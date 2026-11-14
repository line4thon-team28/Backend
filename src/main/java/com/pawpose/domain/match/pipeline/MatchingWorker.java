package com.pawpose.domain.match.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Slf4j @Component @RequiredArgsConstructor
public class MatchingWorker {
    private final MatchingProperties properties;
    private final MatchingSourceRepository sources;
    private final MatchingWorkService workService;
    private final EmbeddingClient embedding;
    private final MatchingJson json;
    private final ComparisonDispatchService comparisons;

    @Scheduled(fixedDelayString = "${pawpose.matching.poll-ms:10000}", initialDelayString = "${pawpose.matching.initial-delay-ms:10000}")
    public void poll() {
        if (!properties.isEnabled()) return;
        comparisons.dispatchPending();
        workService.requeueOutdatedModels();
        for (Long id : sources.workIds(Instant.now(), PageRequest.of(0, 10))) process(id);
        comparisons.dispatchPending();
    }

    public void process(Long id) {
        workService.claim(id).ifPresent(work -> {
            try {
                MatchingSource source = work.source();
                double[][] vectors = source.getVectorsJson() != null && properties.getModelVersion().equals(source.getModelVersion())
                        ? json.vectors(source.getVectorsJson())
                        : json.images(source.getImagesJson()).stream().map(embedding::embed).toArray(double[][]::new);
                if (vectors.length == 0) throw new IllegalArgumentException("NO_IMAGES");
                if (source.getKind() == SourceKind.LOST_PET) workService.completeLostPet(work, vectors, workService.score(source, vectors));
                else workService.completeTarget(work, vectors);
            } catch (Exception e) {
                workService.fail(work, e instanceof AnalysisException ? e.getMessage() : "IMAGE_ANALYSIS_FAILED",
                        !(e instanceof AnalysisException a) || a.retryable());
                log.warn("Matching source {} failed; retry state persisted", id);
            }
        });
    }
}
