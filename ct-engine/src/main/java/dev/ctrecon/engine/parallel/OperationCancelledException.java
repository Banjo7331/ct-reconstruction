package dev.ctrecon.engine.parallel;

public final class OperationCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperationCancelledException(String message) {
        super(message);
    }

    public OperationCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
