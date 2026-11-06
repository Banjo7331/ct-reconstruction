package dev.ctrecon.service.adapter.out.persistence;

import dev.ctrecon.core.application.ReconstructionNotFoundException;
import dev.ctrecon.core.application.port.out.ReconstructionRepository;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

public final class InMemoryReconstructionRepository implements ReconstructionRepository {

    private final ConcurrentMap<ReconstructionId, Reconstruction> reconstructions = new ConcurrentHashMap<>();

    @Override
    public void save(Reconstruction reconstruction) {
        reconstructions.put(reconstruction.id(), reconstruction);
    }

    @Override
    public Optional<Reconstruction> findById(ReconstructionId id) {
        return Optional.ofNullable(reconstructions.get(id));
    }

    @Override
    public List<Reconstruction> findAll() {
        return List.copyOf(reconstructions.values());
    }

    @Override
    public Reconstruction update(ReconstructionId id, UnaryOperator<Reconstruction> change) {
        Reconstruction updated = reconstructions.computeIfPresent(id, (key, current) -> change.apply(current));
        if (updated == null) {
            throw new ReconstructionNotFoundException(id);
        }
        return updated;
    }

    @Override
    public void delete(ReconstructionId id) {
        reconstructions.remove(id);
    }
}
