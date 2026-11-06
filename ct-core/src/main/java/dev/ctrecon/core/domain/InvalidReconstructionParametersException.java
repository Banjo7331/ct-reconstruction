package dev.ctrecon.core.domain;

import java.util.List;

public final class InvalidReconstructionParametersException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final List<String> violations;

    public InvalidReconstructionParametersException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
