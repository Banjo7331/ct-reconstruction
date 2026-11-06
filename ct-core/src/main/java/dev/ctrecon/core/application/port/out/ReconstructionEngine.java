package dev.ctrecon.core.application.port.out;

import dev.ctrecon.core.domain.ReconstructionParameters;

public interface ReconstructionEngine {

    ReconstructionOutcome reconstruct(ReconstructionParameters parameters,
                                      IterationProgress progress,
                                      CancellationSignal cancellation);
}
