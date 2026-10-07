package metopa.library;

import metopa.catalog.CatalogService;
import metopa.catalog.CreateWorkCommand;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreateInstallmentCommand;
import metopa.publication.CreatePageCommand;
import metopa.publication.InstallmentType;
import metopa.publication.PublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-library-storage"
        }
)
@AutoConfigureMockMvc
class LibraryIntegrationTests {

    private static final String PASSWORD =
            "StrongPassword123!";

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
    void shouldAddPublishedWorkToAuthenticatedUsersLibrary()
            throws Exception {

        TestUser user =
                createUser("library-user");

        UUID workId =
                createPublishedWork(user.id());

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        post(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM library_entry
                        WHERE user_id = ?
                          AND work_id = ?
                        """,
                        Integer.class,
                        user.id(),
                        workId
                );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldRejectUnauthenticatedUser()
            throws Exception {

        UUID workId = UUID.randomUUID();

        mockMvc.perform(
                        post(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Authentication required"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                );
    }

    @Test
    void shouldRejectWorkWithoutPublishedContent()
            throws Exception {

        TestUser user =
                createUser("library-draft");

        UUID workId =
                createWork(user.id());

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        post(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
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
                                        "The requested work is not available for the library."
                                )
                );
    }

    @Test
    void shouldRejectDuplicateLibraryEntry()
            throws Exception {

        TestUser user =
                createUser("library-duplicate");

        UUID workId =
                createPublishedWork(user.id());

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        post(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isConflict())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Work already in library"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The work is already present in the user's library."
                                )
                );
    }

    private UUID createPublishedWork(UUID ownerId) {
        UUID workId =
                createWork(ownerId);

        UUID installmentId =
                publicationService.createInstallment(
                        ownerId,
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                null
                        )
                );

        publicationService.createPage(
                ownerId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "library-test-page".getBytes()
                )
        );

        publicationService.publishInstallment(
                ownerId,
                installmentId
        );

        return workId;
    }

    private UUID createWork(UUID ownerId) {
        return catalogService.createWork(
                new CreateWorkCommand(
                        ownerId,
                        "Library Work "
                                + UUID.randomUUID(),
                        null,
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                )
        );
    }

    private TestUser createUser(String prefix) {
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
                        "Library Test User",
                        PASSWORD
                )
        );

        UserReference reference =
                identityService.findByUsername(
                        username
                );

        return new TestUser(
                reference.id(),
                reference.username()
        );
    }

    private MockHttpSession login(String username)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                formLogin(
                                        "/api/auth/login"
                                )
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        return (MockHttpSession) result
                .getRequest()
                .getSession(false);
    }

    private record TestUser(
            UUID id,
            String username
    ) {
    }
}