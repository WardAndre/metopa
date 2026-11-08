package metopa.publication.web;

import metopa.catalog.Genre;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.publication.InstallmentType;
import metopa.publication.PublicationService;
import metopa.publication.PublishedInstallmentSummary;
import metopa.publication.PublishedWorkNotFoundException;
import metopa.publication.PublishedWorkSummary;
import metopa.publication.PublishedWorkView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void shouldReturnPublishedWork() throws Exception {
        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        when(publicationService.findPublishedWork(workId))
                .thenReturn(
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
                        get("/api/works/{workId}", workId)
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
                                .value("A science fiction graphic novel.")
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
                                .value(installmentId.toString())
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

        when(publicationService.findPublishedWork(workId))
                .thenThrow(
                        new PublishedWorkNotFoundException(workId)
                );

        mockMvc.perform(
                        get("/api/works/{workId}", workId)
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
    void shouldReturnPublishedWorks() throws Exception {
        UUID firstWorkId = UUID.randomUUID();
        UUID secondWorkId = UUID.randomUUID();

        stubPublishedWorks(
                null,
                0,
                20,
                2,
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
                        Set.of(Genre.DRAMA),
                        false
                )
        );

        mockMvc.perform(
                        get("/api/works")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(firstWorkId.toString())
                )
                .andExpect(
                        jsonPath("$.content[0].title")
                                .value("Metopa Origins")
                )
                .andExpect(
                        jsonPath("$.content[0].type")
                                .value("GRAPHIC_NOVEL")
                )
                .andExpect(
                        jsonPath("$.content[0].genres")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.content[0].genres")
                                .value(
                                        containsInAnyOrder(
                                                "SCI_FI",
                                                "ADVENTURE"
                                        )
                                )
                )
                .andExpect(
                        jsonPath("$.content[0].coverUrl")
                                .value(
                                        "/api/works/"
                                                + firstWorkId
                                                + "/cover"
                                )
                )
                .andExpect(
                        jsonPath("$.content[1].id")
                                .value(secondWorkId.toString())
                )
                .andExpect(
                        jsonPath("$.content[1].title")
                                .value("Another Work")
                )
                .andExpect(
                        jsonPath("$.content[1].type")
                                .value("COMIC")
                )
                .andExpect(
                        jsonPath("$.content[1].genres")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.content[1].genres")
                                .value(
                                        containsInAnyOrder("DRAMA")
                                )
                )
                .andExpect(
                        jsonPath("$.content[1].coverUrl")
                                .value(nullValue())
                )
                .andExpect(
                        jsonPath("$.page")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(20)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(1)
                );

        verify(publicationService)
                .findPublishedWorks(
                        null,
                        null,
                        PageRequest.of(0, 20)
                );
    }

    @Test
    void shouldReturnPublishedWorksFilteredByGenre()
            throws Exception {

        UUID workId = UUID.randomUUID();

        stubPublishedWorks(
                Genre.SCI_FI,
                0,
                20,
                1,
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
        );

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("genre", "SCI_FI")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(workId.toString())
                )
                .andExpect(
                        jsonPath("$.content[0].title")
                                .value("Metopa Origins")
                )
                .andExpect(
                        jsonPath("$.content[0].type")
                                .value("GRAPHIC_NOVEL")
                )
                .andExpect(
                        jsonPath("$.content[0].genres")
                                .value(
                                        containsInAnyOrder(
                                                "SCI_FI",
                                                "ADVENTURE"
                                        )
                                )
                )
                .andExpect(
                        jsonPath("$.page")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(20)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(1)
                );

        verify(publicationService)
                .findPublishedWorks(
                        null,
                        Genre.SCI_FI,
                        PageRequest.of(0, 20)
                );
    }

    @Test
    void shouldRejectInvalidGenreFilter() throws Exception {
        mockMvc.perform(
                        get("/api/works")
                                .queryParam("genre", "INVALID")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }

    @Test
    void shouldReturnFirstPageOfPublishedWorks()
            throws Exception {

        UUID firstWorkId = UUID.randomUUID();

        stubPublishedWorks(
                null,
                0,
                1,
                2,
                new PublishedWorkSummary(
                        firstWorkId,
                        "Metopa Origins",
                        WorkType.GRAPHIC_NOVEL,
                        Set.of(Genre.SCI_FI),
                        true
                )
        );

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(firstWorkId.toString())
                )
                .andExpect(
                        jsonPath("$.content[0].title")
                                .value("Metopa Origins")
                )
                .andExpect(
                        jsonPath("$.page")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(2)
                );

        verify(publicationService)
                .findPublishedWorks(
                        null,
                        null,
                        PageRequest.of(0, 1)
                );
    }

    @Test
    void shouldReturnSecondPageOfPublishedWorks()
            throws Exception {

        UUID secondWorkId = UUID.randomUUID();

        stubPublishedWorks(
                null,
                1,
                1,
                2,
                new PublishedWorkSummary(
                        secondWorkId,
                        "Second Work",
                        WorkType.GRAPHIC_NOVEL,
                        Set.of(Genre.SCI_FI),
                        false
                )
        );

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("page", "1")
                                .queryParam("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(secondWorkId.toString())
                )
                .andExpect(
                        jsonPath("$.page")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(2)
                );

        verify(publicationService)
                .findPublishedWorks(
                        null,
                        null,
                        PageRequest.of(1, 1)
                );
    }

    @Test
    void shouldReturnEmptyContentWhenPageIsOutOfRange()
            throws Exception {

        stubPublishedWorks(
                null,
                2,
                1,
                1
        );

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("page", "2")
                                .queryParam("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content")
                                .isEmpty()
                )
                .andExpect(
                        jsonPath("$.page")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(1)
                );

        verify(publicationService)
                .findPublishedWorks(
                        null,
                        null,
                        PageRequest.of(2, 1)
                );
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 51"
    })
    void shouldRejectInvalidPaginationParameters(
            String page,
            String size
    ) throws Exception {
        mockMvc.perform(
                        get("/api/works")
                                .queryParam("page", page)
                                .queryParam("size", size)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }

    @Test
    void shouldDelegateFilteredPaginationToPublicationService()
            throws Exception {

        UUID workId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 1);

        when(
                publicationService.findPublishedWorks(
                        null,
                        Genre.SCI_FI,
                        pageable
                )
        ).thenReturn(
                new PageImpl<>(
                        List.of(
                                new PublishedWorkSummary(
                                        workId,
                                        "Metopa Origins",
                                        WorkType.GRAPHIC_NOVEL,
                                        Set.of(Genre.SCI_FI),
                                        true
                                )
                        ),
                        pageable,
                        3
                )
        );

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("genre", "SCI_FI")
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(workId.toString())
                )
                .andExpect(
                        jsonPath("$.page")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(3)
                );

        verify(publicationService)
                .findPublishedWorks(
                        null,
                        Genre.SCI_FI,
                        pageable
                );
    }

    @Test
    void shouldReturnPublishedWorksFilteredByTitle() throws Exception {
        UUID workId = UUID.randomUUID();
        String title = "  dragon  ";

        stubPublishedWorks(
                title,
                null,
                0,
                10,
                1,
                new PublishedWorkSummary(
                        workId,
                        "Dragon Chronicles",
                        WorkType.COMIC,
                        Set.of(Genre.ADVENTURE),
                        true
                )
        );

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("title", title)
                                .queryParam("page", "0")
                                .queryParam("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value(workId.toString()))
                .andExpect(jsonPath("$.content[0].title")
                        .value("Dragon Chronicles"))
                .andExpect(jsonPath("$.content[0].coverUrl")
                        .value("/api/works/" + workId + "/cover"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(publicationService)
                .findPublishedWorks(title, null, PageRequest.of(0, 10));
    }

    @Test
    void shouldReturnPublishedWorksFilteredByTitleAndGenre()
            throws Exception {
        UUID workId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(1, 1);

        stubPublishedWorks(
                "dragon",
                Genre.SCI_FI,
                1,
                1,
                2,
                new PublishedWorkSummary(
                        workId,
                        "Dragon Galaxy",
                        WorkType.GRAPHIC_NOVEL,
                        Set.of(Genre.SCI_FI, Genre.ADVENTURE),
                        false
                )
        );

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("title", "dragon")
                                .queryParam("genre", "SCI_FI")
                                .queryParam("page", "1")
                                .queryParam("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value(workId.toString()))
                .andExpect(jsonPath("$.content[0].title")
                        .value("Dragon Galaxy"))
                .andExpect(jsonPath("$.content[0].genres")
                        .value(containsInAnyOrder("SCI_FI", "ADVENTURE")))
                .andExpect(jsonPath("$.content[0].coverUrl")
                        .value(nullValue()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(publicationService)
                .findPublishedWorks("dragon", Genre.SCI_FI, pageable);
    }

    @Test
    void shouldForwardBlankTitleWithoutChangingIt() throws Exception {
        String blankTitle = "   ";

        stubPublishedWorks(blankTitle, Genre.SCI_FI, 0, 20, 0);

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("title", blankTitle)
                                .queryParam("genre", "SCI_FI")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(publicationService)
                .findPublishedWorks(
                        blankTitle,
                        Genre.SCI_FI,
                        PageRequest.of(0, 20)
                );
    }

    @Test
    void shouldForwardSpecialCharactersInTitle() throws Exception {
        String title = "100%_!";

        stubPublishedWorks(title, null, 0, 20, 0);

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("title", title)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(publicationService)
                .findPublishedWorks(title, null, PageRequest.of(0, 20));
    }

    @Test
    void shouldReturnEmptyContentWhenTitleHasNoMatches()
            throws Exception {
        String title = "not-found";

        stubPublishedWorks(title, null, 0, 10, 0);

        mockMvc.perform(
                        get("/api/works")
                                .queryParam("title", title)
                                .queryParam("page", "0")
                                .queryParam("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(publicationService)
                .findPublishedWorks(title, null, PageRequest.of(0, 10));
    }

    private void stubPublishedWorks(
            Genre genre,
            int page,
            int size,
            long totalElements,
            PublishedWorkSummary... works
    ) {
        stubPublishedWorks(null, genre, page, size, totalElements, works);
    }

    private void stubPublishedWorks(
            String title,
            Genre genre,
            int page,
            int size,
            long totalElements,
            PublishedWorkSummary... works
    ) {
        PageRequest pageable = PageRequest.of(page, size);

        when(publicationService.findPublishedWorks(title, genre, pageable))
                .thenReturn(
                        new PageImpl<>(
                                List.of(works),
                                pageable,
                                totalElements
                        )
                );
    }
}
