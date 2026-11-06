package dev.ctrecon.core.application.port.out;

public final class QueueFullException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueueFullException(String message, Throwable cause) {
        super(message, cause);
    }
}
