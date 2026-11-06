package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.application.DatasetNotFoundException;
import dev.ctrecon.core.application.ImageNotAvailableException;
import dev.ctrecon.core.application.ReconstructionNotFoundException;
import dev.ctrecon.core.application.port.in.CancelReconstructionUseCase;
import dev.ctrecon.core.application.port.in.FindReconstructionsUseCase;
import dev.ctrecon.core.application.port.in.GetReconstructedImageUseCase;
import dev.ctrecon.core.application.port.in.SubmitReconstructionUseCase;
import dev.ctrecon.core.application.port.out.QueueFullException;
import dev.ctrecon.core.domain.ConvergencePoint;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ExecutionMode;
import dev.ctrecon.core.domain.IllegalReconstructionStateException;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;
import dev.ctrecon.core.domain.ReconstructionParameters;
import dev.ctrecon.core.domain.ReconstructionStatus;
import dev.ctrecon.core.domain.Termination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReconstructionControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final DatasetId WALNUT = new DatasetId("walnut/Data164.mat");
    private static final ReconstructionParameters PARAMETERS =
            new ReconstructionParameters(WALNUT, 3, 10.0, ExecutionMode.FORK_JOIN, 2);

    private final SubmitReconstructionUseCase submit = mock(SubmitReconstructionUseCase.class);
    private final CancelReconstructionUseCase cancel = mock(CancelReconstructionUseCase.class);
    private final FindReconstructionsUseCase find = mock(FindReconstructionsUseCase.class);
    private final GetReconstructedImageUseCase image = mock(GetReconstructedImageUseCase.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new ReconstructionController(submit, cancel, find, image))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void acceptsAReconstructionAndPointsToItsStatus() throws Exception {
        Reconstruction reconstruction = Reconstruction.submit(ReconstructionId.random(), PARAMETERS, NOW);
        when(submit.submit(any())).thenReturn(reconstruction);

        mvc.perform(post("/api/reconstructions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetId": "walnut/Data164.mat", "iterations": 3, "regularization": 10,
                                 "mode": "fork_join", "threads": 2}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/api/reconstructions/" + reconstruction.id())))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.parameters.datasetId").value("walnut/Data164.mat"))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-07T12:00:00Z"));

        verify(submit).submit(eq(PARAMETERS));
    }

    @Test
    void reportsEveryInvalidParameter() throws Exception {
        mvc.perform(post("/api/reconstructions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iterations": 0, "threads": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid reconstruction parameters"))
                .andExpect(jsonPath("$.violations.length()").value(3));

        verifyNoInteractions(submit);
    }

    @Test
    void refusesADatasetIdThatTriesToLeaveTheDataFolder() throws Exception {
        mvc.perform(post("/api/reconstructions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetId": "../../etc/passwd.mat"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submit);
    }

    @Test
    void answersNotFoundForAnUnknownDataset() throws Exception {
        when(submit.submit(any())).thenThrow(new DatasetNotFoundException(WALNUT, "Measurement data"));

        mvc.perform(post("/api/reconstructions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetId": "walnut/Data164.mat"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersServiceUnavailableWithRetryAfterWhenTheQueueIsFull() throws Exception {
        when(submit.submit(any())).thenThrow(new QueueFullException("1 reconstruction(s) are running", null));

        mvc.perform(post("/api/reconstructions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetId": "walnut/Data164.mat"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", ApiExceptionHandler.RETRY_AFTER_SECONDS));
    }

    @Test
    void returnsProgressAndTheConvergenceHistory() throws Exception {
        Reconstruction running = Reconstruction.submit(ReconstructionId.random(), PARAMETERS, NOW).start(NOW)
                .recordIteration(new ConvergencePoint(1, 0.5, 0.2))
                .recordIteration(new ConvergencePoint(2, 0.25, 0.1));
        when(find.get(running.id())).thenReturn(running);

        mvc.perform(get("/api/reconstructions/{id}", running.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.progress.percent").value(66))
                .andExpect(jsonPath("$.convergence.length()").value(2))
                .andExpect(jsonPath("$.convergence[1].relativeResidual").value(0.25));
    }

    @Test
    void listsReconstructions() throws Exception {
        when(find.list()).thenReturn(List.of(Reconstruction.submit(ReconstructionId.random(), PARAMETERS, NOW)));

        mvc.perform(get("/api/reconstructions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void answersNotFoundForAnUnknownReconstructionAndBadRequestForAMalformedId() throws Exception {
        ReconstructionId id = ReconstructionId.random();
        when(find.get(id)).thenThrow(new ReconstructionNotFoundException(id));

        mvc.perform(get("/api/reconstructions/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/reconstructions/{id}", "123")).andExpect(status().isBadRequest());
    }

    @Test
    void answersConflictWhenCancellingAFinishedReconstruction() throws Exception {
        ReconstructionId id = ReconstructionId.random();
        when(cancel.cancel(id)).thenThrow(
                new IllegalReconstructionStateException(id, ReconstructionStatus.COMPLETED, "cancel"));

        mvc.perform(post("/api/reconstructions/{id}/cancel", id)).andExpect(status().isConflict());
    }

    @Test
    void reportsTheResultOfACompletedReconstruction() throws Exception {
        Reconstruction completed = Reconstruction.submit(ReconstructionId.random(), PARAMETERS, NOW).start(NOW)
                .complete(Termination.CONVERGED, NOW.plusSeconds(2));
        when(find.get(completed.id())).thenReturn(completed);

        mvc.perform(get("/api/reconstructions/{id}", completed.id()))
                .andExpect(jsonPath("$.termination").value("CONVERGED"))
                .andExpect(jsonPath("$.durationMillis").value(2000));
    }

    @Test
    void servesTheImageAsPng() throws Exception {
        ReconstructionId id = ReconstructionId.random();
        when(image.image(id)).thenReturn(new ImageData(2, 2, new double[]{0, 0.1, 0.2, 0.3}));

        byte[] body = mvc.perform(get("/api/reconstructions/{id}/image", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).startsWith(0x89, 'P', 'N', 'G');
    }

    @Test
    void answersConflictForTheImageOfAnUnfinishedReconstruction() throws Exception {
        ReconstructionId id = ReconstructionId.random();
        when(image.image(id)).thenThrow(new ImageNotAvailableException(id, "reconstruction is RUNNING"));

        mvc.perform(get("/api/reconstructions/{id}/image", id)).andExpect(status().isConflict());
    }

    @Test
    void rejectsAnInvertedDisplayWindow() throws Exception {
        ReconstructionId id = ReconstructionId.random();
        when(image.image(id)).thenReturn(new ImageData(1, 1, new double[]{0}));

        mvc.perform(get("/api/reconstructions/{id}/image", id)
                        .param("windowMin", "0.6")
                        .param("windowMax", "0.2"))
                .andExpect(status().isBadRequest());
    }
}
