package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.out.ReconstructionRepository;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

final class FakeReconstructionRepository implements ReconstructionRepository {

    private final ConcurrentMap<ReconstructionId, Reconstruction> items = new ConcurrentHashMap<>();

    @Override
    public void save(Reconstruction reconstruction) {
        items.put(reconstruction.id(), reconstruction);
    }

    @Override
    public Optional<Reconstruction> findById(ReconstructionId id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public List<Reconstruction> findAll() {
        return List.copyOf(items.values());
    }

    @Override
    public Reconstruction update(ReconstructionId id, UnaryOperator<Reconstruction> change) {
        Reconstruction updated = items.computeIfPresent(id, (key, current) -> change.apply(current));
        if (updated == null) {
            throw new ReconstructionNotFoundException(id);
        }
        return updated;
    }

    @Override
    public void delete(ReconstructionId id) {
        items.remove(id);
    }
}
