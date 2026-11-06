package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.application.DatasetNotFoundException;
import dev.ctrecon.core.application.port.in.ListDatasetsUseCase;
import dev.ctrecon.core.application.port.in.PreviewDatasetUseCase;
import dev.ctrecon.core.domain.Dataset;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatasetControllerTest {

    private static final DatasetId WALNUT = new DatasetId("walnut/Data164.mat");

    private final ListDatasetsUseCase listDatasets = mock(ListDatasetsUseCase.class);
    private final PreviewDatasetUseCase previewDataset = mock(PreviewDatasetUseCase.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new DatasetController(listDatasets, previewDataset))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsTheAvailableDatasets() throws Exception {
        when(listDatasets.datasets()).thenReturn(List.of(new Dataset(WALNUT, 164, 164, 120, 33_200_000L, true)));

        mvc.perform(get("/api/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("walnut/Data164.mat"))
                .andExpect(jsonPath("$[0].imageSize").value(164))
                .andExpect(jsonPath("$[0].hasReference").value(true));
    }

    @Test
    void servesTheMeasuredSinogramForAnIdContainingASlash() throws Exception {
        when(previewDataset.sinogram(WALNUT)).thenReturn(new ImageData(2, 1, new double[]{0.0, 1.0}));

        mvc.perform(get("/api/datasets/sinogram").param("id", "walnut/Data164.mat"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void refusesAnIdThatTriesToLeaveTheDataFolder() throws Exception {
        mvc.perform(get("/api/datasets/sinogram").param("id", "../secret.mat"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(previewDataset);
    }

    @Test
    void answersNotFoundWhenThereIsNoReference() throws Exception {
        when(previewDataset.reference(WALNUT)).thenThrow(new DatasetNotFoundException(WALNUT, "Reference image"));

        mvc.perform(get("/api/datasets/reference").param("id", "walnut/Data164.mat"))
                .andExpect(status().isNotFound());
    }
}
