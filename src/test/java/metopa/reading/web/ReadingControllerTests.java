package metopa.reading.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.reading.ReadingProgressView;
import metopa.reading.ReadingService;
import metopa.publication.PublishedPageNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReadingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadingService readingService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldSaveAuthenticatedUsersReadingProgress()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        mockMvc.perform(
                        put(
                                "/api/reading/progress/pages/{pageId}",
                                pageId
                        )
                                .principal(() -> "andre")
                )
                .andExpect(status().isNoContent());

        verify(readingService)
                .saveProgress(
                        userId,
                        pageId
                );
    }

    @Test
    void shouldReturnAuthenticatedUsersReadingProgress()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID progressId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        Instant updatedAt = Instant.now();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        when(
                readingService.findProgress(
                        userId,
                        workId
                )
        ).thenReturn(
                Optional.of(
                        new ReadingProgressView(
                                progressId,
                                workId,
                                installmentId,
                                pageId,
                                updatedAt
                        )
                )
        );

        mockMvc.perform(
                        get(
                                "/api/reading/progress/works/{workId}",
                                workId
                        )
                                .principal(() -> "andre")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(progressId.toString())
                )
                .andExpect(
                        jsonPath("$.workId")
                                .value(workId.toString())
                )
                .andExpect(
                        jsonPath("$.installmentId")
                                .value(installmentId.toString())
                )
                .andExpect(
                        jsonPath("$.pageId")
                                .value(pageId.toString())
                )
                .andExpect(
                        jsonPath("$.updatedAt")
                                .value(updatedAt.toString())
                );
    }

    @Test
    void shouldReturnNotFoundWhenReadingProgressDoesNotExist()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        when(
                readingService.findProgress(
                        userId,
                        workId
                )
        ).thenReturn(Optional.empty());

        mockMvc.perform(
                        get(
                                "/api/reading/progress/works/{workId}",
                                workId
                        )
                                .principal(() -> "andre")
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenPageIsNotAvailableForProgress()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        doThrow(
                new PublishedPageNotFoundException(pageId)
        )
                .when(readingService)
                .saveProgress(
                        userId,
                        pageId
                );

        mockMvc.perform(
                        put(
                                "/api/reading/progress/pages/{pageId}",
                                pageId
                        )
                                .principal(() -> "andre")
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value("Page not available")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The requested page is not available for reading progress."
                                )
                );
    }
}