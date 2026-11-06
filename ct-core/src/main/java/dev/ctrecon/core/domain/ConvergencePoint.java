package dev.ctrecon.core.domain;

public record ConvergencePoint(int iteration, double relativeResidual, double relativeMisfit) {

    public ConvergencePoint {
        if (iteration < 1) {
            throw new IllegalArgumentException("iteration must be positive, got " + iteration);
        }
        if (!(relativeResidual >= 0.0) || !(relativeMisfit >= 0.0)
                || Double.isInfinite(relativeResidual) || Double.isInfinite(relativeMisfit)) {
            throw new IllegalArgumentException("Residual and misfit must be finite and non-negative");
        }
    }
}
