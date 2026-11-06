package dev.ctrecon.core.application.port.out;

import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface ReconstructionRepository {

    void save(Reconstruction reconstruction);

    Optional<Reconstruction> findById(ReconstructionId id);

    List<Reconstruction> findAll();

    Reconstruction update(ReconstructionId id, UnaryOperator<Reconstruction> change);

    void delete(ReconstructionId id);
}
