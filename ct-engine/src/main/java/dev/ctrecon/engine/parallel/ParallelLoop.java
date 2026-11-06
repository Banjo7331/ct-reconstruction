package dev.ctrecon.engine.parallel;

public interface ParallelLoop {

    void forRange(int fromInclusive, int toExclusive, RangeTask task);
}
