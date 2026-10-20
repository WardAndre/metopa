package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreatorPageContent;
import metopa.publication.PublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorPageController.class)
@AutoConfigureMockMvc(addFilters = false)
class CreatorPageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldReturnCreatorPageContent()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        byte[] pageContent =
                "image-content".getBytes();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        when(
                publicationService
                        .findCreatorPageContent(
                                userId,
                                pageId
                        )
        ).thenReturn(
                new CreatorPageContent(
                        "image/jpeg",
                        pageContent
                )
        );

        mockMvc.perform(
                        get(
                                "/api/me/pages/{pageId}/content",
                                pageId
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
                                MediaType.IMAGE_JPEG
                        )
                )
                .andExpect(
                        content().bytes(pageContent)
                );

        verify(identityService)
                .findByUsername("andre");

        verify(publicationService)
                .findCreatorPageContent(
                        userId,
                        pageId
                );
    }
}