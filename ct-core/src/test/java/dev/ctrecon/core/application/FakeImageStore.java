package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.out.ImageStore;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class FakeImageStore implements ImageStore {

    private final Map<ReconstructionId, ImageData> images = new ConcurrentHashMap<>();

    @Override
    public void store(ReconstructionId id, ImageData image) {
        images.put(id, image);
    }

    @Override
    public Optional<ImageData> find(ReconstructionId id) {
        return Optional.ofNullable(images.get(id));
    }

    void evict(ReconstructionId id) {
        images.remove(id);
    }
}
