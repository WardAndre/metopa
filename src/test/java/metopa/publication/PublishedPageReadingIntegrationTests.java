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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-published-page-storage"
        }
)
@AutoConfigureMockMvc
class PublishedPageReadingIntegrationTests {

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
    void shouldReadPublishedPageWithoutAuthentication()
            throws Exception {

        TestUser owner =
                createUser("page-reader");

        UUID installmentId =
                createInstallment(owner.id());

        byte[] pageContent =
                "published-page-content".getBytes();

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

        publicationService.publishInstallment(
                owner.id(),
                installmentId
        );

        mockMvc.perform(
                        get(
                                "/api/pages/{pageId}/content",
                                pageId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentType(
                                MediaType.IMAGE_JPEG
                        )
                )
                .andExpect(
                        content().bytes(pageContent)
                );
    }

    @Test
    void shouldHidePageFromDraftInstallment()
            throws Exception {

        TestUser owner =
                createUser("draft-page-reader");

        UUID installmentId =
                createInstallment(owner.id());

        UUID pageId =
                publicationService.createPage(
                        owner.id(),
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/png",
                                "draft-page-content".getBytes()
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/pages/{pageId}/content",
                                pageId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Published page not found"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The requested published page could not be found."
                                )
                );
    }

    @Test
    void shouldReturnNotFoundForUnknownPage()
            throws Exception {

        UUID pageId =
                UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/pages/{pageId}/content",
                                pageId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Published page not found"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                );
    }

    private UUID createInstallment(UUID ownerId) {
        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Page Reading Work "
                                        + UUID.randomUUID(),
                                null,
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        return publicationService.createInstallment(
                ownerId,
                new CreateInstallmentCommand(
                        workId,
                        InstallmentType.ISSUE,
                        1,
                        null
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
                        "Page Reading Test User",
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

    private record TestUser(
            UUID id,
            String username
    ) {
    }
}