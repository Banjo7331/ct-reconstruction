package dev.ctrecon.engine.solver;

import dev.ctrecon.engine.linalg.CsrMatrix;
import dev.ctrecon.engine.linalg.VectorOperations;
import dev.ctrecon.engine.parallel.CancellationToken;
import dev.ctrecon.engine.parallel.ParallelLoop;

import java.util.ArrayList;
import java.util.List;

public final class ConjugateGradientSolver {

    public SolverResult solve(TikhonovProblem problem, SolverSettings settings, ParallelLoop loop,
                              IterationListener listener, CancellationToken cancellation) {
        CsrMatrix a = problem.systemMatrix();
        CsrMatrix aTransposed = problem.transposedSystemMatrix();
        double alpha = problem.regularization();
        double[] m = problem.measurements();
        VectorOperations vectors = new VectorOperations(loop);

        int n = problem.unknowns();
        double[] x = new double[n];
        double[] r = new double[n];
        aTransposed.multiply(m, r, loop);
        double rightHandSideNorm = vectors.norm(r);
        double measurementNorm = vectors.norm(m);
        if (rightHandSideNorm == 0.0) {
            return new SolverResult(x, StopReason.NOTHING_TO_SOLVE, List.of());
        }

        double[] p = r.clone();
        double[] q = new double[n];
        double[] projectedDirection = new double[a.rows()];
        double[] projectedSolution = new double[a.rows()];
        double residualSquared = vectors.dot(r, r);
        List<IterationReport> history = new ArrayList<>();

        for (int iteration = 1; iteration <= settings.maxIterations(); iteration++) {
            cancellation.throwIfCancellationRequested();
            a.multiply(p, projectedDirection, loop);
            aTransposed.multiply(projectedDirection, q, loop);
            vectors.addScaled(q, alpha, p);

            double step = residualSquared / vectors.dot(p, q);
            vectors.addScaled(x, step, p);
            vectors.addScaled(projectedSolution, step, projectedDirection);
            vectors.addScaled(r, -step, q);
            double nextResidualSquared = vectors.dot(r, r);

            IterationReport report = new IterationReport(iteration,
                    Math.sqrt(nextResidualSquared) / rightHandSideNorm,
                    measurementNorm == 0.0 ? 0.0 : vectors.distance(projectedSolution, m) / measurementNorm);
            history.add(report);
            listener.iterationCompleted(report);
            if (report.relativeResidual() <= settings.tolerance()) {
                return new SolverResult(x, StopReason.CONVERGED, history);
            }

            vectors.scaleAndAdd(p, nextResidualSquared / residualSquared, r);
            residualSquared = nextResidualSquared;
        }
        return new SolverResult(x, StopReason.MAX_ITERATIONS, history);
    }
}
