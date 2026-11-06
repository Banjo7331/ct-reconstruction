package dev.ctrecon.service.adapter.out.persistence;

import dev.ctrecon.core.application.port.out.ImageStore;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryImageStore implements ImageStore {

    private final Map<ReconstructionId, ImageData> images;

    public InMemoryImageStore(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        this.images = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ReconstructionId, ImageData> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public synchronized void store(ReconstructionId id, ImageData image) {
        images.put(id, image);
    }

    @Override
    public synchronized Optional<ImageData> find(ReconstructionId id) {
        return Optional.ofNullable(images.get(id));
    }
}
