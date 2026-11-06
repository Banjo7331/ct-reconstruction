package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.domain.ConvergencePoint;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record ReconstructionResponse(
        String id,
        String status,
        Parameters parameters,
        Progress progress,
        List<Point> convergence,
        String submittedAt,
        String startedAt,
        String finishedAt,
        Long durationMillis,
        String termination,
        String failureReason,
        boolean cancellationRequested) {

    public static ReconstructionResponse from(Reconstruction reconstruction) {
        ReconstructionParameters parameters = reconstruction.parameters();
        return new ReconstructionResponse(
                reconstruction.id().toString(),
                reconstruction.status().name(),
                new Parameters(parameters.datasetId().value(), parameters.iterations(), parameters.regularization(),
                        parameters.mode().name(), parameters.threads()),
                Progress.of(reconstruction.completedIterations(), parameters.iterations()),
                reconstruction.history().stream().map(Point::from).toList(),
                reconstruction.submittedAt().toString(),
                reconstruction.startedAt().map(Instant::toString).orElse(null),
                reconstruction.finishedAt().map(Instant::toString).orElse(null),
                reconstruction.duration().map(Duration::toMillis).orElse(null),
                reconstruction.termination().map(Enum::name).orElse(null),
                reconstruction.failureReason().orElse(null),
                reconstruction.isCancellationRequested());
    }

    public record Parameters(String datasetId, int iterations, double regularization, String mode, int threads) {
    }

    public record Progress(int completedIterations, int totalIterations, int percent) {

        static Progress of(int completed, int total) {
            return new Progress(completed, total, (int) (100L * completed / total));
        }
    }

    public record Point(int iteration, double relativeResidual, double relativeMisfit) {

        static Point from(ConvergencePoint point) {
            return new Point(point.iteration(), point.relativeResidual(), point.relativeMisfit());
        }
    }
}
