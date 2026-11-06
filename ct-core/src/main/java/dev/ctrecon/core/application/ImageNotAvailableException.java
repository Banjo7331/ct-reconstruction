package dev.ctrecon.core.application;

import dev.ctrecon.core.domain.ReconstructionId;

public final class ImageNotAvailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageNotAvailableException(ReconstructionId id, String reason) {
        super("Image of reconstruction " + id + " is not available: " + reason);
    }
}
