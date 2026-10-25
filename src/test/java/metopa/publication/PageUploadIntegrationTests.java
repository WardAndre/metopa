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
import metopa.publication.internal.InstallmentRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private InstallmentRepository installmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Test
    void shouldDeleteDraftPageAndStoredContent()
            throws Exception {

        byte[] content =
                "page-to-delete".getBytes();

        UUID pageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                content
                        )
                );

        String storageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT storage_key
                        FROM installment_page
                        WHERE id = ?
                        """,
                        String.class,
                        pageId
                );

        Path storedFile =
                STORAGE_ROOT
                        .resolve(storageKey)
                        .normalize();

        assertThat(storedFile)
                .exists();

        assertThat(
                Files.readAllBytes(storedFile)
        ).isEqualTo(content);

        MockHttpSession session =
                login();

        mockMvc.perform(
                        delete(
                                "/api/installments/{installmentId}/pages/{pageId}",
                                installmentId,
                                pageId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(
                        status().isNoContent()
                );

        Integer remainingPages =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment_page
                        WHERE id = ?
                        """,
                        Integer.class,
                        pageId
                );

        assertThat(remainingPages)
                .isZero();

        assertThat(storedFile)
                .doesNotExist();
    }

    @Test
    void shouldRejectDeletingPageFromPublishedInstallment()
            throws Exception {

        byte[] content =
                "published-page-content".getBytes();

        UUID pageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                content
                        )
                );

        String storageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT storage_key
                        FROM installment_page
                        WHERE id = ?
                        """,
                        String.class,
                        pageId
                );

        Path storedFile =
                STORAGE_ROOT
                        .resolve(storageKey)
                        .normalize();

        publicationService.publishInstallment(
                userId,
                installmentId
        );

        MockHttpSession session =
                login();

        mockMvc.perform(
                        delete(
                                "/api/installments/{installmentId}/pages/{pageId}",
                                installmentId,
                                pageId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(
                        status().isConflict()
                );

        Integer remainingPages =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment_page
                        WHERE id = ?
                        """,
                        Integer.class,
                        pageId
                );

        assertThat(remainingPages)
                .isEqualTo(1);

        assertThat(storedFile)
                .exists();

        assertThat(
                Files.readAllBytes(storedFile)
        ).isEqualTo(content);
    }

    @Test
    void shouldPersistReorderedPagesWithoutUniqueConstraintViolation() {
        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        UUID thirdPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                3,
                                "image/jpeg",
                                "third-page".getBytes()
                        )
                );

        publicationService.reorderPages(
                userId,
                installmentId,
                new ReorderPagesCommand(
                        List.of(
                                thirdPageId,
                                firstPageId,
                                secondPageId
                        )
                )
        );

        assertThat(pageNumber(thirdPageId))
                .isEqualTo(1);

        assertThat(pageNumber(firstPageId))
                .isEqualTo(2);

        assertThat(pageNumber(secondPageId))
                .isEqualTo(3);
    }

    @Test
    void shouldReorderPagesWithNumberingGapsAfterDeletion() {
        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        UUID thirdPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                3,
                                "image/jpeg",
                                "third-page".getBytes()
                        )
                );

        publicationService.deletePage(
                userId,
                installmentId,
                secondPageId
        );

        assertThat(pageNumber(firstPageId))
                .isEqualTo(1);

        assertThat(pageNumber(thirdPageId))
                .isEqualTo(3);

        publicationService.reorderPages(
                userId,
                installmentId,
                new ReorderPagesCommand(
                        List.of(
                                thirdPageId,
                                firstPageId
                        )
                )
        );

        assertThat(pageNumber(thirdPageId))
                .isEqualTo(1);

        assertThat(pageNumber(firstPageId))
                .isEqualTo(2);
    }

    @Test
    void shouldPreservePersistedPageNumbersWhenOrderIsInvalid() {
        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        assertThatThrownBy(() ->
                publicationService.reorderPages(
                        userId,
                        installmentId,
                        new ReorderPagesCommand(
                                List.of(
                                        firstPageId,
                                        firstPageId
                                )
                        )
                )
        ).isInstanceOf(
                InvalidPageOrderException.class
        );

        assertThat(pageNumber(firstPageId))
                .isEqualTo(1);

        assertThat(pageNumber(secondPageId))
                .isEqualTo(2);
    }

    private Integer pageNumber(UUID pageId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT page_number
                FROM installment_page
                WHERE id = ?
                """,
                Integer.class,
                pageId
        );
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

    @Test
    void shouldReorderPagesThroughAuthenticatedHttpRequest()
            throws Exception {

        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        UUID thirdPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                3,
                                "image/jpeg",
                                "third-page".getBytes()
                        )
                );

        MockHttpSession session = login();

        String requestBody = """
            {
              "pageIds": [
                "%s",
                "%s",
                "%s"
              ]
            }
            """.formatted(
                thirdPageId,
                firstPageId,
                secondPageId
        );

        mockMvc.perform(
                        put(
                                "/api/installments/{installmentId}/pages/order",
                                installmentId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNoContent());

        assertThat(pageNumber(thirdPageId))
                .isEqualTo(1);

        assertThat(pageNumber(firstPageId))
                .isEqualTo(2);

        assertThat(pageNumber(secondPageId))
                .isEqualTo(3);
    }

    @Test
    void shouldRejectPageReorderingWithoutCsrf()
            throws Exception {

        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        MockHttpSession session = login();

        String requestBody = """
            {
              "pageIds": [
                "%s",
                "%s"
              ]
            }
            """.formatted(
                secondPageId,
                firstPageId
        );

        mockMvc.perform(
                        put(
                                "/api/installments/{installmentId}/pages/order",
                                installmentId
                        )
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isForbidden());

        assertThat(pageNumber(firstPageId))
                .isEqualTo(1);

        assertThat(pageNumber(secondPageId))
                .isEqualTo(2);
    }

    @Test
    void shouldRejectInvalidPageOrderThroughHttp()
            throws Exception {

        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        MockHttpSession session = login();

        String requestBody = """
            {
              "pageIds": [
                "%s",
                "%s"
              ]
            }
            """.formatted(
                firstPageId,
                firstPageId
        );

        mockMvc.perform(
                        put(
                                "/api/installments/{installmentId}/pages/order",
                                installmentId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest());

        assertThat(pageNumber(firstPageId))
                .isEqualTo(1);

        assertThat(pageNumber(secondPageId))
                .isEqualTo(2);
    }

    @Test
    void shouldRejectReorderingPublishedInstallmentThroughHttp()
            throws Exception {

        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        publicationService.publishInstallment(
                userId,
                installmentId
        );

        MockHttpSession session = login();

        String requestBody = """
            {
              "pageIds": [
                "%s",
                "%s"
              ]
            }
            """.formatted(
                secondPageId,
                firstPageId
        );

        mockMvc.perform(
                        put(
                                "/api/installments/{installmentId}/pages/order",
                                installmentId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict());

        assertThat(pageNumber(firstPageId))
                .isEqualTo(1);

        assertThat(pageNumber(secondPageId))
                .isEqualTo(2);
    }

    @Test
    void shouldWaitForPageReorderingBeforePublishingInstallment()
            throws Exception {

        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        CountDownLatch reorderingCompleted =
                new CountDownLatch(1);

        CountDownLatch allowReorderingCommit =
                new CountDownLatch(1);

        CountDownLatch publishingStarted =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> reordering = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(
                            status -> {
                                publicationService.reorderPages(
                                        userId,
                                        installmentId,
                                        new ReorderPagesCommand(
                                                List.of(
                                                        secondPageId,
                                                        firstPageId
                                                )
                                        )
                                );

                                reorderingCompleted.countDown();

                                try {
                                    if (!allowReorderingCommit.await(
                                            10,
                                            TimeUnit.SECONDS
                                    )) {
                                        throw new IllegalStateException(
                                                "Timed out waiting to commit reordering."
                                        );
                                    }
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();

                                    throw new IllegalStateException(
                                            "Reordering interrupted.",
                                            exception
                                    );
                                }
                            }
                    )
            );

            assertThat(
                    reorderingCompleted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            Future<?> publishing = executor.submit(() -> {
                publishingStarted.countDown();

                publicationService.publishInstallment(
                        userId,
                        installmentId
                );
            });

            assertThat(
                    publishingStarted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            assertThatThrownBy(() ->
                    publishing.get(
                            500,
                            TimeUnit.MILLISECONDS
                    )
            ).isInstanceOf(TimeoutException.class);

            allowReorderingCommit.countDown();

            reordering.get(10, TimeUnit.SECONDS);
            publishing.get(10, TimeUnit.SECONDS);

            assertThat(pageNumber(secondPageId))
                    .isEqualTo(1);

            assertThat(pageNumber(firstPageId))
                    .isEqualTo(2);

            assertThat(
                    installmentRepository
                            .findById(installmentId)
                            .orElseThrow()
                            .getStatus()
            ).isEqualTo(PublicationStatus.PUBLISHED);

        } finally {
            allowReorderingCommit.countDown();

            executor.shutdownNow();

            assertThat(
                    executor.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
        }
    }

    @Test
    void shouldDeleteDraftInstallmentWithPagesAndPhysicalFiles() {
        UUID firstPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "first-page".getBytes()
                        )
                );

        UUID secondPageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                2,
                                "image/jpeg",
                                "second-page".getBytes()
                        )
                );

        String firstStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT storage_key
                        FROM installment_page
                        WHERE id = ?
                        """,
                        String.class,
                        firstPageId
                );

        String secondStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT storage_key
                        FROM installment_page
                        WHERE id = ?
                        """,
                        String.class,
                        secondPageId
                );

        Path firstFile = STORAGE_ROOT.resolve(firstStorageKey);
        Path secondFile = STORAGE_ROOT.resolve(secondStorageKey);

        assertThat(Files.exists(firstFile)).isTrue();
        assertThat(Files.exists(secondFile)).isTrue();

        publicationService.deleteInstallment(
                userId,
                installmentId
        );

        Long installmentCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment
                        WHERE id = ?
                        """,
                        Long.class,
                        installmentId
                );

        Long pageCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment_page
                        WHERE installment_id = ?
                        """,
                        Long.class,
                        installmentId
                );

        assertThat(installmentCount).isZero();
        assertThat(pageCount).isZero();

        assertThat(Files.exists(firstFile)).isFalse();
        assertThat(Files.exists(secondFile)).isFalse();
    }

    @Test
    void shouldPreservePublishedInstallmentAndItsFilesWhenDeletionIsRejected() {
        UUID pageId =
                publicationService.createPage(
                        userId,
                        new CreatePageCommand(
                                installmentId,
                                1,
                                "image/jpeg",
                                "published-page".getBytes()
                        )
                );

        String storageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT storage_key
                        FROM installment_page
                        WHERE id = ?
                        """,
                        String.class,
                        pageId
                );

        Path physicalFile = STORAGE_ROOT.resolve(storageKey);

        publicationService.publishInstallment(
                userId,
                installmentId
        );

        assertThatThrownBy(() ->
                publicationService.deleteInstallment(
                        userId,
                        installmentId
                )
        ).isInstanceOf(
                PublishedInstallmentModificationException.class
        );

        Long installmentCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment
                        WHERE id = ?
                        """,
                        Long.class,
                        installmentId
                );

        assertThat(installmentCount).isEqualTo(1L);

        assertThat(pageNumber(pageId)).isEqualTo(1);

        assertThat(Files.exists(physicalFile)).isTrue();
    }

    @Test
    void shouldDeleteDraftInstallmentThroughHttp() throws Exception {
        mockMvc.perform(
                        delete("/api/installments/{installmentId}", installmentId)
                                .session(login())
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Long remainingInstallments = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM installment
                WHERE id = ?
                """,
                Long.class,
                installmentId
        );

        assertThat(remainingInstallments).isZero();
    }

    @Test
    void shouldRejectPublishedInstallmentDeletionThroughHttp()
            throws Exception {

        UUID pageId = publicationService.createPage(
                userId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "published-page".getBytes()
                )
        );

        publicationService.publishInstallment(
                userId,
                installmentId
        );

        mockMvc.perform(
                        delete("/api/installments/{installmentId}", installmentId)
                                .session(login())
                                .with(csrf())
                )
                .andExpect(status().isConflict());

        Long remainingInstallments = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM installment
                WHERE id = ?
                """,
                Long.class,
                installmentId
        );

        assertThat(remainingInstallments).isEqualTo(1L);
        assertThat(pageNumber(pageId)).isEqualTo(1);
    }

    @Test
    void shouldRequireAuthenticationToDeleteInstallment()
            throws Exception {

        mockMvc.perform(
                        delete("/api/installments/{installmentId}", installmentId)
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized());

        Long remainingInstallments =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment
                        WHERE id = ?
                        """,
                        Long.class,
                        installmentId
                );

        assertThat(remainingInstallments).isEqualTo(1L);
    }

    @Test
    void shouldRequireCsrfToDeleteInstallment()
            throws Exception {

        mockMvc.perform(
                        delete("/api/installments/{installmentId}", installmentId)
                                .session(login())
                )
                .andExpect(status().isForbidden());

        Long remainingInstallments =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment
                        WHERE id = ?
                        """,
                        Long.class,
                        installmentId
                );

        assertThat(remainingInstallments).isEqualTo(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownInstallment()
            throws Exception {

        UUID unknownInstallmentId = UUID.randomUUID();

        mockMvc.perform(
                        delete(
                                "/api/installments/{installmentId}",
                                unknownInstallmentId
                        )
                                .session(login())
                                .with(csrf())
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectPublishedInstallmentDeletionByNonOwnerThroughHttp()
            throws Exception {

        UUID pageId = publicationService.createPage(
                userId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "published-page".getBytes()
                )
        );

        publicationService.publishInstallment(
                userId,
                installmentId
        );

        // Create a second user who does not own the installment.
        String otherUsername = "other" +
                UUID.randomUUID().toString().substring(0, 8);

        String password = "StrongPassword123!";

        mockMvc.perform(
                        post("/api/users")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "username": "%s",
                              "email": "%s@example.com",
                              "displayName": "Other User",
                              "password": "%s"
                            }
                            """.formatted(
                                        otherUsername,
                                        otherUsername,
                                        password
                                ))
                )
                .andExpect(status().isCreated());

        // Authenticate as the second user.
        MockHttpSession otherSession = (MockHttpSession) mockMvc.perform(
                        formLogin("/api/auth/login")
                                .user(otherUsername)
                                .password(password)
                )
                .andExpect(authenticated().withUsername(otherUsername))
                .andReturn()
                .getRequest()
                .getSession(false);

        // The user must not learn the publication status.
        mockMvc.perform(
                        delete(
                                "/api/installments/{installmentId}",
                                installmentId
                        )
                                .session(otherSession)
                                .with(csrf())
                )
                .andExpect(status().isForbidden());

        // The installment and its page must remain unchanged.
        Long remainingInstallments = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM installment
                WHERE id = ?
                """,
                Long.class,
                installmentId
        );

        assertThat(remainingInstallments).isEqualTo(1L);
        assertThat(pageNumber(pageId)).isEqualTo(1);
    }

    @Test
    void shouldRejectConcurrentDeletionAfterPublication()
            throws Exception {

        UUID pageId = publicationService.createPage(
                userId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "concurrent-page".getBytes()
                )
        );

        String storageKey = jdbcTemplate.queryForObject(
                """
                SELECT storage_key
                FROM installment_page
                WHERE id = ?
                """,
                String.class,
                pageId
        );

        Path physicalFile = STORAGE_ROOT.resolve(storageKey);

        assertThat(Files.exists(physicalFile)).isTrue();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        CountDownLatch publicationPerformed =
                new CountDownLatch(1);

        CountDownLatch allowPublicationCommit =
                new CountDownLatch(1);

        CountDownLatch deletionAttemptStarted =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> publicationFuture = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        publicationService.publishInstallment(
                                userId,
                                installmentId
                        );

                        publicationPerformed.countDown();

                        try {
                            if (!allowPublicationCommit.await(
                                    15,
                                    TimeUnit.SECONDS
                            )) {
                                throw new AssertionError(
                                        "Timed out waiting to commit publication"
                                );
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    })
            );

            assertThat(
                    publicationPerformed.await(
                            15,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            Future<?> deletionFuture = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        deletionAttemptStarted.countDown();

                        publicationService.deleteInstallment(
                                userId,
                                installmentId
                        );
                    })
            );

            assertThat(
                    deletionAttemptStarted.await(
                            15,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // Deletion should remain blocked while publication
            // holds the installment row lock.
            assertThatThrownBy(() ->
                    deletionFuture.get(
                            500,
                            TimeUnit.MILLISECONDS
                    )
            ).isInstanceOf(TimeoutException.class);

            // Allow the publication transaction to commit.
            allowPublicationCommit.countDown();

            publicationFuture.get(15, TimeUnit.SECONDS);

            // Deletion should observe the committed PUBLISHED state.
            assertThatThrownBy(() ->
                    deletionFuture.get(
                            15,
                            TimeUnit.SECONDS
                    )
            )
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(
                            PublishedInstallmentModificationException.class
                    );

            String persistedStatus = jdbcTemplate.queryForObject(
                    """
                    SELECT status
                    FROM installment
                    WHERE id = ?
                    """,
                    String.class,
                    installmentId
            );

            assertThat(persistedStatus).isEqualTo("PUBLISHED");
            assertThat(pageNumber(pageId)).isEqualTo(1);
            assertThat(Files.exists(physicalFile)).isTrue();

        } finally {
            allowPublicationCommit.countDown();

            executor.shutdownNow();
            executor.awaitTermination(
                    15,
                    TimeUnit.SECONDS
            );
        }
    }

    @Test
    void shouldPreservePageFilesWhenInstallmentDeletionRollsBack() {
        UUID pageId = publicationService.createPage(
                userId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "rollback-page".getBytes()
                )
        );

        String storageKey = jdbcTemplate.queryForObject(
                """
                SELECT storage_key
                FROM installment_page
                WHERE id = ?
                """,
                String.class,
                pageId
        );

        Path physicalFile = STORAGE_ROOT.resolve(storageKey);

        assertThat(Files.exists(physicalFile)).isTrue();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            publicationService.deleteInstallment(
                    userId,
                    installmentId
            );

            // Simulate a failure in the surrounding transaction.
            status.setRollbackOnly();
        });

        Long remainingInstallments = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM installment
                WHERE id = ?
                """,
                Long.class,
                installmentId
        );

        // The database transaction was rolled back.
        assertThat(remainingInstallments).isEqualTo(1L);
        assertThat(pageNumber(pageId)).isEqualTo(1);

        // The physical file must also remain available.
        assertThat(Files.exists(physicalFile)).isTrue();
    }

    @Test
    void shouldPreservePageFileWhenPageDeletionRollsBack() {
        UUID pageId = publicationService.createPage(
                userId,
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "rollback-page".getBytes()
                )
        );

        String storageKey = jdbcTemplate.queryForObject(
                """
                SELECT storage_key
                FROM installment_page
                WHERE id = ?
                """,
                String.class,
                pageId
        );

        Path physicalFile = STORAGE_ROOT.resolve(storageKey);

        assertThat(Files.exists(physicalFile)).isTrue();
        assertThat(pageNumber(pageId)).isEqualTo(1);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            publicationService.deletePage(
                    userId,
                    installmentId,
                    pageId
            );

            // Simulate a rollback of the surrounding transaction.
            status.setRollbackOnly();
        });

        // The page record must be restored by PostgreSQL.
        assertThat(pageNumber(pageId)).isEqualTo(1);

        // The physical file must remain available.
        assertThat(Files.exists(physicalFile)).isTrue();
    }

    @Test
    void shouldRemoveUploadedPageFileWhenTransactionRollsBack() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        Path uploadedFile = transactionTemplate.execute(status -> {
            publicationService.createPage(
                    userId,
                    new CreatePageCommand(
                            installmentId,
                            1,
                            "image/jpeg",
                            "rollback-upload".getBytes()
                    )
            );

            // Verify that the file exists before rolling back.
            Path storedFile;

            try (var paths = Files.walk(STORAGE_ROOT)) {
                List<Path> storedFiles = paths
                        .filter(Files::isRegularFile)
                        .toList();

                assertThat(storedFiles).hasSize(1);
                storedFile = storedFiles.getFirst();

            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }

            status.setRollbackOnly();

            return storedFile;
        });

        Long persistedPages = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM installment_page
                WHERE installment_id = ?
                """,
                Long.class,
                installmentId
        );

        // PostgreSQL must discard the page record.
        assertThat(persistedPages).isZero();

        // The physical file must also be removed.
        assertThat(uploadedFile).isNotNull();
        assertThat(Files.exists(uploadedFile)).isFalse();
    }

}