package dev.ctrecon.core.domain;

public final class IllegalReconstructionStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IllegalReconstructionStateException(ReconstructionId id, ReconstructionStatus status, String action) {
        super("Cannot " + action + " reconstruction " + id + " in status " + status);
    }
}
