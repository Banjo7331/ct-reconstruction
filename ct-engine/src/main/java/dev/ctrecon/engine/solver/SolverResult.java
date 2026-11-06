package dev.ctrecon.engine.solver;

import java.util.List;

public record SolverResult(double[] solution, StopReason stopReason, List<IterationReport> history) {

    public SolverResult {
        history = List.copyOf(history);
    }

    public int iterations() {
        return history.size();
    }
}
