package metopa.publication.web;

import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.catalog.Genre;
import metopa.publication.PublicationService;
import metopa.publication.PublishedWorkNotFoundException;
import metopa.publication.PublishedWorkView;
import metopa.publication.InstallmentType;
import metopa.publication.PublishedInstallmentSummary;
import metopa.publication.PublishedWorkSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
                        Set.of(
                                Genre.SCI_FI,
                                Genre.ADVENTURE
                        ),
                        true,
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
                        jsonPath("$.genres")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.genres")
                                .value(
                                        containsInAnyOrder(
                                                "SCI_FI",
                                                "ADVENTURE"
                                        )
                                )
                )
                .andExpect(
                        jsonPath("$.coverUrl")
                                .value(
                                        "/api/works/"
                                                + workId
                                                + "/cover"
                                )
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

    @Test
    void shouldReturnPublishedWorks()
            throws Exception {

        UUID firstWorkId = UUID.randomUUID();
        UUID secondWorkId = UUID.randomUUID();

        when(publicationService.findPublishedWorks())
                .thenReturn(
                        List.of(
                                new PublishedWorkSummary(
                                        firstWorkId,
                                        "Metopa Origins",
                                        WorkType.GRAPHIC_NOVEL,
                                        Set.of(
                                                Genre.SCI_FI,
                                                Genre.ADVENTURE
                                        ),
                                        true
                                ),
                                new PublishedWorkSummary(
                                        secondWorkId,
                                        "Another Work",
                                        WorkType.COMIC,
                                        Set.of(
                                                Genre.DRAMA
                                        ),
                                        false
                                )
                        )
                );

        mockMvc.perform(
                        get("/api/works")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(firstWorkId.toString())
                )
                .andExpect(
                        jsonPath("$[0].title")
                                .value("Metopa Origins")
                )
                .andExpect(
                        jsonPath("$[0].type")
                                .value("GRAPHIC_NOVEL")
                )
                .andExpect(
                        jsonPath("$[0].genres")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$[0].genres")
                                .value(
                                        containsInAnyOrder(
                                                "SCI_FI",
                                                "ADVENTURE"
                                        )
                                )
                )
                .andExpect(
                        jsonPath("$[0].coverUrl")
                                .value(
                                        "/api/works/"
                                                + firstWorkId
                                                + "/cover"
                                )
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(secondWorkId.toString())
                )
                .andExpect(
                        jsonPath("$[1].title")
                                .value("Another Work")
                )
                .andExpect(
                        jsonPath("$[1].type")
                                .value("COMIC")
                )
                .andExpect(
                        jsonPath("$[1].genres")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$[1].genres")
                                .value(
                                        containsInAnyOrder(
                                                "DRAMA"
                                        )
                                )
                )
                .andExpect(
                        jsonPath("$[1].coverUrl")
                                .value(nullValue())
                );
    }

    @Test
    void shouldReturnPublishedWorksFilteredByGenre()
            throws Exception {

        UUID workId = UUID.randomUUID();

        when(
                publicationService.findPublishedWorks(
                        Genre.SCI_FI
                )
        ).thenReturn(
                List.of(
                        new PublishedWorkSummary(
                                workId,
                                "Metopa Origins",
                                WorkType.GRAPHIC_NOVEL,
                                Set.of(
                                        Genre.SCI_FI,
                                        Genre.ADVENTURE
                                ),
                                true
                        )
                )
        );

        mockMvc.perform(
                        get("/api/works")
                                .queryParam(
                                        "genre",
                                        "SCI_FI"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(
                                        workId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$[0].title")
                                .value(
                                        "Metopa Origins"
                                )
                )
                .andExpect(
                        jsonPath("$[0].type")
                                .value(
                                        "GRAPHIC_NOVEL"
                                )
                )
                .andExpect(
                        jsonPath("$[0].genres")
                                .value(
                                        containsInAnyOrder(
                                                "SCI_FI",
                                                "ADVENTURE"
                                        )
                                )
                );
    }

    @Test
    void shouldRejectInvalidGenreFilter()
            throws Exception {

        mockMvc.perform(
                        get("/api/works")
                                .queryParam(
                                        "genre",
                                        "INVALID"
                                )
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }
}