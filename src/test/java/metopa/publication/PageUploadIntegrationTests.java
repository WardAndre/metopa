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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-page-storage"
        }
)
@AutoConfigureMockMvc
class PageUploadIntegrationTests {

    private static final String USERNAME =
            "pageauthor";

    private static final String PASSWORD =
            "StrongPassword123!";

    private static final Path STORAGE_ROOT =
            Path.of("target/test-page-storage");

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

    private UUID userId;
    private UUID installmentId;

    @BeforeEach
    void setUp() throws Exception {
        clearStorage();

        ensureUserExists();

        userId = identityService
                .findByUsername(USERNAME)
                .id();

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                userId,
                                "Page Upload Work "
                                        + UUID.randomUUID(),
                                null,
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        installmentId =
                publicationService.createInstallment(
                        userId,
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.ISSUE,
                                1,
                                null
                        )
                );
    }

    @Test
    void shouldUploadAndPersistPage()
            throws Exception {

        MockHttpSession session =
                login();

        byte[] content =
                "page-image-content".getBytes();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "page.jpg",
                        MediaType.IMAGE_JPEG_VALUE,
                        content
                );

        mockMvc.perform(
                        multipart(
                                "/api/installments/{installmentId}/pages",
                                installmentId
                        )
                                .file(file)
                                .param("number", "1")
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        String storageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT storage_key
                        FROM installment_page
                        WHERE installment_id = ?
                          AND page_number = ?
                        """,
                        String.class,
                        installmentId,
                        1
                );

        String contentType =
                jdbcTemplate.queryForObject(
                        """
                        SELECT content_type
                        FROM installment_page
                        WHERE installment_id = ?
                          AND page_number = ?
                        """,
                        String.class,
                        installmentId,
                        1
                );

        assertThat(storageKey).isNotBlank();
        assertThat(contentType)
                .isEqualTo("image/jpeg");

        Path storedFile =
                STORAGE_ROOT
                        .resolve(storageKey)
                        .normalize();

        assertThat(storedFile)
                .exists();

        assertThat(
                Files.readAllBytes(storedFile)
        ).isEqualTo(content);
    }

    private MockHttpSession login()
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                formLogin(
                                        "/api/auth/login"
                                )
                                        .user(USERNAME)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(
                                                USERNAME
                                        )
                        )
                        .andReturn();

        return (MockHttpSession) result
                .getRequest()
                .getSession(false);
    }

    private void ensureUserExists() {
        try {
            identityService.createUser(
                    new CreateUserCommand(
                            USERNAME,
                            "pageauthor@example.com",
                            "Page Author",
                            PASSWORD
                    )
            );
        } catch (
                UsernameAlreadyExistsException
                | EmailAlreadyExistsException ignored
        ) {
            // User already exists from a previous execution.
        }
    }

    private void clearStorage()
            throws Exception {

        if (!Files.exists(STORAGE_ROOT)) {
            return;
        }

        try (var paths = Files.walk(STORAGE_ROOT)) {
            paths.sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception exception) {
                            throw new RuntimeException(
                                    exception
                            );
                        }
                    });
        }
    }
}