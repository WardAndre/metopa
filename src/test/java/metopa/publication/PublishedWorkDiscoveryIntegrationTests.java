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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-work-discovery-storage"
        }
)
@AutoConfigureMockMvc
class PublishedWorkDiscoveryIntegrationTests {

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
    void shouldReturnOnlyPublishedWorksWithoutDuplicates()
            throws Exception {

        UUID ownerId =
                createUser("discovery-user");

        UUID firstPublishedWorkId =
                createPublishedWork(
                        ownerId,
                        "First Published Work",
                        2
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

        mockMvc.perform(
                        get("/api/works")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$[?(@.id == '" +
                                        firstPublishedWorkId +
                                        "')]"
                        ).value(hasSize(1))
                )
                .andExpect(
                        jsonPath(
                                "$[?(@.id == '" +
                                        secondPublishedWorkId +
                                        "')]"
                        ).value(hasSize(1))
                )
                .andExpect(
                        jsonPath(
                                "$[?(@.id == '" +
                                        draftWorkId +
                                        "')]"
                        ).value(hasSize(0))
                );
    }

    private UUID createPublishedWork(
            UUID ownerId,
            String title,
            int installmentCount
    ) {
        UUID workId =
                createWork(
                        ownerId,
                        title
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
        UUID workId =
                createWork(
                        ownerId,
                        title
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

    private UUID createUser(
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