package dev.ctrecon.engine.solver;

import dev.ctrecon.engine.parallel.CancellationFlag;
import dev.ctrecon.engine.parallel.CancellationToken;
import dev.ctrecon.engine.parallel.ExecutionMode;
import dev.ctrecon.engine.parallel.OperationCancelledException;
import dev.ctrecon.engine.synthetic.SyntheticScan;
import dev.ctrecon.engine.synthetic.SyntheticScanner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconstructorTest {

    private static final SyntheticScan SCAN = SyntheticScanner.parallelBeam(24, 30);
    private static final TikhonovProblem PROBLEM = TikhonovProblem.of(SCAN.systemMatrix(), SCAN.measurements(), 10.0);

    private final Reconstructor reconstructor = new Reconstructor();

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void runsInEveryModeAndReleasesItsThreads(ExecutionMode mode) {
        SolverResult result = reconstructor.reconstruct(
                new ReconstructionRequest(PROBLEM, new SolverSettings(5, 0.0), mode, 2),
                IterationListener.NONE, CancellationToken.NONE);

        assertThat(result.iterations()).isEqualTo(5);
        assertThat(liveEngineThreads()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void releasesItsThreadsWhenCancelled(ExecutionMode mode) {
        CancellationFlag cancellation = new CancellationFlag();

        assertThatThrownBy(() -> reconstructor.reconstruct(
                new ReconstructionRequest(PROBLEM, new SolverSettings(50, 0.0), mode, 2),
                report -> cancellation.requestCancellation(), cancellation))
                .isInstanceOf(OperationCancelledException.class);

        assertThat(liveEngineThreads()).isEmpty();
    }

    private static List<String> liveEngineThreads() {
        List<Thread> engineThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("ct-block-") || thread.getName().startsWith("ct-fj-"))
                .toList();
        for (Thread thread : engineThreads) {
            try {
                thread.join(Duration.ofSeconds(5));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return engineThreads.stream().filter(Thread::isAlive).map(Thread::getName).toList();
    }
}
