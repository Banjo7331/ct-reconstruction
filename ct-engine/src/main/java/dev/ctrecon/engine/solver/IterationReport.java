package dev.ctrecon.engine.solver;

public record IterationReport(int iteration, double relativeResidual, double relativeMisfit) {
}
