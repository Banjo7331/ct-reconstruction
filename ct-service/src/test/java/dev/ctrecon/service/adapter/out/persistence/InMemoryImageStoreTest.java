package dev.ctrecon.service.adapter.out.persistence;

import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.ReconstructionId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryImageStoreTest {

    private final InMemoryImageStore store = new InMemoryImageStore(2);
    private final ImageData image = new ImageData(1, 1, new double[]{1.0});

    @Test
    void evictsTheLeastRecentlyUsedImageWhenFull() {
        ReconstructionId first = ReconstructionId.random();
        ReconstructionId second = ReconstructionId.random();
        ReconstructionId third = ReconstructionId.random();
        store.store(first, image);
        store.store(second, image);

        store.find(first);
        store.store(third, image);

        assertThat(store.find(first)).isPresent();
        assertThat(store.find(second)).isEmpty();
        assertThat(store.find(third)).isPresent();
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new InMemoryImageStore(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
