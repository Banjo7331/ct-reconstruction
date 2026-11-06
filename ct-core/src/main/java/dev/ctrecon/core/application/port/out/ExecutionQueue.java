package dev.ctrecon.core.application.port.out;

import dev.ctrecon.core.domain.ReconstructionId;

public interface ExecutionQueue {

    void enqueue(ReconstructionId id, Runnable work);
}
