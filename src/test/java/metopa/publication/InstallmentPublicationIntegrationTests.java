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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void shouldUpdateDraftInstallmentMetadata()
            throws Exception {

        TestUser owner =
                createUser("update-owner");

        UUID workId =
                createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Original Title"
                        )
                );

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        put(
                                "/api/installments/{installmentId}",
                                installmentId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "type": "ISSUE",
                                      "number": 2,
                                      "title": "  Updated Title  "
                                    }
                                    """)
                )
                .andExpect(
                        status().isNoContent()
                );

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            installment_type,
                            number,
                            title,
                            status
                        FROM installment
                        WHERE id = ?
                        """,
                        installmentId
                );

        assertThat(
                row.get("installment_type")
        ).isEqualTo("ISSUE");

        assertThat(
                row.get("number")
        ).isEqualTo(2);

        assertThat(
                row.get("title")
        ).isEqualTo("Updated Title");

        assertThat(
                row.get("status")
        ).isEqualTo("DRAFT");
    }

    @Test
    void shouldRejectMetadataUpdateForPublishedInstallment()
            throws Exception {

        TestUser owner =
                createUser("update-published");

        UUID workId =
                createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Published Title"
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

        publicationService.publishInstallment(
                owner.id(),
                installmentId
        );

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        put(
                                "/api/installments/{installmentId}",
                                installmentId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "type": "ISSUE",
                                      "number": 2,
                                      "title": "Updated Title"
                                    }
                                    """)
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Published installment cannot be modified"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Published installments cannot be modified."
                                )
                );

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            installment_type,
                            number,
                            title,
                            status
                        FROM installment
                        WHERE id = ?
                        """,
                        installmentId
                );

        assertThat(
                row.get("installment_type")
        ).isEqualTo("CHAPTER");

        assertThat(
                row.get("number")
        ).isEqualTo(1);

        assertThat(
                row.get("title")
        ).isEqualTo("Published Title");

        assertThat(
                row.get("status")
        ).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldListDraftAndPublishedInstallmentsForWorkOwner()
            throws Exception {

        TestUser owner =
                createUser("creator-list-owner");

        UUID workId =
                createWork(owner.id());

        UUID publishedInstallmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Chapter One"
                        )
                );

        publicationService.createPage(
                owner.id(),
                new CreatePageCommand(
                        publishedInstallmentId,
                        1,
                        "image/jpeg",
                        "page-content".getBytes()
                )
        );

        publicationService.publishInstallment(
                owner.id(),
                publishedInstallmentId
        );

        UUID draftInstallmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                2,
                                "Chapter Two"
                        )
                );

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}/installments",
                                workId
                        )
                                .session(session)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(
                                        publishedInstallmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$[0].type")
                                .value("CHAPTER")
                )
                .andExpect(
                        jsonPath("$[0].number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].title")
                                .value("Chapter One")
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("PUBLISHED")
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(
                                        draftInstallmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$[1].type")
                                .value("CHAPTER")
                )
                .andExpect(
                        jsonPath("$[1].number")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[1].title")
                                .value("Chapter Two")
                )
                .andExpect(
                        jsonPath("$[1].status")
                                .value("DRAFT")
                );
    }

    @Test
    void shouldRejectCreatorInstallmentListForUserWhoDoesNotOwnWork()
            throws Exception {

        TestUser owner =
                createUser("creator-list-owner");

        TestUser anotherUser =
                createUser("creator-list-other");

        UUID workId =
                createWork(owner.id());

        publicationService.createInstallment(
                owner.id(),
                new CreateInstallmentCommand(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Private Draft"
                )
        );

        MockHttpSession session =
                login(anotherUser.username());

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}/installments",
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
                                .value("Access denied")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );
    }

    @Test
    void shouldReturnDraftInstallmentDetailForWorkOwner()
            throws Exception {

        TestUser owner =
                createUser("creator-detail-owner");

        UUID workId =
                createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Chapter One"
                        )
                );

        UUID pageId =
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
                        get(
                                "/api/me/installments/{installmentId}",
                                installmentId
                        )
                                .session(session)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        installmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.workId")
                                .value(
                                        workId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("CHAPTER")
                )
                .andExpect(
                        jsonPath("$.number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Chapter One")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("DRAFT")
                )
                .andExpect(
                        jsonPath("$.pages.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.pages[0].id")
                                .value(
                                        pageId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.pages[0].number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.pages[0].contentType")
                                .value("image/jpeg")
                );
    }

    @Test
    void shouldRejectCreatorInstallmentDetailForUserWhoDoesNotOwnWork()
            throws Exception {

        TestUser owner =
                createUser("creator-detail-owner");

        TestUser anotherUser =
                createUser("creator-detail-other");

        UUID workId =
                createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Private Draft"
                        )
                );

        publicationService.createPage(
                owner.id(),
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "private-page".getBytes()
                )
        );

        MockHttpSession session =
                login(anotherUser.username());

        mockMvc.perform(
                        get(
                                "/api/me/installments/{installmentId}",
                                installmentId
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
                                .value("Access denied")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );
    }

    @Test
    void shouldReturnDraftPageContentForWorkOwner()
            throws Exception {

        TestUser owner =
                createUser("creator-page-owner");

        UUID workId =
                createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Chapter One"
                        )
                );

        byte[] pageContent =
                "draft-page-content".getBytes();

        UUID pageId =
                publicationService.createPage(
                        owner.id(),
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                pageContent
                        )
                );

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        get(
                                "/api/me/pages/{pageId}/content",
                                pageId
                        )
                                .session(session)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        content().contentType(
                                MediaType.IMAGE_JPEG
                        )
                )
                .andExpect(
                        content().bytes(
                                pageContent
                        )
                );
    }

    @Test
    void shouldRejectCreatorPageContentForUserWhoDoesNotOwnWork()
            throws Exception {

        TestUser owner =
                createUser("creator-page-owner");

        TestUser anotherUser =
                createUser("creator-page-other");

        UUID workId =
                createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Private Draft"
                        )
                );

        UUID pageId =
                publicationService.createPage(
                        owner.id(),
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "private-page-content".getBytes()
                        )
                );

        MockHttpSession session =
                login(anotherUser.username());

        mockMvc.perform(
                        get(
                                "/api/me/pages/{pageId}/content",
                                pageId
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
                                .value("Access denied")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );
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