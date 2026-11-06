package dev.ctrecon.core.application.port.in;

import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.List;

public interface FindReconstructionsUseCase {

    Reconstruction get(ReconstructionId id);

    List<Reconstruction> list();
}
