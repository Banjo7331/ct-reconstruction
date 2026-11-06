package dev.ctrecon.core.application;

import dev.ctrecon.core.domain.DatasetId;

public final class DatasetNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatasetNotFoundException(DatasetId id, String what) {
        super(what + " of dataset " + id + " is not available");
    }
}
