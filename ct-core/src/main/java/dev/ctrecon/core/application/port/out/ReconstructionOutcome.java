package dev.ctrecon.core.application.port.out;

import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.Termination;

import java.util.Objects;

public record ReconstructionOutcome(ImageData image, Termination termination) {

    public ReconstructionOutcome {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(termination, "termination");
    }
}
