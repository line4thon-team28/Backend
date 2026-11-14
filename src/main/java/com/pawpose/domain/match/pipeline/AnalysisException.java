package com.pawpose.domain.match.pipeline;

public class AnalysisException extends RuntimeException {
    private final boolean retryable;
    public AnalysisException(String code, boolean retryable) { super(code); this.retryable = retryable; }
    public boolean retryable() { return retryable; }
}
