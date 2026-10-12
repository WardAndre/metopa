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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-published-work-cover-storage"
        }
)
@AutoConfigureMockMvc
class PublishedWorkCoverIntegrationTests {

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

    @Test
    void shouldReturnPublishedWorkCoverWithoutAuthentication()
            throws Exception {

        TestUser owner =
                createUser("public-cover");

        UUID workId =
                createWork(owner.id());

        byte[] coverContent =
                "published-cover-content".getBytes();

        uploadCover(
                owner,
                workId,
                coverContent
        );

        publishWork(
                owner.id(),
                workId
        );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}/cover",
                                workId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentType(
                                "image/webp"
                        )
                )
                .andExpect(
                        content().bytes(
                                coverContent
                        )
                );
    }

    @Test
    void shouldHideCoverForWorkWithOnlyDraftContent()
            throws Exception {

        TestUser owner =
                createUser("draft-cover");

        UUID workId =
                createWork(owner.id());

        byte[] coverContent =
                "draft-cover-content".getBytes();

        uploadCover(
                owner,
                workId,
                coverContent
        );

        createDraftInstallment(
                owner.id(),
                workId
        );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}/cover",
                                workId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Work cover not available"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The requested work cover is not available."
                                )
                );
    }

    private void uploadCover(
            TestUser owner,
            UUID workId,
            byte[] content
    ) throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        content
                );

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        multipart(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .file(file)
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());
    }

    private void publishWork(
            UUID ownerId,
            UUID workId
    ) {
        UUID installmentId =
                createDraftInstallment(
                        ownerId,
                        workId
                );

        publicationService.publishInstallment(
                ownerId,
                installmentId
        );
    }

    private UUID createDraftInstallment(
            UUID ownerId,
            UUID workId
    ) {
        UUID installmentId =
                publicationService.createInstallment(
                        ownerId,
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.ISSUE,
                                1,
                                "Issue One"
                        )
                );

        publicationService.createPage(
                ownerId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/webp",
                        "page-content".getBytes()
                )
        );

        return installmentId;
    }

    private UUID createWork(UUID ownerId) {
        return catalogService.createWork(
                new CreateWorkCommand(
                        ownerId,
                        "Cover Reading Work "
                                + UUID.randomUUID(),
                        "A work with a public cover.",
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                )
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
                        "Cover Reading User",
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