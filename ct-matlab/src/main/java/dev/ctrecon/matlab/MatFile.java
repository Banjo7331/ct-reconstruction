package dev.ctrecon.matlab;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MatFile {

    private final Map<String, MatVariable> variables;

    MatFile(Map<String, MatVariable> variables) {
        this.variables = Map.copyOf(variables);
    }

    public Set<String> names() {
        return variables.keySet();
    }

    public Optional<MatNumericArray> numeric(String name) {
        return variables.get(name) instanceof MatNumericArray array ? Optional.of(array) : Optional.empty();
    }
}
