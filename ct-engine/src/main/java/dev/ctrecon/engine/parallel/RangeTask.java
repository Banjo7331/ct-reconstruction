package dev.ctrecon.engine.parallel;

@FunctionalInterface
public interface RangeTask {

    void run(int fromInclusive, int toExclusive);
}
