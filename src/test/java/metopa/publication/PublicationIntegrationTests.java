package metopa.publication;

import metopa.catalog.CatalogService;
import metopa.catalog.CreateWorkCommand;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.identity.CreateUserCommand;
import metopa.identity.EmailAlreadyExistsException;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicationIntegrationTests {

    private static final String OWNER_USERNAME =
            "publicationowner";

    private static final String OTHER_USERNAME =
            "publicationother";

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

    private UUID ownerId;
    private UUID otherUserId;
    private UUID ownerWorkId;
    private UUID otherWorkId;

    @BeforeEach
    void setUp() {
        ensureUserExists(
                OWNER_USERNAME,
                "publicationowner@example.com",
                "Publication Owner"
        );

        ensureUserExists(
                OTHER_USERNAME,
                "publicationother@example.com",
                "Publication Other"
        );

        ownerId = identityService
                .findByUsername(OWNER_USERNAME)
                .id();

        otherUserId = identityService
                .findByUsername(OTHER_USERNAME)
                .id();

        ownerWorkId = createWork(
                ownerId,
                "Owner Work " + UUID.randomUUID()
        );

        otherWorkId = createWork(
                otherUserId,
                "Other Work " + UUID.randomUUID()
        );
    }

    @Test
    void shouldCreateInstallmentForWorkOwner()
            throws Exception {

        MockHttpSession session =
                login(OWNER_USERNAME);

        mockMvc.perform(
                        post(
                                "/api/works/{workId}/installments",
                                ownerWorkId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "type": "CHAPTER",
                                          "number": 1,
                                          "title": "  The Beginning  "
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM installment
                WHERE work_id = ?
                  AND installment_type = ?
                  AND number = ?
                """,
                Integer.class,
                ownerWorkId,
                "CHAPTER",
                1
        );

        assertThat(count).isEqualTo(1);

        String title = jdbcTemplate.queryForObject(
                """
                SELECT title
                FROM installment
                WHERE work_id = ?
                  AND installment_type = ?
                  AND number = ?
                """,
                String.class,
                ownerWorkId,
                "CHAPTER",
                1
        );

        assertThat(title)
                .isEqualTo("The Beginning");

        String status = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM installment
                WHERE work_id = ?
                  AND installment_type = ?
                  AND number = ?
                """,
                String.class,
                ownerWorkId,
                "CHAPTER",
                1
        );

        assertThat(status)
                .isEqualTo("DRAFT");
    }

    @Test
    void shouldRejectUnauthenticatedUser()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/works/{workId}/installments",
                                ownerWorkId
                        )
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "type": "CHAPTER",
                                          "number": 1,
                                          "title": "The Beginning"
                                        }
                                        """)
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
    void shouldRejectUserWhoDoesNotOwnWork()
            throws Exception {

        MockHttpSession session =
                login(OWNER_USERNAME);

        mockMvc.perform(
                        post(
                                "/api/works/{workId}/installments",
                                otherWorkId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "type": "CHAPTER",
                                          "number": 1,
                                          "title": "Not My Work"
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
                                .value("Access denied")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "You do not have permission to modify this work."
                                )
                );
    }

    @Test
    void shouldRejectDuplicateInstallment()
            throws Exception {

        MockHttpSession session =
                login(OWNER_USERNAME);

        String requestBody = """
                {
                  "type": "ISSUE",
                  "number": 1,
                  "title": null
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/works/{workId}/installments",
                                ownerWorkId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post(
                                "/api/works/{workId}/installments",
                                ownerWorkId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
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
                                        "Installment already exists"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                );
    }

    private MockHttpSession login(String username)
            throws Exception {

        MvcResult loginResult = mockMvc.perform(
                        formLogin("/api/auth/login")
                                .user(username)
                                .password(PASSWORD)
                )
                .andExpect(status().isOk())
                .andExpect(
                        authenticated()
                                .withUsername(username)
                )
                .andReturn();

        return (MockHttpSession) loginResult
                .getRequest()
                .getSession(false);
    }

    private void ensureUserExists(
            String username,
            String email,
            String displayName
    ) {
        try {
            identityService.createUser(
                    new CreateUserCommand(
                            username,
                            email,
                            displayName,
                            PASSWORD
                    )
            );
        } catch (
                UsernameAlreadyExistsException
                | EmailAlreadyExistsException ignored
        ) {
            // User already exists from another test execution.
        }
    }

    private UUID createWork(
            UUID ownerId,
            String title
    ) {
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
}