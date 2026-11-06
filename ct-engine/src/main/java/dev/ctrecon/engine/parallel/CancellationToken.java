package dev.ctrecon.engine.parallel;

@FunctionalInterface
public interface CancellationToken {

    CancellationToken NONE = () -> false;

    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new OperationCancelledException("Cancellation was requested");
        }
    }
}
