package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.application.port.in.ListDatasetsUseCase;
import dev.ctrecon.core.application.port.in.PreviewDatasetUseCase;
import dev.ctrecon.core.domain.DatasetId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final ListDatasetsUseCase listDatasets;
    private final PreviewDatasetUseCase previewDataset;

    public DatasetController(ListDatasetsUseCase listDatasets, PreviewDatasetUseCase previewDataset) {
        this.listDatasets = listDatasets;
        this.previewDataset = previewDataset;
    }

    @GetMapping
    public List<DatasetResponse> list() {
        return listDatasets.datasets().stream().map(DatasetResponse::from).toList();
    }

    @GetMapping("/sinogram")
    public ResponseEntity<byte[]> sinogram(@RequestParam("id") String id,
                                           @RequestParam(name = "windowMin", required = false) Double windowMin,
                                           @RequestParam(name = "windowMax", required = false) Double windowMax) {
        return ImageResponses.png(previewDataset.sinogram(new DatasetId(id)), windowMin, windowMax);
    }

    @GetMapping("/reference")
    public ResponseEntity<byte[]> reference(@RequestParam("id") String id,
                                            @RequestParam(name = "windowMin", required = false) Double windowMin,
                                            @RequestParam(name = "windowMax", required = false) Double windowMax) {
        return ImageResponses.png(previewDataset.reference(new DatasetId(id)), windowMin, windowMax);
    }
}
