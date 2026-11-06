package dev.ctrecon.core.domain;

public enum ReconstructionStatus {
    QUEUED(false),
    RUNNING(false),
    COMPLETED(true),
    FAILED(true),
    CANCELLED(true);

    private final boolean terminal;

    ReconstructionStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
