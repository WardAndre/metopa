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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-library-removal-storage"
        }
)
@AutoConfigureMockMvc
class LibraryRemovalIntegrationTests {

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
    private LibraryService libraryService;

    @Test
    void shouldRemoveWorkAndRemainIdempotent()
            throws Exception {

        TestUser user =
                createUser("library-remove");

        UUID workId =
                createPublishedWork(
                        user.id()
                );

        libraryService.addWork(
                user.id(),
                workId
        );

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        get("/api/library")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].workId")
                                .value(
                                        workId.toString()
                                )
                );

        mockMvc.perform(
                        delete(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/library")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(0)
                );

        mockMvc.perform(
                        delete(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectUnauthenticatedLibraryRemoval()
            throws Exception {

        UUID workId = UUID.randomUUID();

        mockMvc.perform(
                        delete(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized())
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

    private UUID createPublishedWork(
            UUID ownerId
    ) {
        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Library Removal Work "
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

        publicationService.createPage(
                ownerId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "library-removal-page"
                                .getBytes()
                )
        );

        publicationService.publishInstallment(
                ownerId,
                installmentId
        );

        return workId;
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
                        "Library Removal User",
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
}