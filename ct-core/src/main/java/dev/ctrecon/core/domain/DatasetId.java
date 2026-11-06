package dev.ctrecon.core.domain;

import java.util.regex.Pattern;

public record DatasetId(String value) {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*\\.mat");

    public DatasetId {
        if (value == null || !ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Dataset id must be a relative path of letters, digits, '.', '_' or '-' ending in .mat, got "
                            + value);
        }
        for (String segment : value.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Dataset id must not contain '.' or '..' segments: " + value);
            }
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
