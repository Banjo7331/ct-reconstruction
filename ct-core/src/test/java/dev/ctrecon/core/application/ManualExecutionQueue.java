package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.out.ExecutionQueue;
import dev.ctrecon.core.application.port.out.QueueFullException;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.ArrayDeque;
import java.util.Deque;

final class ManualExecutionQueue implements ExecutionQueue {

    private final Deque<Runnable> pending = new ArrayDeque<>();
    private final int capacity;

    ManualExecutionQueue(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void enqueue(ReconstructionId id, Runnable work) {
        if (pending.size() >= capacity) {
            throw new QueueFullException("Queue holds " + capacity + " reconstructions", null);
        }
        pending.add(work);
    }

    int size() {
        return pending.size();
    }

    void runNext() {
        pending.removeFirst().run();
    }
}
