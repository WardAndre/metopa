package metopa.publication;

import com.jayway.jsonpath.JsonPath;
import metopa.catalog.CatalogService;
import metopa.catalog.CreateWorkCommand;
import metopa.catalog.Genre;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.moderation.CreateReportCommand;
import metopa.moderation.ModerationService;
import metopa.moderation.ReportReason;
import metopa.reading.ReadingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-work-discovery-storage"
        }
)
@AutoConfigureMockMvc
class PublishedWorkDiscoveryIntegrationTests {

    private static final String PASSWORD =
            "StrongPassword123!";

    private static final int PAGE_SIZE = 50;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private PublicationService publicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReadingService readingService;

    @Autowired
    private ModerationService moderationService;

    @Test
    void shouldReturnOnlyPublishedWorksWithoutDuplicates()
            throws Exception {

        long totalBefore = countPublishedWorks(null);

        UUID ownerId = createUser("discovery-user");

        UUID firstPublishedWorkId =
                createPublishedWork(
                        ownerId,
                        "First Published Work",
                        2
                );

        catalogService.setCover(
                ownerId,
                firstPublishedWorkId,
                "image/webp",
                "discovery-cover".getBytes()
        );

        UUID secondPublishedWorkId =
                createPublishedWork(
                        ownerId,
                        "Second Published Work",
                        1
                );

        UUID draftWorkId =
                createDraftWork(
                        ownerId,
                        "Draft Work"
                );

        String firstPage =
                findPageContainingWork(
                        firstPublishedWorkId,
                        null
                );

        String secondPage =
                findPageContainingWork(
                        secondPublishedWorkId,
                        null
                );

        assertThat(
                JsonPath.<List<String>>read(
                        firstPage,
                        workFieldPath(
                                firstPublishedWorkId,
                                "id"
                        )
                )
        ).containsExactly(
                firstPublishedWorkId.toString()
        );

        assertThat(
                JsonPath.<List<String>>read(
                        firstPage,
                        workFieldPath(
                                firstPublishedWorkId,
                                "coverUrl"
                        )
                )
        ).containsExactly(
                "/api/works/"
                        + firstPublishedWorkId
                        + "/cover"
        );

        assertThat(
                JsonPath.<List<String>>read(
                        secondPage,
                        workFieldPath(
                                secondPublishedWorkId,
                                "id"
                        )
                )
        ).containsExactly(
                secondPublishedWorkId.toString()
        );

        long expectedTotal = totalBefore + 2;

        assertThat(totalElements(firstPage))
                .isEqualTo(expectedTotal);

        assertThat(totalElements(secondPage))
                .isEqualTo(expectedTotal);

        assertThat(countPublishedWorks(null))
                .isEqualTo(expectedTotal);

        mockMvc.perform(
                        get(
                                "/api/works/{workId}",
                                draftWorkId
                        )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldFilterPublishedWorksByGenre()
            throws Exception {

        long totalBefore =
                countPublishedWorks(Genre.SCI_FI);

        UUID ownerId =
                createUser("genre-discovery-user");

        UUID scienceFictionWorkId =
                createPublishedWork(
                        ownerId,
                        "Science Fiction Published Work",
                        2,
                        Set.of(
                                Genre.SCI_FI,
                                Genre.ADVENTURE
                        )
                );

        UUID dramaWorkId =
                createPublishedWork(
                        ownerId,
                        "Drama Published Work",
                        1,
                        Set.of(Genre.DRAMA)
                );

        UUID draftScienceFictionWorkId =
                createDraftWork(
                        ownerId,
                        "Science Fiction Draft Work",
                        Set.of(Genre.SCI_FI)
                );

        String response =
                findPageContainingWork(
                        scienceFictionWorkId,
                        Genre.SCI_FI
                );

        assertThat(
                JsonPath.<List<String>>read(
                        response,
                        workFieldPath(
                                scienceFictionWorkId,
                                "id"
                        )
                )
        ).containsExactly(
                scienceFictionWorkId.toString()
        );

        List<List<String>> genres =
                JsonPath.read(
                        response,
                        workFieldPath(
                                scienceFictionWorkId,
                                "genres"
                        )
                );

        assertThat(genres).hasSize(1);

        assertThat(genres.getFirst())
                .contains(
                        "SCI_FI",
                        "ADVENTURE"
                );

        assertThat(
                JsonPath.<List<String>>read(
                        response,
                        "$.content[*].id"
                )
        ).doesNotContain(
                dramaWorkId.toString(),
                draftScienceFictionWorkId.toString()
        );

        long expectedTotal = totalBefore + 1;

        assertThat(totalElements(response))
                .isEqualTo(expectedTotal);

        assertThat(countPublishedWorks(Genre.SCI_FI))
                .isEqualTo(expectedTotal);

        mockMvc.perform(
                        get(
                                "/api/works/{workId}",
                                draftScienceFictionWorkId
                        )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldFindPublishedWorksByPartialTitleIgnoringCase()
            throws Exception {

        UUID ownerId = createUser("title-discovery-user");

        String marker = "dragon-" + UUID.randomUUID();

        UUID firstWorkId =
                createPublishedWork(
                        ownerId,
                        "The " + marker + " Chronicles",
                        2
                );

        UUID secondWorkId =
                createPublishedWork(
                        ownerId,
                        "Legends of " + marker,
                        1
                );

        UUID draftWorkId =
                createDraftWork(
                        ownerId,
                        "Unpublished " + marker
                );

        String response = findPublishedWorks(
                "  " + marker.toUpperCase(Locale.ROOT) + "  ",
                null,
                0,
                PAGE_SIZE
        );

        List<String> workIds = JsonPath.read(
                response,
                "$.content[*].id"
        );

        assertThat(workIds)
                .containsExactlyInAnyOrder(
                        firstWorkId.toString(),
                        secondWorkId.toString()
                );

        assertThat(workIds)
                .doesNotContain(draftWorkId.toString());

        assertThat(totalElements(response)).isEqualTo(2);
        assertThat(totalPages(response)).isEqualTo(1);
    }

    @Test
    void shouldFilterPublishedWorksByTitleAndGenre()
            throws Exception {

        UUID ownerId = createUser("title-genre-user");

        String marker = "expedition-" + UUID.randomUUID();

        UUID matchingWorkId =
                createPublishedWork(
                        ownerId,
                        "The " + marker + " Journey",
                        2,
                        Set.of(
                                Genre.SCI_FI,
                                Genre.ADVENTURE
                        )
                );

        UUID wrongGenreWorkId =
                createPublishedWork(
                        ownerId,
                        "Drama " + marker,
                        1,
                        Set.of(Genre.DRAMA)
                );

        UUID wrongTitleWorkId =
                createPublishedWork(
                        ownerId,
                        "An Unrelated Story",
                        1,
                        Set.of(Genre.SCI_FI)
                );

        UUID draftWorkId =
                createDraftWork(
                        ownerId,
                        "Draft " + marker,
                        Set.of(Genre.SCI_FI)
                );

        String response = findPublishedWorks(
                marker,
                Genre.SCI_FI,
                0,
                PAGE_SIZE
        );

        List<String> workIds = JsonPath.read(
                response,
                "$.content[*].id"
        );

        assertThat(workIds)
                .containsExactly(matchingWorkId.toString());

        assertThat(workIds).doesNotContain(
                wrongGenreWorkId.toString(),
                wrongTitleWorkId.toString(),
                draftWorkId.toString()
        );

        List<List<String>> genres = JsonPath.read(
                response,
                workFieldPath(matchingWorkId, "genres")
        );

        assertThat(genres).hasSize(1);

        assertThat(genres.getFirst())
                .contains(
                        "SCI_FI",
                        "ADVENTURE"
                );

        assertThat(totalElements(response)).isEqualTo(1);
        assertThat(totalPages(response)).isEqualTo(1);
    }

    @Test
    void shouldPaginateTitleSearchByLatestPublishedInstallment()
            throws Exception {

        UUID ownerId = createUser("title-page-user");

        String marker = "chronicles-" + UUID.randomUUID();

        UUID oldestWorkId =
                createPublishedWork(
                        ownerId,
                        "Oldest " + marker,
                        2
                );

        UUID middleWorkId =
                createPublishedWork(
                        ownerId,
                        "Middle " + marker,
                        1
                );

        UUID newestWorkId =
                createPublishedWork(
                        ownerId,
                        "Newest " + marker,
                        1
                );

        jdbcTemplate.update(
                """
                UPDATE installment
                SET updated_at =
                    CURRENT_TIMESTAMP - INTERVAL '3 days'
                WHERE work_id = ?
                """,
                oldestWorkId
        );

        jdbcTemplate.update(
                """
                UPDATE installment
                SET updated_at =
                    CURRENT_TIMESTAMP - INTERVAL '2 days'
                WHERE work_id = ?
                """,
                middleWorkId
        );

        jdbcTemplate.update(
                """
                UPDATE installment
                SET updated_at =
                    CURRENT_TIMESTAMP - INTERVAL '1 day'
                WHERE work_id = ?
                """,
                newestWorkId
        );

        String firstPage = findPublishedWorks(
                marker,
                null,
                0,
                1
        );

        String secondPage = findPublishedWorks(
                marker,
                null,
                1,
                1
        );

        String thirdPage = findPublishedWorks(
                marker,
                null,
                2,
                1
        );

        String outOfRangePage = findPublishedWorks(
                marker,
                null,
                3,
                1
        );

        assertThat(
                JsonPath.<List<String>>read(
                        firstPage,
                        "$.content[*].id"
                )
        ).containsExactly(newestWorkId.toString());

        assertThat(
                JsonPath.<List<String>>read(
                        secondPage,
                        "$.content[*].id"
                )
        ).containsExactly(middleWorkId.toString());

        assertThat(
                JsonPath.<List<String>>read(
                        thirdPage,
                        "$.content[*].id"
                )
        ).containsExactly(oldestWorkId.toString());

        assertThat(
                JsonPath.<List<String>>read(
                        outOfRangePage,
                        "$.content[*].id"
                )
        ).isEmpty();

        assertThat(totalElements(firstPage)).isEqualTo(3);
        assertThat(totalElements(secondPage)).isEqualTo(3);
        assertThat(totalElements(thirdPage)).isEqualTo(3);
        assertThat(totalElements(outOfRangePage)).isEqualTo(3);

        assertThat(totalPages(firstPage)).isEqualTo(3);
        assertThat(totalPages(secondPage)).isEqualTo(3);
        assertThat(totalPages(thirdPage)).isEqualTo(3);
        assertThat(totalPages(outOfRangePage)).isEqualTo(3);
    }

    @Test
    void shouldTreatTitleWildcardsAsLiteralCharacters()
            throws Exception {

        UUID ownerId = createUser("title-escape-user");

        String marker = UUID.randomUUID().toString();

        String literalTitle = "100%_!-" + marker;

        UUID matchingWorkId =
                createPublishedWork(
                        ownerId,
                        "The " + literalTitle + " Tale",
                        1
                );

        UUID wildcardDecoyWorkId =
                createPublishedWork(
                        ownerId,
                        "The 100AB!-" + marker + " Tale",
                        1
                );

        UUID draftWorkId =
                createDraftWork(
                        ownerId,
                        "Draft " + literalTitle
                );

        String response = findPublishedWorks(
                literalTitle,
                null,
                0,
                PAGE_SIZE
        );

        List<String> workIds = JsonPath.read(
                response,
                "$.content[*].id"
        );

        assertThat(workIds)
                .containsExactly(matchingWorkId.toString());

        assertThat(workIds).doesNotContain(
                wildcardDecoyWorkId.toString(),
                draftWorkId.toString()
        );

        assertThat(totalElements(response)).isEqualTo(1);
        assertThat(totalPages(response)).isEqualTo(1);
    }

    @Test
    void shouldExcludeRestrictedWorksFromTitleSearchBeforePagination()
            throws Exception {

        UUID ownerId = createUser("visibility-discovery");

        String uniqueTitle = "Visibility-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "");

        UUID restrictedWorkId = createPublishedWork(
                ownerId,
                uniqueTitle + " Restricted",
                2,
                Set.of(Genre.SCI_FI)
        );

        UUID publicWorkId = createPublishedWork(
                ownerId,
                uniqueTitle + " Public",
                1,
                Set.of(Genre.SCI_FI)
        );

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                restrictedWorkId
        );

        assertThat(updatedRows).isEqualTo(1);

        for (boolean filterByGenre : List.of(false, true)) {
            var request = get("/api/works")
                    .queryParam("title", uniqueTitle)
                    .queryParam("page", "0")
                    .queryParam("size", "1");

            if (filterByGenre) {
                request.queryParam("genre", "SCI_FI");
            }

            mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id")
                            .value(publicWorkId.toString()));
        }
    }

    @Test
    void shouldExcludeRestrictedWorksFromPublicDiscoveryTotals()
            throws Exception {

        Long totalBefore = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT i.work_id)
                FROM installment i
                JOIN work w ON w.id = i.work_id
                WHERE i.status = 'PUBLISHED'
                  AND w.visibility = 'PUBLIC'
                """,
                Long.class
        );

        Long genreTotalBefore = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT i.work_id)
                FROM installment i
                JOIN work w ON w.id = i.work_id
                WHERE i.status = 'PUBLISHED'
                  AND w.visibility = 'PUBLIC'
                  AND EXISTS (
                      SELECT 1
                      FROM work_genre wg
                      WHERE wg.work_id = i.work_id
                        AND wg.genre = 'SCI_FI'
                  )
                """,
                Long.class
        );

        UUID ownerId = createUser("visibility-count");

        String suffix = UUID.randomUUID().toString();

        UUID publicWorkId = createPublishedWork(
                ownerId,
                "Public " + suffix,
                1,
                Set.of(Genre.SCI_FI)
        );

        UUID restrictedWorkId = createPublishedWork(
                ownerId,
                "Restricted " + suffix,
                1,
                Set.of(Genre.SCI_FI)
        );

        assertThat(publicWorkId).isNotEqualTo(restrictedWorkId);

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                restrictedWorkId
        );

        assertThat(updatedRows).isEqualTo(1);

        mockMvc.perform(get("/api/works")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements")
                        .value(totalBefore + 1));

        mockMvc.perform(get("/api/works")
                        .queryParam("genre", "SCI_FI")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements")
                        .value(genreTotalBefore + 1));
    }

    @Test
    void shouldHideRestrictedPublishedWorkFromDirectAccess()
            throws Exception {

        UUID ownerId = createUser("restricted-detail");

        UUID workId = createPublishedWork(
                ownerId,
                "Restricted Direct Access " + UUID.randomUUID(),
                1
        );

        // A published and public work must be accessible.
        mockMvc.perform(
                        get("/api/works/{workId}", workId)
                )
                .andExpect(status().isOk());

        // Simulate an administrative visibility restriction.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                workId
        );

        assertThat(updatedRows).isEqualTo(1);

        // A restricted work must no longer be publicly accessible.
        mockMvc.perform(
                        get("/api/works/{workId}", workId)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldHideRestrictedPublishedWorkCoverFromDirectAccess()
            throws Exception {

        UUID ownerId = createUser("restricted-cover");

        UUID workId = createPublishedWork(
                ownerId,
                "Restricted Cover " + UUID.randomUUID(),
                1
        );

        byte[] coverContent = "restricted-cover-content".getBytes();

        catalogService.setCover(
                ownerId,
                workId,
                "image/webp",
                coverContent
        );

        // A published and public work cover must be accessible.
        mockMvc.perform(
                        get("/api/works/{workId}/cover", workId)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(coverContent));

        // Simulate an administrative visibility restriction.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                workId
        );

        assertThat(updatedRows).isEqualTo(1);

        // A restricted work cover must no longer be publicly accessible.
        mockMvc.perform(
                        get("/api/works/{workId}/cover", workId)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldHideRestrictedPublishedInstallmentFromDirectAccess()
            throws Exception {

        UUID ownerId = createUser("restricted-installment");

        UUID workId = createPublishedWork(
                ownerId,
                "Restricted Installment " + UUID.randomUUID(),
                1
        );

        UUID installmentId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM installment
                WHERE work_id = ?
                  AND status = 'PUBLISHED'
                ORDER BY number ASC
                LIMIT 1
                """,
                UUID.class,
                workId
        );

        assertThat(installmentId).isNotNull();

        // A published installment of a public work must be accessible.
        mockMvc.perform(
                        get(
                                "/api/installments/{installmentId}",
                                installmentId
                        )
                )
                .andExpect(status().isOk());

        // Simulate an administrative visibility restriction.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                workId
        );

        assertThat(updatedRows).isEqualTo(1);

        // An installment of a restricted work must not be publicly accessible.
        mockMvc.perform(
                        get(
                                "/api/installments/{installmentId}",
                                installmentId
                        )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldHideRestrictedPublishedPageContentFromDirectAccess()
            throws Exception {

        UUID ownerId = createUser("restricted-page");

        UUID workId = createPublishedWork(
                ownerId,
                "Restricted Page " + UUID.randomUUID(),
                1
        );

        UUID installmentId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM installment
                WHERE work_id = ?
                  AND status = 'PUBLISHED'
                ORDER BY number ASC
                LIMIT 1
                """,
                UUID.class,
                workId
        );

        assertThat(installmentId).isNotNull();

        UUID pageId = publicationService
                .findPublishedInstallment(installmentId)
                .pages()
                .getFirst()
                .id();

        byte[] expectedContent = "page-1".getBytes();

        // Published page content must be accessible for a public work.
        mockMvc.perform(
                        get("/api/pages/{pageId}/content", pageId)
                )
                .andExpect(status().isOk())
                .andExpect(content().bytes(expectedContent));

        // Simulate an administrative visibility restriction.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                workId
        );

        assertThat(updatedRows).isEqualTo(1);

        // Page content must not be accessible for a restricted work.
        mockMvc.perform(
                        get("/api/pages/{pageId}/content", pageId)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectReadingProgressForRestrictedWork()
            throws Exception {

        UUID userId = createUser("restricted-reading");

        UUID workId = createPublishedWork(
                userId,
                "Restricted Reading " + UUID.randomUUID(),
                1
        );

        PublishedWorkView work =
                publicationService.findPublishedWork(workId);

        UUID installmentId =
                work.installments().getFirst().id();

        UUID pageId =
                publicationService
                        .findPublishedInstallment(installmentId)
                        .pages()
                        .getFirst()
                        .id();

        // The work must be restricted before recording progress.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                workId
        );

        assertThat(updatedRows).isEqualTo(1);

        assertThatThrownBy(() ->
                readingService.saveProgress(userId, pageId)
        ).isInstanceOf(
                PublishedPageNotFoundException.class
        );

        // Rejected progress must not create a reading record.
        assertThat(readingService.findProgress(userId, workId))
                .isEmpty();
    }

    @Test
    void shouldPreserveExistingReadingProgressWhenWorkBecomesRestricted()
            throws Exception {

        UUID userId = createUser("restricted-progress-update");

        UUID workId = createPublishedWork(
                userId,
                "Restricted Progress Update " + UUID.randomUUID(),
                2
        );

        PublishedWorkView work =
                publicationService.findPublishedWork(workId);

        UUID firstInstallmentId =
                work.installments().get(0).id();

        UUID secondInstallmentId =
                work.installments().get(1).id();

        UUID firstPageId =
                publicationService
                        .findPublishedInstallment(firstInstallmentId)
                        .pages()
                        .getFirst()
                        .id();

        UUID secondPageId =
                publicationService
                        .findPublishedInstallment(secondInstallmentId)
                        .pages()
                        .getFirst()
                        .id();

        // Save reading progress while the work is public.
        UUID progressId =
                readingService.saveProgress(userId, firstPageId);

        var originalProgress =
                readingService.findProgress(userId, workId)
                        .orElseThrow();

        assertThat(originalProgress.id()).isEqualTo(progressId);
        assertThat(originalProgress.pageId()).isEqualTo(firstPageId);

        // Restrict the work after progress has been saved.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                workId
        );

        assertThat(updatedRows).isEqualTo(1);

        // Updating progress for a restricted work must be rejected.
        assertThatThrownBy(() ->
                readingService.saveProgress(userId, secondPageId)
        ).isInstanceOf(
                PublishedPageNotFoundException.class
        );

        // Previously saved progress must remain unchanged.
        var preservedProgress =
                readingService.findProgress(userId, workId)
                        .orElseThrow();

        assertThat(preservedProgress.id())
                .isEqualTo(originalProgress.id());

        assertThat(preservedProgress.installmentId())
                .isEqualTo(firstInstallmentId);

        assertThat(preservedProgress.pageId())
                .isEqualTo(firstPageId);

        assertThat(preservedProgress.updatedAt())
                .isEqualTo(originalProgress.updatedAt());
    }

    @Test
    void shouldHideAndRestorePublishedWorkAfterModerationActions()
            throws Exception {

        UUID ownerId = createUser("moderated-public-work");

        UUID workId = createPublishedWork(
                ownerId,
                "Moderated Published Work " + UUID.randomUUID(),
                1
        );

        // The published work must initially be publicly accessible.
        mockMvc.perform(
                        get("/api/works/{workId}", workId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workId.toString()));

        assertThat(catalogService.hasPublicVisibility(workId))
                .isTrue();

        // Create a moderation report for the published work.
        UUID reportId = moderationService.createReport(
                new CreateReportCommand(
                        ownerId,
                        workId,
                        ReportReason.COPYRIGHT,
                        "Possible copyright infringement."
                )
        );

        // Exercise the moderation workflow.
        moderationService.startReview(
                reportId,
                ownerId,
                "Administrative review initiated."
        );

        moderationService.resolveWorkRestriction(
                reportId,
                ownerId,
                "Confirmed content policy violation."
        );

        // Verify that the moderation decision was persisted.
        String decision = jdbcTemplate.queryForObject(
                """
                SELECT decision
                FROM moderation_report
                WHERE id = ?
                """,
                String.class,
                reportId
        );

        assertThat(decision)
                .isEqualTo("WORK_RESTRICTED");

        // Verify that the work visibility changed in PostgreSQL.
        String visibility = jdbcTemplate.queryForObject(
                """
                SELECT visibility
                FROM work
                WHERE id = ?
                """,
                String.class,
                workId
        );

        assertThat(visibility)
                .isEqualTo("RESTRICTED");

        assertThat(catalogService.hasPublicVisibility(workId))
                .isFalse();

        // The previously accessible work must now be hidden.
        mockMvc.perform(
                        get("/api/works/{workId}", workId)
                )
                .andExpect(status().isNotFound());

        // Restore the work through the moderation service.
        moderationService.restoreWork(
                reportId,
                ownerId,
                "Restriction reversed after administrative review."
        );

        // Verify that the original moderation decision is preserved.
        String decisionAfterRestoration =
                jdbcTemplate.queryForObject(
                        """
                        SELECT decision
                        FROM moderation_report
                        WHERE id = ?
                        """,
                        String.class,
                        reportId
                );

        assertThat(decisionAfterRestoration)
                .isEqualTo("WORK_RESTRICTED");

        String statusAfterRestoration =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM moderation_report
                        WHERE id = ?
                        """,
                        String.class,
                        reportId
                );

        assertThat(statusAfterRestoration)
                .isEqualTo("RESOLVED");

        // Verify the restored work visibility in PostgreSQL.
        String restoredVisibility =
                jdbcTemplate.queryForObject(
                        """
                        SELECT visibility
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(restoredVisibility)
                .isEqualTo("PUBLIC");

        assertThat(catalogService.hasPublicVisibility(workId))
                .isTrue();

        // Verify that the restoration was recorded in the audit trail.
        Long restorationAuditCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM moderation_audit_entry
                        WHERE report_id = ?
                          AND action = 'WORK_RESTORED'
                        """,
                        Long.class,
                        reportId
                );

        assertThat(restorationAuditCount)
                .isEqualTo(1L);

        // The restored work must be publicly accessible again.
        mockMvc.perform(
                        get("/api/works/{workId}", workId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workId.toString()));
    }

    private String findPageContainingWork(
            UUID workId,
            Genre genre
    ) throws Exception {

        for (int page = 0; ; page++) {

            String response = findPublishedWorks(
                    null,
                    genre,
                    page,
                    PAGE_SIZE
            );

            List<String> workIds = JsonPath.read(
                    response,
                    "$.content[*].id"
            );

            if (workIds.contains(workId.toString())) {
                return response;
            }

            if (page + 1 >= totalPages(response)) {
                throw new AssertionError(
                        "Published work not found in discovery: "
                                + workId
                );
            }
        }
    }

    private String findPublishedWorks(
            String title,
            Genre genre,
            int page,
            int size
    ) throws Exception {

        var request =
                get("/api/works")
                        .queryParam(
                                "page",
                                String.valueOf(page)
                        )
                        .queryParam(
                                "size",
                                String.valueOf(size)
                        );

        if (title != null) {
            request.queryParam(
                    "title",
                    title
            );
        }

        if (genre != null) {
            request.queryParam(
                    "genre",
                    genre.name()
            );
        }

        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.page")
                                .value(page)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(size)
                )
                .andExpect(
                        jsonPath("$.content")
                                .isArray()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String workFieldPath(
            UUID workId,
            String field
    ) {
        return "$.content[?(@.id == '"
                + workId
                + "')]."
                + field;
    }

    private long totalElements(String response) {
        return ((Number) JsonPath.read(
                response,
                "$.totalElements"
        )).longValue();
    }

    private int totalPages(String response) {
        return ((Number) JsonPath.read(
                response,
                "$.totalPages"
        )).intValue();
    }

    private long countPublishedWorks(Genre genre) {
        if (genre == null) {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(DISTINCT i.work_id)
                    FROM installment i
                    JOIN work w ON w.id = i.work_id
                    WHERE i.status = 'PUBLISHED'
                      AND w.visibility = 'PUBLIC'
                    """,
                    Long.class
            );
        }

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT i.work_id)
                FROM installment i
                JOIN work w ON w.id = i.work_id
                WHERE i.status = 'PUBLISHED'
                  AND w.visibility = 'PUBLIC'
                  AND EXISTS (
                      SELECT 1
                      FROM work_genre wg
                      WHERE wg.work_id = i.work_id
                        AND wg.genre = ?
                  )
                """,
                Long.class,
                genre.name()
        );
    }

    private UUID createPublishedWork(
            UUID ownerId,
            String title,
            int installmentCount
    ) {
        return createPublishedWork(
                ownerId,
                title,
                installmentCount,
                null
        );
    }

    private UUID createPublishedWork(
            UUID ownerId,
            String title,
            int installmentCount,
            Set<Genre> genres
    ) {
        UUID workId =
                createWork(
                        ownerId,
                        title,
                        genres
                );

        for (int number = 1;
             number <= installmentCount;
             number++) {

            UUID installmentId =
                    publicationService.createInstallment(
                            ownerId,
                            new CreateInstallmentCommand(
                                    workId,
                                    InstallmentType.ISSUE,
                                    number,
                                    "Issue " + number
                            )
                    );

            publicationService.createPage(
                    ownerId,
                    new CreatePageCommand(
                            installmentId,
                            1,
                            "image/jpeg",
                            ("page-" + number).getBytes()
                    )
            );

            publicationService.publishInstallment(
                    ownerId,
                    installmentId
            );
        }

        return workId;
    }

    private UUID createDraftWork(
            UUID ownerId,
            String title
    ) {
        return createDraftWork(
                ownerId,
                title,
                null
        );
    }

    private UUID createDraftWork(
            UUID ownerId,
            String title,
            Set<Genre> genres
    ) {
        UUID workId =
                createWork(
                        ownerId,
                        title,
                        genres
                );

        UUID installmentId =
                publicationService.createInstallment(
                        ownerId,
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.ISSUE,
                                1,
                                "Draft Issue"
                        )
                );

        publicationService.createPage(
                ownerId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "draft-page".getBytes()
                )
        );

        return workId;
    }

    private UUID createWork(
            UUID ownerId,
            String title,
            Set<Genre> genres
    ) {
        if (genres == null) {
            return catalogService.createWork(
                    new CreateWorkCommand(
                            ownerId,
                            title,
                            null,
                            WorkType.COMIC,
                            ReadingDirection.LEFT_TO_RIGHT,
                            PresentationMode.SINGLE_PAGE
                    )
            );
        }

        return catalogService.createWork(
                new CreateWorkCommand(
                        ownerId,
                        title,
                        null,
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE,
                        genres
                )
        );
    }

    private UUID createUser(String prefix) {
        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8);

        String username =
                prefix + "-" + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Discovery Test User",
                        PASSWORD
                )
        );

        UserReference user =
                identityService.findByUsername(
                        username
                );

        return user.id();
    }
}
