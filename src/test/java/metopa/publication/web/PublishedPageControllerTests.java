package metopa.publication.web;

import metopa.publication.PublicationService;
import metopa.publication.PublishedPageContent;
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

@WebMvcTest(PublishedPageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublishedPageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @Test
    void shouldReturnPublishedPageContent()
            throws Exception {

        UUID pageId = UUID.randomUUID();

        byte[] pageContent =
                "image-content".getBytes();

        when(
                publicationService
                        .findPublishedPageContent(pageId)
        ).thenReturn(
                new PublishedPageContent(
                        "image/jpeg",
                        pageContent
                )
        );

        mockMvc.perform(
                        get(
                                "/api/pages/{pageId}/content",
                                pageId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentType(
                                MediaType.IMAGE_JPEG
                        )
                )
                .andExpect(
                        content().bytes(pageContent)
                );

        verify(publicationService)
                .findPublishedPageContent(pageId);
    }
}