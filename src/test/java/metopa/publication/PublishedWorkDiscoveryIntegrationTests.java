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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private String findPageContainingWork(
            UUID workId,
            Genre genre
    ) throws Exception {

        for (int page = 0; ; page++) {
            var request =
                    get("/api/works")
                            .queryParam(
                                    "page",
                                    String.valueOf(page)
                            )
                            .queryParam(
                                    "size",
                                    String.valueOf(PAGE_SIZE)
                            );

            if (genre != null) {
                request.queryParam(
                        "genre",
                        genre.name()
                );
            }

            String response =
                    mockMvc.perform(request)
                            .andExpect(status().isOk())
                            .andExpect(
                                    jsonPath("$.page")
                                            .value(page)
                            )
                            .andExpect(
                                    jsonPath("$.size")
                                            .value(PAGE_SIZE)
                            )
                            .andExpect(
                                    jsonPath("$.content")
                                            .isArray()
                            )
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            List<String> workIds =
                    JsonPath.read(
                            response,
                            "$.content[*].id"
                    );

            if (workIds.contains(workId.toString())) {
                return response;
            }

            int totalPages =
                    ((Number) JsonPath.read(
                            response,
                            "$.totalPages"
                    )).intValue();

            if (page + 1 >= totalPages) {
                throw new AssertionError(
                        "Published work not found in discovery: "
                                + workId
                );
            }
        }
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

    private long countPublishedWorks(Genre genre) {
        if (genre == null) {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(DISTINCT work_id)
                    FROM installment
                    WHERE status = 'PUBLISHED'
                    """,
                    Long.class
            );
        }

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT i.work_id)
                FROM installment i
                WHERE i.status = 'PUBLISHED'
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
