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
                "metopa.storage.root=target/test-reading-storage"
        }
)
@AutoConfigureMockMvc
class PublishedInstallmentReadingIntegrationTests {

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
    void shouldReadPublishedInstallmentWithoutAuthentication()
            throws Exception {

        TestUser owner =
                createUser("reader-published");

        UUID workId =
                createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "The Beginning"
                        )
                );

        publicationService.createPage(
                owner.id(),
                new CreatePageCommand(
                        installmentId,
                        2,
                        "image/webp",
                        "page-two".getBytes()
                )
        );

        publicationService.createPage(
                owner.id(),
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "page-one".getBytes()
                )
        );

        publicationService.publishInstallment(
                owner.id(),
                installmentId
        );

        mockMvc.perform(
                        get(
                                "/api/installments/{installmentId}",
                                installmentId
                        )
                )
                .andExpect(status().isOk())
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
                                .value("The Beginning")
                )
                .andExpect(
                        jsonPath("$.pages.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.pages[0].number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.pages[0].contentType")
                                .value("image/jpeg")
                )
                .andExpect(
                        jsonPath("$.pages[1].number")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.pages[1].contentType")
                                .value("image/webp")
                );
    }

    @Test
    void shouldHideDraftInstallmentFromPublicReading()
            throws Exception {

        TestUser owner =
                createUser("reader-draft");

        UUID workId =
                createWork(owner.id());

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.ISSUE,
                                1,
                                null
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/installments/{installmentId}",
                                installmentId
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
                                        "Published installment not found"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The requested published installment could not be found."
                                )
                );
    }

    @Test
    void shouldReturnNotFoundForUnknownInstallment()
            throws Exception {

        UUID installmentId =
                UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/installments/{installmentId}",
                                installmentId
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
                                        "Published installment not found"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                );
    }

    private UUID createWork(UUID ownerId) {
        return catalogService.createWork(
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
                        "Reading Test User",
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