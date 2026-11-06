package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.out.CancellationSignal;
import dev.ctrecon.core.application.port.out.ImageStore;
import dev.ctrecon.core.application.port.out.ReconstructionCancelledException;
import dev.ctrecon.core.application.port.out.ReconstructionEngine;
import dev.ctrecon.core.application.port.out.ReconstructionOutcome;
import dev.ctrecon.core.application.port.out.ReconstructionRepository;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;
import dev.ctrecon.core.domain.ReconstructionStatus;

import java.time.Clock;

final class ReconstructionProcessor {

    private final ReconstructionRepository reconstructions;
    private final ImageStore images;
    private final ReconstructionEngine engine;
    private final CancellationRequests cancellations;
    private final Clock clock;

    ReconstructionProcessor(ReconstructionRepository reconstructions, ImageStore images, ReconstructionEngine engine,
                            CancellationRequests cancellations, Clock clock) {
        this.reconstructions = reconstructions;
        this.images = images;
        this.engine = engine;
        this.cancellations = cancellations;
        this.clock = clock;
    }

    void process(ReconstructionId id) {
        CancellationSignal cancellation = cancellations.register(id);
        try {
            Reconstruction reconstruction = reconstructions.update(id, current ->
                    current.status() == ReconstructionStatus.QUEUED ? current.start(clock.instant()) : current);
            if (reconstruction.status() == ReconstructionStatus.RUNNING) {
                run(reconstruction, cancellation);
            }
        } finally {
            cancellations.unregister(id);
        }
    }

    private void run(Reconstruction reconstruction, CancellationSignal cancellation) {
        ReconstructionId id = reconstruction.id();
        try {
            ReconstructionOutcome outcome = engine.reconstruct(reconstruction.parameters(),
                    point -> reconstructions.update(id, current -> current.recordIteration(point)),
                    cancellation);
            images.store(id, outcome.image());
            reconstructions.update(id, current -> current.complete(outcome.termination(), clock.instant()));
        } catch (ReconstructionCancelledException cancelled) {
            reconstructions.update(id, current -> current.markCancelled(clock.instant()));
        } catch (RuntimeException failure) {
            reconstructions.update(id, current -> current.fail(describe(failure), clock.instant()));
        }
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
