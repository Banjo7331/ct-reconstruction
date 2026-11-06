package dev.ctrecon.engine.solver;

import dev.ctrecon.engine.linalg.CsrMatrix;
import dev.ctrecon.engine.parallel.CancellationFlag;
import dev.ctrecon.engine.parallel.CancellationToken;
import dev.ctrecon.engine.parallel.ExecutionMode;
import dev.ctrecon.engine.parallel.ExecutionStrategies;
import dev.ctrecon.engine.parallel.ExecutionStrategy;
import dev.ctrecon.engine.parallel.OperationCancelledException;
import dev.ctrecon.engine.parallel.SequentialLoop;
import dev.ctrecon.engine.synthetic.SyntheticScan;
import dev.ctrecon.engine.synthetic.SyntheticScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ConjugateGradientSolverTest {

    private final ConjugateGradientSolver solver = new ConjugateGradientSolver();

    @Test
    void matchesTheDirectSolutionOfTheRegularisedNormalEquations() {
        double[][] dense = {
                {1.0, 0.0, 2.0},
                {0.0, 3.0, 0.0},
                {4.0, 0.0, 0.5},
                {0.0, 1.0, 1.0}
        };
        double[] m = {1.0, -2.0, 0.5, 3.0};
        double alpha = 0.3;
        TikhonovProblem problem = TikhonovProblem.of(toCsr(dense), m, alpha);

        SolverResult result = solver.solve(problem, new SolverSettings(50, 1e-14),
                SequentialLoop.INSTANCE, IterationListener.NONE, CancellationToken.NONE);

        double[] expected = solveNormalEquations(dense, m, alpha);
        assertThat(result.stopReason()).isEqualTo(StopReason.CONVERGED);
        assertThat(result.iterations()).isLessThanOrEqualTo(4);
        for (int i = 0; i < expected.length; i++) {
            assertThat(result.solution()[i]).isCloseTo(expected[i], within(1e-10));
        }
    }

    @Test
    void reconstructsASyntheticScanCloseToThePhantom() {
        SyntheticScan scan = SyntheticScanner.parallelBeam(32, 60);
        TikhonovProblem problem = TikhonovProblem.of(scan.systemMatrix(), scan.measurements(), 1e-3);

        SolverResult result = solver.solve(problem, new SolverSettings(100, 1e-8),
                SequentialLoop.INSTANCE, IterationListener.NONE, CancellationToken.NONE);

        assertThat(relativeError(result.solution(), scan.phantom())).isLessThan(0.1);
        IterationReport last = result.history().getLast();
        assertThat(last.relativeMisfit()).isLessThan(0.01);
    }

    @ParameterizedTest
    @EnumSource(value = ExecutionMode.class, names = {"STATIC_BLOCKS", "FORK_JOIN"})
    void parallelExecutionReproducesTheSequentialSolutionBitForBit(ExecutionMode mode) {
        SyntheticScan scan = SyntheticScanner.parallelBeam(32, 40);
        TikhonovProblem problem = TikhonovProblem.of(scan.systemMatrix(), scan.measurements(), 10.0);
        SolverSettings settings = new SolverSettings(20, 0.0);

        SolverResult sequential = solver.solve(problem, settings, SequentialLoop.INSTANCE,
                IterationListener.NONE, CancellationToken.NONE);
        SolverResult parallel;
        try (ExecutionStrategy strategy = ExecutionStrategies.create(mode, 3)) {
            parallel = solver.solve(problem, settings, strategy.loop(), IterationListener.NONE, CancellationToken.NONE);
        }

        assertThat(parallel.solution()).containsExactly(sequential.solution());
        assertThat(parallel.history()).isEqualTo(sequential.history());
    }

    @Test
    void reportsEveryIterationInOrder() {
        SyntheticScan scan = SyntheticScanner.parallelBeam(16, 20);
        List<Integer> seen = new ArrayList<>();

        SolverResult result = solver.solve(TikhonovProblem.of(scan.systemMatrix(), scan.measurements(), 10.0),
                new SolverSettings(7, 0.0), SequentialLoop.INSTANCE,
                report -> seen.add(report.iteration()), CancellationToken.NONE);

        assertThat(result.stopReason()).isEqualTo(StopReason.MAX_ITERATIONS);
        assertThat(seen).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void stopsBetweenIterationsWhenCancelled() {
        SyntheticScan scan = SyntheticScanner.parallelBeam(16, 20);
        CancellationFlag cancellation = new CancellationFlag();
        List<Integer> seen = new ArrayList<>();

        assertThatThrownBy(() -> solver.solve(TikhonovProblem.of(scan.systemMatrix(), scan.measurements(), 10.0),
                new SolverSettings(50, 0.0), SequentialLoop.INSTANCE,
                report -> {
                    seen.add(report.iteration());
                    if (report.iteration() == 3) {
                        cancellation.requestCancellation();
                    }
                }, cancellation))
                .isInstanceOf(OperationCancelledException.class);

        assertThat(seen).containsExactly(1, 2, 3);
    }

    @Test
    void returnsZeroImageForZeroMeasurements() {
        SyntheticScan scan = SyntheticScanner.parallelBeam(16, 10);

        SolverResult result = solver.solve(
                TikhonovProblem.of(scan.systemMatrix(), new double[scan.systemMatrix().rows()], 1.0),
                new SolverSettings(10, 1e-6), SequentialLoop.INSTANCE, IterationListener.NONE, CancellationToken.NONE);

        assertThat(result.stopReason()).isEqualTo(StopReason.NOTHING_TO_SOLVE);
        assertThat(result.solution()).containsOnly(0.0);
    }

    @Test
    void rejectsInvalidSettingsAndProblems() {
        assertThatThrownBy(() -> new SolverSettings(0, 1e-6)).isInstanceOf(IllegalArgumentException.class);
        CsrMatrix matrix = toCsr(new double[][]{{1.0}});
        assertThatThrownBy(() -> TikhonovProblem.of(matrix, new double[]{1.0}, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TikhonovProblem.of(matrix, new double[2], 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static double relativeError(double[] actual, double[] expected) {
        double difference = 0.0;
        double reference = 0.0;
        for (int i = 0; i < actual.length; i++) {
            difference += (actual[i] - expected[i]) * (actual[i] - expected[i]);
            reference += expected[i] * expected[i];
        }
        return Math.sqrt(difference / reference);
    }

    private static CsrMatrix toCsr(double[][] dense) {
        int rows = dense.length;
        int columns = dense[0].length;
        int[] pointers = new int[rows + 1];
        List<Integer> indices = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (dense[row][column] != 0.0) {
                    indices.add(column);
                    values.add(dense[row][column]);
                }
            }
            pointers[row + 1] = indices.size();
        }
        return CsrMatrix.fromArrays(rows, columns, pointers,
                indices.stream().mapToInt(Integer::intValue).toArray(),
                values.stream().mapToDouble(Double::doubleValue).toArray());
    }

    private static double[] solveNormalEquations(double[][] a, double[] m, double alpha) {
        int n = a[0].length;
        double[][] system = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (double[] row : a) {
                    system[i][j] += row[i] * row[j];
                }
            }
            system[i][i] += alpha;
            for (int k = 0; k < a.length; k++) {
                system[i][n] += a[k][i] * m[k];
            }
        }
        for (int pivot = 0; pivot < n; pivot++) {
            for (int row = pivot + 1; row < n; row++) {
                double factor = system[row][pivot] / system[pivot][pivot];
                for (int column = pivot; column <= n; column++) {
                    system[row][column] -= factor * system[pivot][column];
                }
            }
        }
        double[] solution = new double[n];
        for (int row = n - 1; row >= 0; row--) {
            double sum = system[row][n];
            for (int column = row + 1; column < n; column++) {
                sum -= system[row][column] * solution[column];
            }
            solution[row] = sum / system[row][row];
        }
        return solution;
    }
}
