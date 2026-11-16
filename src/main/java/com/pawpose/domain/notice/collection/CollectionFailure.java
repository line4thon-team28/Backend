package com.pawpose.domain.notice.collection;

/** Deliberately excludes provider response bodies, request URLs and credentials. */
public class CollectionFailure extends RuntimeException {
    public enum Reason { TRANSIENT, QUOTA, INVALID_RESPONSE, REJECTED, LEASE_LOST }

    private final Reason reason;

    public CollectionFailure(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
