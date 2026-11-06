package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.application.port.in.CancelReconstructionUseCase;
import dev.ctrecon.core.application.port.in.FindReconstructionsUseCase;
import dev.ctrecon.core.application.port.in.GetReconstructedImageUseCase;
import dev.ctrecon.core.application.port.in.SubmitReconstructionUseCase;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/reconstructions")
public class ReconstructionController {

    private final SubmitReconstructionUseCase submitReconstruction;
    private final CancelReconstructionUseCase cancelReconstruction;
    private final FindReconstructionsUseCase findReconstructions;
    private final GetReconstructedImageUseCase getImage;

    public ReconstructionController(SubmitReconstructionUseCase submitReconstruction,
                                    CancelReconstructionUseCase cancelReconstruction,
                                    FindReconstructionsUseCase findReconstructions,
                                    GetReconstructedImageUseCase getImage) {
        this.submitReconstruction = submitReconstruction;
        this.cancelReconstruction = cancelReconstruction;
        this.findReconstructions = findReconstructions;
        this.getImage = getImage;
    }

    @PostMapping
    public ResponseEntity<ReconstructionResponse> submit(@RequestBody CreateReconstructionRequest request) {
        Reconstruction reconstruction = submitReconstruction.submit(request.toParameters());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(reconstruction.id().toString())
                .toUri();
        return ResponseEntity.accepted().location(location).body(ReconstructionResponse.from(reconstruction));
    }

    @GetMapping
    public List<ReconstructionResponse> list() {
        return findReconstructions.list().stream().map(ReconstructionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ReconstructionResponse get(@PathVariable("id") String id) {
        return ReconstructionResponse.from(findReconstructions.get(ReconstructionId.parse(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReconstructionResponse> cancel(@PathVariable("id") String id) {
        return ResponseEntity.accepted()
                .body(ReconstructionResponse.from(cancelReconstruction.cancel(ReconstructionId.parse(id))));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable("id") String id,
                                        @RequestParam(name = "windowMin", required = false) Double windowMin,
                                        @RequestParam(name = "windowMax", required = false) Double windowMax) {
        return ImageResponses.png(getImage.image(ReconstructionId.parse(id)), windowMin, windowMax);
    }
}
