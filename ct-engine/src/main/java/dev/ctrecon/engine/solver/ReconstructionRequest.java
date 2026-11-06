package dev.ctrecon.engine.solver;

import dev.ctrecon.engine.parallel.ExecutionMode;

import java.util.Objects;

public record ReconstructionRequest(TikhonovProblem problem, SolverSettings settings, ExecutionMode mode, int threads) {

    public ReconstructionRequest {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(mode, "mode");
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive, got " + threads);
        }
    }
}
