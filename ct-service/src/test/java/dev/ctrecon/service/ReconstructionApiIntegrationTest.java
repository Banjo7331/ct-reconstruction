package dev.ctrecon.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ct.data-directory=src/test/resources/data")
class ReconstructionApiIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void findsTheDatasetInTheConfiguredFolder() throws Exception {
        mvc.perform(get("/api/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("tiny/Data8.mat"))
                .andExpect(jsonPath("$[0].hasReference").value(true));
    }

    @Test
    void reconstructsADatasetFromSubmissionToPng() throws Exception {
        String location = mvc.perform(post("/api/reconstructions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetId": "tiny/Data8.mat", "iterations": 10, "regularization": 0.01,
                                 "mode": "STATIC_BLOCKS", "threads": 2}
                                """))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).isNotNull();
        awaitStatus(location, "COMPLETED");

        mvc.perform(get(location))
                .andExpect(jsonPath("$.convergence").isNotEmpty())
                .andExpect(jsonPath("$.termination").isString());

        byte[] png = mvc.perform(get(location + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(png).startsWith(0x89, 'P', 'N', 'G');
    }

    @Test
    void servesTheViewerTheHealthEndpointAndDatasetPreviews() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/datasets/sinogram").param("id", "tiny/Data8.mat")).andExpect(status().isOk());
        mvc.perform(get("/api/datasets/reference").param("id", "tiny/Data8.mat")).andExpect(status().isOk());
    }

    private void awaitStatus(String location, String expected) throws Exception {
        Instant deadline = Instant.now().plus(TIMEOUT);
        String body = "";
        while (Instant.now().isBefore(deadline)) {
            body = mvc.perform(get(location)).andReturn().getResponse().getContentAsString();
            if (body.contains("\"status\":\"" + expected + "\"")) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Reconstruction did not reach " + expected + " within " + TIMEOUT
                + ", last state: " + body);
    }
}
