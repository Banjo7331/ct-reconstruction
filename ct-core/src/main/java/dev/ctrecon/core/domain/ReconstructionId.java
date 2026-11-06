package dev.ctrecon.core.domain;

import java.util.Objects;
import java.util.UUID;

public record ReconstructionId(UUID value) {

    public ReconstructionId {
        Objects.requireNonNull(value, "value");
    }

    public static ReconstructionId random() {
        return new ReconstructionId(UUID.randomUUID());
    }

    public static ReconstructionId parse(String text) {
        try {
            return new ReconstructionId(UUID.fromString(text));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Not a valid reconstruction id: " + text, invalid);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
