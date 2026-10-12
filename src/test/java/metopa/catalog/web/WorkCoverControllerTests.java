package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.catalog.WorkAccessDeniedException;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkCoverController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkCoverControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldSetCoverForAuthenticatedWorkOwner()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        byte[] content =
                "cover-content".getBytes();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        content
                );

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        mockMvc.perform(
                        multipart(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .file(file)
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                                .principal(() -> "andre")
                )
                .andExpect(status().isNoContent());

        verify(identityService)
                .findByUsername("andre");

        verify(catalogService)
                .setCover(
                        userId,
                        workId,
                        "image/webp",
                        content
                );
    }

    @Test
    void shouldReturnForbiddenWhenUserDoesNotOwnWork()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        byte[] content =
                "cover-content".getBytes();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        content
                );

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        doThrow(
                new WorkAccessDeniedException()
        )
                .when(catalogService)
                .setCover(
                        userId,
                        workId,
                        "image/webp",
                        content
                );

        mockMvc.perform(
                        multipart(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .file(file)
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                                .principal(() -> "andre")
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.title")
                                .value("Work access denied")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The authenticated user cannot modify this work."
                                )
                );
    }
}