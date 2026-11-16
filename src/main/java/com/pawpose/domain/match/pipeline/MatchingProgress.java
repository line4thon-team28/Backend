package com.pawpose.domain.match.pipeline;

/** Scalar snapshot: avoids reusing a source entity loaded before dispatch committed. */
public record MatchingProgress(ProcessingStatus status, String errorCode, boolean comparisonQueued) {}
