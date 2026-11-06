package dev.ctrecon.engine.parallel;

public final class SequentialLoop implements ParallelLoop {

    public static final SequentialLoop INSTANCE = new SequentialLoop();

    private SequentialLoop() {
    }

    @Override
    public void forRange(int fromInclusive, int toExclusive, RangeTask task) {
        if (fromInclusive < toExclusive) {
            task.run(fromInclusive, toExclusive);
        }
    }
}
