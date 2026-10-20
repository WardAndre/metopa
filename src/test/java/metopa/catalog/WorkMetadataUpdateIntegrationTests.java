package metopa.catalog;

import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.identity.UsernameAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkMetadataUpdateIntegrationTests {

    private static final String PASSWORD =
            "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureAuthorExists() {
        try {
            identityService.createUser(
                    new CreateUserCommand(
                            "metadataauthor",
                            "metadataauthor@example.com",
                            "Metadata Author",
                            PASSWORD
                    )
            );
        } catch (UsernameAlreadyExistsException ignored) {
            // User already created by a previous test execution.
        }
    }

    @Test
    void shouldUpdateWorkMetadataThroughHttp()
            throws Exception {

        UUID ownerId =
                identityService
                        .findByUsername(
                                "metadataauthor"
                        )
                        .id();

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Original Title",
                                "Original description.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE,
                                Set.of(
                                        Genre.DRAMA,
                                        Genre.ROMANCE
                                )
                        )
                );

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user("metadataauthor")
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(
                                                "metadataauthor"
                                        )
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        put(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "title": "  Updated Title  ",
                                          "description": "  Updated description.  ",
                                          "type": "MANGA",
                                          "readingDirection": "RIGHT_TO_LEFT",
                                          "presentationMode": "DOUBLE_PAGE",
                                          "genres": [
                                            "SCI_FI",
                                            "ADVENTURE"
                                          ]
                                        }
                                        """)
                )
                .andExpect(
                        status().isNoContent()
                );

        Map<String, Object> storedWork =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            title,
                            description,
                            work_type,
                            reading_direction,
                            presentation_mode
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(storedWork.get("title"))
                .isEqualTo("Updated Title");

        assertThat(storedWork.get("description"))
                .isEqualTo("Updated description.");

        assertThat(storedWork.get("work_type"))
                .isEqualTo("MANGA");

        assertThat(
                storedWork.get("reading_direction")
        ).isEqualTo("RIGHT_TO_LEFT");

        assertThat(
                storedWork.get("presentation_mode")
        ).isEqualTo("DOUBLE_PAGE");

        assertThat(
                jdbcTemplate.queryForList(
                        """
                        SELECT genre
                        FROM work_genre
                        WHERE work_id = ?
                        ORDER BY genre
                        """,
                        String.class,
                        workId
                )
        ).containsExactly(
                "ADVENTURE",
                "SCI_FI"
        );
    }

    @Test
    void shouldRejectWorkMetadataUpdateFromNonOwner()
            throws Exception {

        UUID ownerId =
                identityService
                        .findByUsername(
                                "metadataauthor"
                        )
                        .id();

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Protected Work",
                                "Original description.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE,
                                Set.of(
                                        Genre.DRAMA
                                )
                        )
                );

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8);

        String anotherUsername =
                "metadata-other-" + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        anotherUsername,
                        anotherUsername + "@example.com",
                        "Another Metadata User",
                        PASSWORD
                )
        );

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(anotherUsername)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(
                                                anotherUsername
                                        )
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        put(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "title": "Unauthorized Change",
                                      "description": "Changed description.",
                                      "type": "MANGA",
                                      "readingDirection": "RIGHT_TO_LEFT",
                                      "presentationMode": "DOUBLE_PAGE",
                                      "genres": [
                                        "SCI_FI"
                                      ]
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Work access denied"
                                )
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

        Map<String, Object> storedWork =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            title,
                            description,
                            work_type,
                            reading_direction,
                            presentation_mode
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(storedWork.get("title"))
                .isEqualTo("Protected Work");

        assertThat(storedWork.get("description"))
                .isEqualTo("Original description.");

        assertThat(storedWork.get("work_type"))
                .isEqualTo("COMIC");

        assertThat(
                jdbcTemplate.queryForList(
                        """
                        SELECT genre
                        FROM work_genre
                        WHERE work_id = ?
                        """,
                        String.class,
                        workId
                )
        ).containsExactly(
                "DRAMA"
        );
    }

    @Test
    void shouldRejectInvalidWorkMetadata()
            throws Exception {

        UUID ownerId =
                identityService
                        .findByUsername(
                                "metadataauthor"
                        )
                        .id();

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Valid Work",
                                "Valid description.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE,
                                Set.of(
                                        Genre.DRAMA
                                )
                        )
                );

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user("metadataauthor")
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(
                                                "metadataauthor"
                                        )
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        put(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "title": "   ",
                                      "description": "Invalid update",
                                      "type": "COMIC",
                                      "readingDirection": "LEFT_TO_RIGHT",
                                      "presentationMode": "SINGLE_PAGE",
                                      "genres": [
                                        "DRAMA"
                                      ]
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Invalid work data"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "One or more work fields are invalid."
                                )
                );

        String storedTitle =
                jdbcTemplate.queryForObject(
                        """
                        SELECT title
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(storedTitle)
                .isEqualTo("Valid Work");
    }

    @Test
    void shouldReturnCreatorWorkDetailsForOwner()
            throws Exception {

        UUID ownerId =
                identityService
                        .findByUsername(
                                "metadataauthor"
                        )
                        .id();

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Creator Work",
                                "A work still being prepared.",
                                WorkType.GRAPHIC_NOVEL,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.DOUBLE_PAGE,
                                Set.of(
                                        Genre.SCI_FI,
                                        Genre.ADVENTURE
                                )
                        )
                );

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user("metadataauthor")
                                        .password(PASSWORD)
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                authenticated()
                                        .withUsername(
                                                "metadataauthor"
                                        )
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}",
                                workId
                        )
                                .session(session)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        workId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Creator Work")
                )
                .andExpect(
                        jsonPath("$.description")
                                .value(
                                        "A work still being prepared."
                                )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "GRAPHIC_NOVEL"
                                )
                )
                .andExpect(
                        jsonPath("$.readingDirection")
                                .value(
                                        "LEFT_TO_RIGHT"
                                )
                )
                .andExpect(
                        jsonPath("$.presentationMode")
                                .value(
                                        "DOUBLE_PAGE"
                                )
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
                        jsonPath("$.hasCover")
                                .value(false)
                );
    }

    @Test
    void shouldRejectCreatorWorkDetailsFromNonOwner()
            throws Exception {

        UUID ownerId =
                identityService
                        .findByUsername(
                                "metadataauthor"
                        )
                        .id();

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Private Creator Work",
                                "Private draft metadata.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE,
                                Set.of(
                                        Genre.DRAMA
                                )
                        )
                );

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8);

        String anotherUsername =
                "creator-detail-other-" + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        anotherUsername,
                        anotherUsername + "@example.com",
                        "Another Creator Detail User",
                        PASSWORD
                )
        );

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(anotherUsername)
                                        .password(PASSWORD)
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                authenticated()
                                        .withUsername(
                                                anotherUsername
                                        )
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}",
                                workId
                        )
                                .session(session)
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Work access denied"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );
    }
}