package metopa.publication.web;

import metopa.publication.PublicationService;
import metopa.publication.PublishedWorkCoverContent;
import metopa.publication.PublishedWorkCoverNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublishedWorkCoverController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublishedWorkCoverControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @Test
    void shouldReturnPublishedWorkCover()
            throws Exception {

        UUID workId = UUID.randomUUID();

        byte[] coverContent =
                "cover-content".getBytes();

        when(
                publicationService
                        .findPublishedWorkCover(
                                workId
                        )
        ).thenReturn(
                new PublishedWorkCoverContent(
                        "image/webp",
                        coverContent
                )
        );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}/cover",
                                workId
                        )
                )
                .andExpect(status().isOk())
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
    }

    @Test
    void shouldReturnNotFoundWhenPublishedWorkCoverIsNotAvailable()
            throws Exception {

        UUID workId = UUID.randomUUID();

        when(
                publicationService
                        .findPublishedWorkCover(
                                workId
                        )
        ).thenThrow(
                new PublishedWorkCoverNotFoundException(
                        workId
                )
        );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}/cover",
                                workId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/problem+json"
                        )
                );
    }
}