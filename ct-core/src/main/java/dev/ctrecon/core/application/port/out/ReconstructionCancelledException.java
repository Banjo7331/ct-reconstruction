package dev.ctrecon.core.application.port.out;

public final class ReconstructionCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReconstructionCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
