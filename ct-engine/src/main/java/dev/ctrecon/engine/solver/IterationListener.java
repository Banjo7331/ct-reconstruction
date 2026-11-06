package dev.ctrecon.engine.solver;

@FunctionalInterface
public interface IterationListener {

    IterationListener NONE = report -> {
    };

    void iterationCompleted(IterationReport report);
}
