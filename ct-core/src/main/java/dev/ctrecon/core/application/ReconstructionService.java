package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.in.CancelReconstructionUseCase;
import dev.ctrecon.core.application.port.in.FindReconstructionsUseCase;
import dev.ctrecon.core.application.port.in.GetReconstructedImageUseCase;
import dev.ctrecon.core.application.port.in.SubmitReconstructionUseCase;
import dev.ctrecon.core.application.port.out.DatasetCatalog;
import dev.ctrecon.core.application.port.out.ExecutionQueue;
import dev.ctrecon.core.application.port.out.ImageStore;
import dev.ctrecon.core.application.port.out.QueueFullException;
import dev.ctrecon.core.application.port.out.ReconstructionEngine;
import dev.ctrecon.core.application.port.out.ReconstructionRepository;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;
import dev.ctrecon.core.domain.ReconstructionParameters;
import dev.ctrecon.core.domain.ReconstructionStatus;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class ReconstructionService implements SubmitReconstructionUseCase, CancelReconstructionUseCase,
        FindReconstructionsUseCase, GetReconstructedImageUseCase {

    private final ReconstructionRepository reconstructions;
    private final ImageStore images;
    private final DatasetCatalog catalog;
    private final ExecutionQueue queue;
    private final Clock clock;
    private final Supplier<ReconstructionId> identities;
    private final CancellationRequests cancellations = new CancellationRequests();
    private final ReconstructionProcessor processor;

    public ReconstructionService(ReconstructionRepository reconstructions, ImageStore images, DatasetCatalog catalog,
                                 ReconstructionEngine engine, ExecutionQueue queue, Clock clock,
                                 Supplier<ReconstructionId> identities) {
        this.reconstructions = Objects.requireNonNull(reconstructions, "reconstructions");
        this.images = Objects.requireNonNull(images, "images");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.processor = new ReconstructionProcessor(reconstructions, images,
                Objects.requireNonNull(engine, "engine"), cancellations, clock);
    }

    @Override
    public Reconstruction submit(ReconstructionParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        if (catalog.find(parameters.datasetId()).isEmpty()) {
            throw new DatasetNotFoundException(parameters.datasetId(), "Measurement data");
        }
        Reconstruction reconstruction = Reconstruction.submit(identities.get(), parameters, clock.instant());
        reconstructions.save(reconstruction);
        try {
            queue.enqueue(reconstruction.id(), () -> processor.process(reconstruction.id()));
        } catch (QueueFullException full) {
            reconstructions.delete(reconstruction.id());
            throw full;
        }
        return reconstruction;
    }

    @Override
    public Reconstruction cancel(ReconstructionId id) {
        Reconstruction reconstruction = reconstructions.update(id, current -> current.cancel(clock.instant()));
        if (reconstruction.status() == ReconstructionStatus.RUNNING) {
            cancellations.request(id);
        }
        return reconstruction;
    }

    @Override
    public Reconstruction get(ReconstructionId id) {
        return reconstructions.findById(id).orElseThrow(() -> new ReconstructionNotFoundException(id));
    }

    @Override
    public List<Reconstruction> list() {
        return reconstructions.findAll().stream()
                .sorted(Comparator.comparing(Reconstruction::submittedAt).reversed())
                .toList();
    }

    @Override
    public ImageData image(ReconstructionId id) {
        Reconstruction reconstruction = get(id);
        if (reconstruction.status() != ReconstructionStatus.COMPLETED) {
            throw new ImageNotAvailableException(id, "reconstruction is " + reconstruction.status());
        }
        return images.find(id).orElseThrow(() -> new ImageNotAvailableException(id, "image is no longer stored"));
    }

    public void cancelActiveReconstructions() {
        for (Reconstruction reconstruction : reconstructions.findAll()) {
            Reconstruction current = reconstructions.update(reconstruction.id(), candidate ->
                    candidate.status().isTerminal() ? candidate : candidate.cancel(clock.instant()));
            if (current.status() == ReconstructionStatus.RUNNING) {
                cancellations.request(current.id());
            }
        }
    }
}
