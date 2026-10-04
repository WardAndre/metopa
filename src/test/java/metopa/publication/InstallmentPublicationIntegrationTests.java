package metopa.publication;

import metopa.catalog.CatalogService;
import metopa.catalog.CreateWorkCommand;
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
                "metopa.storage.root=target/test-publication-storage"
        }
)
@AutoConfigureMockMvc
class InstallmentPublicationIntegrationTests {

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
    void shouldPublishInstallmentWithPage()
            throws Exception {

        TestUser owner = createUser("publish-owner");

        UUID workId = createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                null
                        )
                );

        publicationService.createPage(
                owner.id(),
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "page-content".getBytes()
                )
        );

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        post(
                                "/api/installments/{installmentId}/publish",
                                installmentId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        String status =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM installment
                        WHERE id = ?
                        """,
                        String.class,
                        installmentId
                );

        assertThat(status)
                .isEqualTo("PUBLISHED");
    }

    @Test
    void shouldRejectUnauthenticatedPublication()
            throws Exception {

        TestUser owner = createUser("publish-unauth");

        UUID installmentId =
                createInstallment(owner.id());

        mockMvc.perform(
                        post(
                                "/api/installments/{installmentId}/publish",
                                installmentId
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
    void shouldRejectPublicationByUserWhoDoesNotOwnWork()
            throws Exception {

        TestUser owner = createUser("publish-owner");
        TestUser anotherUser =
                createUser("publish-other");

        UUID installmentId =
                createInstallment(owner.id());

        MockHttpSession session =
                login(anotherUser.username());

        mockMvc.perform(
                        post(
                                "/api/installments/{installmentId}/publish",
                                installmentId
                        )
                                .session(session)
                                .with(csrf())
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
                );
    }

    @Test
    void shouldRejectPublishingInstallmentWithoutPages()
            throws Exception {

        TestUser owner =
                createUser("publish-empty");

        UUID installmentId =
                createInstallment(owner.id());

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        post(
                                "/api/installments/{installmentId}/publish",
                                installmentId
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
                                        "Installment cannot be published"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "An installment must contain at least one page before publication."
                                )
                );

        String status =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM installment
                        WHERE id = ?
                        """,
                        String.class,
                        installmentId
                );

        assertThat(status)
                .isEqualTo("DRAFT");
    }

    private UUID createInstallment(UUID ownerId) {
        UUID workId = createWork(ownerId);

        return publicationService.createInstallment(
                ownerId,
                new CreateInstallmentCommand(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                )
        );
    }

    private UUID createWork(UUID ownerId) {
        return catalogService.createWork(
                new CreateWorkCommand(
                        ownerId,
                        "Publication Work "
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

        String email =
                username + "@example.com";

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        email,
                        "Publication Test User",
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