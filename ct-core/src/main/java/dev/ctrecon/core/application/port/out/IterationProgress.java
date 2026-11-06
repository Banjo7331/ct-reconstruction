package dev.ctrecon.core.application.port.out;

import dev.ctrecon.core.domain.ConvergencePoint;

@FunctionalInterface
public interface IterationProgress {

    void iterationCompleted(ConvergencePoint point);
}
