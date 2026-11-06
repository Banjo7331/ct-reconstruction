package dev.ctrecon.core.application;

import dev.ctrecon.core.domain.ReconstructionId;

public final class ReconstructionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReconstructionNotFoundException(ReconstructionId id) {
        super("Reconstruction " + id + " does not exist");
    }
}
