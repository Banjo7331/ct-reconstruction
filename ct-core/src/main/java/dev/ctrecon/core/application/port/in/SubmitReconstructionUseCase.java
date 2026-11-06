package dev.ctrecon.core.application.port.in;

import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionParameters;

public interface SubmitReconstructionUseCase {

    Reconstruction submit(ReconstructionParameters parameters);
}
