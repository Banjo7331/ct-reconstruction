package dev.ctrecon.core.application.port.in;

import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;

public interface CancelReconstructionUseCase {

    Reconstruction cancel(ReconstructionId id);
}
