package dev.ctrecon.core.application.port.in;

import dev.ctrecon.core.domain.Dataset;

import java.util.List;

public interface ListDatasetsUseCase {

    List<Dataset> datasets();
}
