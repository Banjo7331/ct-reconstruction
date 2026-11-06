package dev.ctrecon.engine.parallel;

public interface ExecutionStrategy extends AutoCloseable {

    ParallelLoop loop();

    @Override
    void close();
}
