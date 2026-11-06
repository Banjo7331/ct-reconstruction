package dev.ctrecon.engine.parallel;

public final class CancellationFlag implements CancellationToken {

    private volatile boolean requested;

    public void requestCancellation() {
        requested = true;
    }

    @Override
    public boolean isCancellationRequested() {
        return requested;
    }
}
