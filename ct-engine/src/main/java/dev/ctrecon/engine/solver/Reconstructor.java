package dev.ctrecon.engine.solver;

import dev.ctrecon.engine.parallel.CancellationToken;
import dev.ctrecon.engine.parallel.ExecutionStrategies;
import dev.ctrecon.engine.parallel.ExecutionStrategy;

public final class Reconstructor {

    private final ConjugateGradientSolver solver = new ConjugateGradientSolver();

    public SolverResult reconstruct(ReconstructionRequest request, IterationListener listener,
                                    CancellationToken cancellation) {
        try (ExecutionStrategy strategy = ExecutionStrategies.create(request.mode(), request.threads())) {
            return solver.solve(request.problem(), request.settings(), strategy.loop(), listener, cancellation);
        }
    }
}
