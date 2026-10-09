package metopa.reading;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-reading-storage"
        }
)
@AutoConfigureMockMvc
class ReadingIntegrationTests {

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

    private UUID createDraftPage(
            UUID ownerId
    ) {
        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Draft Reading Work "
                                        + UUID.randomUUID(),
                                null,
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        UUID installmentId =
                publicationService.createInstallment(
                        ownerId,
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.ISSUE,
                                1,
                                null
                        )
                );

        return publicationService.createPage(
                ownerId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "draft-reading-page"
                                .getBytes()
                )
        );
    }

    @Test
    void shouldCreateAndUpdateReadingProgress()
            throws Exception {

        TestUser user =
                createUser("reading-user");

        PublishedWork work =
                createPublishedWork(
                        user.id()
                );

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        put(
                                "/api/reading/progress/pages/{pageId}",
                                work.firstPageId()
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        MvcResult firstResult =
                mockMvc.perform(
                                get(
                                        "/api/reading/progress/works/{workId}",
                                        work.workId()
                                )
                                        .session(session)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.workId")
                                        .value(
                                                work.workId().toString()
                                        )
                        )
                        .andExpect(
                                jsonPath("$.installmentId")
                                        .value(
                                                work.installmentId()
                                                        .toString()
                                        )
                        )
                        .andExpect(
                                jsonPath("$.pageId")
                                        .value(
                                                work.firstPageId()
                                                        .toString()
                                        )
                        )
                        .andReturn();

        String firstProgressId =
                JsonPath.read(
                        firstResult
                                .getResponse()
                                .getContentAsString(),
                        "$.id"
                );

        mockMvc.perform(
                        put(
                                "/api/reading/progress/pages/{pageId}",
                                work.secondPageId()
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        MvcResult secondResult =
                mockMvc.perform(
                                get(
                                        "/api/reading/progress/works/{workId}",
                                        work.workId()
                                )
                                        .session(session)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.pageId")
                                        .value(
                                                work.secondPageId()
                                                        .toString()
                                        )
                        )
                        .andReturn();

        String secondProgressId =
                JsonPath.read(
                        secondResult
                                .getResponse()
                                .getContentAsString(),
                        "$.id"
                );

        assertThat(secondProgressId)
                .isEqualTo(firstProgressId);
    }

    @Test
    void shouldRejectUnauthenticatedReadingProgressAccess()
            throws Exception {

        UUID workId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/reading/progress/works/{workId}",
                                workId
                        )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireCsrfWhenSavingReadingProgress()
            throws Exception {

        TestUser user =
                createUser("reading-csrf");

        PublishedWork work =
                createPublishedWork(
                        user.id()
                );

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        put(
                                "/api/reading/progress/pages/{pageId}",
                                work.firstPageId()
                        )
                                .session(session)
                )
                .andExpect(status().isForbidden());
    }

    private PublishedWork createPublishedWork(
            UUID ownerId
    ) {
        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Reading Work "
                                        + UUID.randomUUID(),
                                null,
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        UUID installmentId =
                publicationService.createInstallment(
                        ownerId,
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.ISSUE,
                                1,
                                null
                        )
                );

        UUID firstPageId =
                publicationService.createPage(
                        ownerId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "reading-page-1"
                                        .getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        ownerId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "reading-page-2"
                                        .getBytes()
                        )
                );

        publicationService.publishInstallment(
                ownerId,
                installmentId
        );

        return new PublishedWork(
                workId,
                installmentId,
                firstPageId,
                secondPageId
        );
    }

    private TestUser createUser(
            String prefix
    ) {
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
                        "Reading Test User",
                        PASSWORD
                )
        );

        UserReference user =
                identityService.findByUsername(
                        username
                );

        return new TestUser(
                user.id(),
                user.username()
        );
    }

    private MockHttpSession login(
            String username
    ) throws Exception {

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

    private record PublishedWork(
            UUID workId,
            UUID installmentId,
            UUID firstPageId,
            UUID secondPageId
    ) {
    }

    @Test
    void shouldReturnNotFoundWhenSavingProgressForUnknownPage()
            throws Exception {

        TestUser user =
                createUser("reading-unknown-page");

        UUID pageId = UUID.randomUUID();

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        put(
                                "/api/reading/progress/pages/{pageId}",
                                pageId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value("Page not available")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The requested page is not available for reading progress."
                                )
                );
    }

    @Test
    void shouldReturnNotFoundWhenSavingProgressForDraftPage()
            throws Exception {

        TestUser user =
                createUser("reading-draft-page");

        UUID pageId =
                createDraftPage(
                        user.id()
                );

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        put(
                                "/api/reading/progress/pages/{pageId}",
                                pageId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value("Page not available")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The requested page is not available for reading progress."
                                )
                );
    }
}