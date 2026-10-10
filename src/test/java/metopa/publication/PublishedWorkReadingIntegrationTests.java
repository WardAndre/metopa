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

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-published-work-storage"
        }
)
@AutoConfigureMockMvc
class PublishedWorkReadingIntegrationTests {

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

    private MixedPublicationWork
    createWorkWithPublishedAndDraftInstallments(
            UUID ownerId
    ) {
        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Mixed Publication Work",
                                "Contains published and draft content.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        UUID publishedInstallmentId =
                publicationService.createInstallment(
                        ownerId,
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.ISSUE,
                                1,
                                "Published Issue"
                        )
                );

        publicationService.createPage(
                ownerId,
                new CreatePageCommand(
                        publishedInstallmentId,
                        1,
                        "image/jpeg",
                        "published-page".getBytes()
                )
        );

        publicationService.publishInstallment(
                ownerId,
                publishedInstallmentId
        );

        UUID draftInstallmentId =
                publicationService.createInstallment(
                        ownerId,
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.ISSUE,
                                2,
                                "Draft Issue"
                        )
                );

        publicationService.createPage(
                ownerId,
                new CreatePageCommand(
                        draftInstallmentId,
                        1,
                        "image/jpeg",
                        "draft-page".getBytes()
                )
        );

        return new MixedPublicationWork(
                workId,
                publishedInstallmentId,
                draftInstallmentId
        );
    }

    @Test
    void shouldReturnPublishedWorkWithoutAuthentication()
            throws Exception {

        UUID ownerId =
                createUser("published-work");

        UUID workId =
                createPublishedWork(
                        ownerId,
                        "Metopa Origins"
                );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}",
                                workId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(workId.toString())
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Metopa Origins")
                )
                .andExpect(
                        jsonPath("$.description")
                                .value(
                                        "A published graphic novel."
                                )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("GRAPHIC_NOVEL")
                )
                .andExpect(
                        jsonPath("$.readingDirection")
                                .value("LEFT_TO_RIGHT")
                )
                .andExpect(
                        jsonPath("$.presentationMode")
                                .value("SINGLE_PAGE")
                );
    }

    @Test
    void shouldHideWorkWithOnlyDraftContent()
            throws Exception {

        UUID ownerId =
                createUser("draft-work");

        UUID workId =
                createDraftWork(
                        ownerId
                );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}",
                                workId
                        )
                )
                .andExpect(status().isNotFound())
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
                                        "The requested work is not available."
                                )
                );
    }

    private UUID createPublishedWork(
            UUID ownerId,
            String title
    ) {
        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                title,
                                "A published graphic novel.",
                                WorkType.GRAPHIC_NOVEL,
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
                        "published-work-page".getBytes()
                )
        );

        publicationService.publishInstallment(
                ownerId,
                installmentId
        );

        return workId;
    }

    private UUID createDraftWork(
            UUID ownerId
    ) {
        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                ownerId,
                                "Draft Work",
                                "This work must remain private.",
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
                        "draft-work-page".getBytes()
                )
        );

        return workId;
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
                        "Published Work Test User",
                        PASSWORD
                )
        );

        UserReference user =
                identityService.findByUsername(
                        username
                );

        return user.id();
    }

    @Test
    void shouldReturnOnlyPublishedInstallments()
            throws Exception {

        UUID ownerId =
                createUser("mixed-work");

        MixedPublicationWork work =
                createWorkWithPublishedAndDraftInstallments(
                        ownerId
                );

        mockMvc.perform(
                        get(
                                "/api/works/{workId}",
                                work.workId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        work.workId().toString()
                                )
                )
                .andExpect(
                        jsonPath("$.installments.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.installments[0].id")
                                .value(
                                        work.publishedInstallmentId()
                                                .toString()
                                )
                )
                .andExpect(
                        jsonPath("$.installments[0].type")
                                .value("ISSUE")
                )
                .andExpect(
                        jsonPath("$.installments[0].number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.installments[0].title")
                                .value("Published Issue")
                );
    }

    private record MixedPublicationWork(
            UUID workId,
            UUID publishedInstallmentId,
            UUID draftInstallmentId
    ) {
    }
}