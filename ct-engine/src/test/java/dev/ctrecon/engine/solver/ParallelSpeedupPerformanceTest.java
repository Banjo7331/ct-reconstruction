package dev.ctrecon.engine.solver;

import dev.ctrecon.engine.parallel.CancellationToken;
import dev.ctrecon.engine.parallel.ExecutionMode;
import dev.ctrecon.engine.synthetic.SyntheticScan;
import dev.ctrecon.engine.synthetic.SyntheticScanner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("performance")
class ParallelSpeedupPerformanceTest {

    private static final int THREADS = 4;
    private static final double MINIMUM_SPEEDUP = 1.5;
    private static final int WARM_UP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 5;
    private static final SolverSettings WARM_UP = new SolverSettings(5, 0.0);
    private static final SolverSettings MEASURED = new SolverSettings(20, 0.0);

    private static TikhonovProblem problem;

    private final Reconstructor reconstructor = new Reconstructor();

    @BeforeAll
    static void buildProblem() {
        SyntheticScan scan = SyntheticScanner.parallelBeam(160, 180);
        problem = TikhonovProblem.of(scan.systemMatrix(), scan.measurements(), 10.0);
    }

    @ParameterizedTest
    @EnumSource(value = ExecutionMode.class, names = {"STATIC_BLOCKS", "FORK_JOIN"})
    void parallelExecutionIsMeasurablyFasterThanSequential(ExecutionMode mode, TestReporter reporter) {
        int cores = Runtime.getRuntime().availableProcessors();
        assumeTrue(cores >= THREADS, "needs at least " + THREADS + " cores, this machine has " + cores);
        warmUp(mode);

        long sequential = medianNanos(ExecutionMode.SEQUENTIAL, 1);
        long parallel = medianNanos(mode, THREADS);
        double speedup = (double) sequential / parallel;

        String summary = String.format(Locale.ROOT, "%s on %d threads: %.1f ms vs %.1f ms sequential, speedup %.2fx",
                mode, THREADS, parallel / 1e6, sequential / 1e6, speedup);
        reporter.publishEntry("speedup", summary);
        System.out.println(summary);
        assertThat(speedup).as(summary).isGreaterThanOrEqualTo(MINIMUM_SPEEDUP);
    }

    private void warmUp(ExecutionMode mode) {
        for (int round = 0; round < WARM_UP_ROUNDS; round++) {
            run(ExecutionMode.SEQUENTIAL, 1, WARM_UP);
            run(mode, THREADS, WARM_UP);
        }
    }

    private long medianNanos(ExecutionMode mode, int threads) {
        long[] samples = new long[MEASURED_ROUNDS];
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long start = System.nanoTime();
            run(mode, threads, MEASURED);
            samples[round] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        return samples[MEASURED_ROUNDS / 2];
    }

    private void run(ExecutionMode mode, int threads, SolverSettings settings) {
        reconstructor.reconstruct(new ReconstructionRequest(problem, settings, mode, threads),
                IterationListener.NONE, CancellationToken.NONE);
    }
}
