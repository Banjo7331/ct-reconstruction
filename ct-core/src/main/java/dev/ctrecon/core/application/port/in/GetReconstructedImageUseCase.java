package dev.ctrecon.core.application.port.in;

import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.ReconstructionId;

public interface GetReconstructedImageUseCase {

    ImageData image(ReconstructionId id);
}
