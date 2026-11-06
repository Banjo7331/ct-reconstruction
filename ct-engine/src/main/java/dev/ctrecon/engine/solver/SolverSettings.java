package dev.ctrecon.engine.solver;

public record SolverSettings(int maxIterations, double tolerance) {

    public SolverSettings {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be positive, got " + maxIterations);
        }
        if (!(tolerance >= 0.0) || Double.isInfinite(tolerance)) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative, got " + tolerance);
        }
    }
}
