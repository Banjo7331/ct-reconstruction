package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.out.CancellationSignal;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class CancellationRequests {

    private final ConcurrentMap<ReconstructionId, AtomicBoolean> flags = new ConcurrentHashMap<>();

    CancellationSignal register(ReconstructionId id) {
        AtomicBoolean flag = flags.computeIfAbsent(id, ignored -> new AtomicBoolean());
        return flag::get;
    }

    void request(ReconstructionId id) {
        AtomicBoolean flag = flags.get(id);
        if (flag != null) {
            flag.set(true);
        }
    }

    void unregister(ReconstructionId id) {
        flags.remove(id);
    }

    int activeCount() {
        return flags.size();
    }
}
