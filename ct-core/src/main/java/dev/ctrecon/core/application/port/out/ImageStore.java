package dev.ctrecon.core.application.port.out;

import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.Optional;

public interface ImageStore {

    void store(ReconstructionId id, ImageData image);

    Optional<ImageData> find(ReconstructionId id);
}
