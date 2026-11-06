package dev.ctrecon.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Reconstruction {

    private final ReconstructionId id;
    private final ReconstructionParameters parameters;
    private final ReconstructionStatus status;
    private final List<ConvergencePoint> history;
    private final Instant submittedAt;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final Termination termination;
    private final String failureReason;
    private final boolean cancellationRequested;

    private Reconstruction(ReconstructionId id, ReconstructionParameters parameters, ReconstructionStatus status,
                           List<ConvergencePoint> history, Instant submittedAt, Instant startedAt,
                           Instant finishedAt, Termination termination, String failureReason,
                           boolean cancellationRequested) {
        this.id = id;
        this.parameters = parameters;
        this.status = status;
        this.history = List.copyOf(history);
        this.submittedAt = submittedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.termination = termination;
        this.failureReason = failureReason;
        this.cancellationRequested = cancellationRequested;
    }

    public static Reconstruction submit(ReconstructionId id, ReconstructionParameters parameters, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(now, "now");
        return new Reconstruction(id, parameters, ReconstructionStatus.QUEUED, List.of(), now,
                null, null, null, null, false);
    }

    public Reconstruction start(Instant now) {
        requireStatus(ReconstructionStatus.QUEUED, "start");
        return new Reconstruction(id, parameters, ReconstructionStatus.RUNNING, List.of(), submittedAt,
                now, null, null, null, false);
    }

    public Reconstruction recordIteration(ConvergencePoint point) {
        requireStatus(ReconstructionStatus.RUNNING, "record an iteration of");
        if (point.iteration() > parameters.iterations()) {
            throw new IllegalArgumentException("Iteration " + point.iteration() + " exceeds the limit of "
                    + parameters.iterations());
        }
        if (point.iteration() <= completedIterations()) {
            return this;
        }
        List<ConvergencePoint> extended = new ArrayList<>(history);
        extended.add(point);
        return new Reconstruction(id, parameters, status, extended, submittedAt, startedAt, null, null, null,
                cancellationRequested);
    }

    public Reconstruction complete(Termination reason, Instant now) {
        requireStatus(ReconstructionStatus.RUNNING, "complete");
        Objects.requireNonNull(reason, "reason");
        return new Reconstruction(id, parameters, ReconstructionStatus.COMPLETED, history, submittedAt,
                startedAt, now, reason, null, cancellationRequested);
    }

    public Reconstruction fail(String reason, Instant now) {
        if (status.isTerminal()) {
            throw new IllegalReconstructionStateException(id, status, "fail");
        }
        return new Reconstruction(id, parameters, ReconstructionStatus.FAILED, history, submittedAt,
                startedAt, now, null, reason, cancellationRequested);
    }

    public Reconstruction cancel(Instant now) {
        return switch (status) {
            case QUEUED -> new Reconstruction(id, parameters, ReconstructionStatus.CANCELLED, history,
                    submittedAt, null, now, null, null, true);
            case RUNNING -> cancellationRequested ? this : new Reconstruction(id, parameters, status, history,
                    submittedAt, startedAt, null, null, null, true);
            case COMPLETED, FAILED, CANCELLED -> throw new IllegalReconstructionStateException(id, status, "cancel");
        };
    }

    public Reconstruction markCancelled(Instant now) {
        requireStatus(ReconstructionStatus.RUNNING, "mark as cancelled");
        return new Reconstruction(id, parameters, ReconstructionStatus.CANCELLED, history, submittedAt,
                startedAt, now, null, null, true);
    }

    public ReconstructionId id() {
        return id;
    }

    public ReconstructionParameters parameters() {
        return parameters;
    }

    public ReconstructionStatus status() {
        return status;
    }

    public List<ConvergencePoint> history() {
        return history;
    }

    public int completedIterations() {
        return history.isEmpty() ? 0 : history.getLast().iteration();
    }

    public Optional<ConvergencePoint> latest() {
        return history.isEmpty() ? Optional.empty() : Optional.of(history.getLast());
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public Optional<Duration> duration() {
        if (startedAt == null || finishedAt == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(startedAt, finishedAt));
    }

    public Optional<Termination> termination() {
        return Optional.ofNullable(termination);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    private void requireStatus(ReconstructionStatus required, String action) {
        if (status != required) {
            throw new IllegalReconstructionStateException(id, status, action);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Reconstruction reconstruction && id.equals(reconstruction.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Reconstruction[" + id + ", " + status + ", " + completedIterations() + "/"
                + parameters.iterations() + "]";
    }
}
