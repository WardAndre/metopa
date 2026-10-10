package metopa.publication.web;

import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.publication.PublicationService;
import metopa.publication.PublishedWorkNotFoundException;
import metopa.publication.PublishedWorkView;
import metopa.publication.InstallmentType;
import metopa.publication.PublishedInstallmentSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublishedWorkController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublishedWorkControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @Test
    void shouldReturnPublishedWork()
            throws Exception {

        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        when(
                publicationService.findPublishedWork(
                        workId
                )
        ).thenReturn(
                new PublishedWorkView(
                        workId,
                        "Metopa Origins",
                        "A science fiction graphic novel.",
                        WorkType.GRAPHIC_NOVEL,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE,
                        List.of(
                                new PublishedInstallmentSummary(
                                        installmentId,
                                        InstallmentType.ISSUE,
                                        1,
                                        "Issue One"
                                )
                        )
                )
        );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}",
                                workId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(workId.toString())
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Metopa Origins")
                )
                .andExpect(
                        jsonPath("$.description")
                                .value(
                                        "A science fiction graphic novel."
                                )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("GRAPHIC_NOVEL")
                )
                .andExpect(
                        jsonPath("$.readingDirection")
                                .value("LEFT_TO_RIGHT")
                )
                .andExpect(
                        jsonPath("$.presentationMode")
                                .value("SINGLE_PAGE")
                )
                .andExpect(
                        jsonPath("$.installments.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.installments[0].id")
                                .value(
                                        installmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.installments[0].type")
                                .value("ISSUE")
                )
                .andExpect(
                        jsonPath("$.installments[0].number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.installments[0].title")
                                .value("Issue One")
                );
    }

    @Test
    void shouldReturnNotFoundWhenWorkIsNotAvailable()
            throws Exception {

        UUID workId = UUID.randomUUID();

        when(
                publicationService.findPublishedWork(
                        workId
                )
        ).thenThrow(
                new PublishedWorkNotFoundException(
                        workId
                )
        );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}",
                                workId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value("Work not available")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The requested work is not available."
                                )
                );
    }
}