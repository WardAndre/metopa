package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.catalog.WorkCoverContent;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorWorkCoverController.class)
@AutoConfigureMockMvc(addFilters = false)
class CreatorWorkCoverControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldReturnCreatorWorkCoverForAuthenticatedOwner()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        byte[] coverContent =
                "cover-content".getBytes();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        when(
                catalogService.findCreatorWorkCover(
                        userId,
                        workId
                )
        ).thenReturn(
                new WorkCoverContent(
                        "image/webp",
                        coverContent
                )
        );

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}/cover",
                                workId
                        )
                                .principal(
                                        () -> "andre"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        content().contentType(
                                "image/webp"
                        )
                )
                .andExpect(
                        content().bytes(
                                coverContent
                        )
                );

        verify(identityService)
                .findByUsername("andre");

        verify(catalogService)
                .findCreatorWorkCover(
                        userId,
                        workId
                );
    }
}