package metopa.catalog;

import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.media.BinaryStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-work-cover-storage"
        }
)
@AutoConfigureMockMvc
class WorkCoverIntegrationTests {

    private static final String PASSWORD =
            "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private BinaryStorage binaryStorage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldStoreCoverAndPersistReference()
            throws Exception {

        TestUser user =
                createUser("cover-owner");

        UUID workId =
                createWork(user.id());

        byte[] content =
                "real-cover-content".getBytes();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        content
                );

        MockHttpSession session =
                login(user.username());

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

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        String storageKey =
                (String) row.get(
                        "cover_storage_key"
                );

        String contentType =
                (String) row.get(
                        "cover_content_type"
                );

        assertThat(storageKey)
                .startsWith(
                        "covers/"
                                + workId
                                + "/"
                );

        assertThat(contentType)
                .isEqualTo("image/webp");

        assertThat(
                binaryStorage.read(storageKey)
        ).isEqualTo(content);
    }

    @Test
    void shouldRejectCoverUploadFromAnotherUser()
            throws Exception {

        TestUser owner =
                createUser("cover-owner");

        TestUser anotherUser =
                createUser("cover-other");

        UUID workId =
                createWork(owner.id());

        byte[] content =
                "unauthorized-cover".getBytes();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        content
                );

        MockHttpSession session =
                login(anotherUser.username());

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
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Work access denied"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(
                row.get("cover_storage_key")
        ).isNull();

        assertThat(
                row.get("cover_content_type")
        ).isNull();
    }

    @Test
    void shouldRejectUnauthenticatedCoverUpload()
            throws Exception {

        UUID workId = UUID.randomUUID();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        "cover-content".getBytes()
                );

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
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireCsrfForCoverUpload()
            throws Exception {

        TestUser user =
                createUser("cover-csrf");

        UUID workId =
                createWork(user.id());

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        "cover-content".getBytes()
                );

        MockHttpSession session =
                login(user.username());

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
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnCreatorWorkCoverForOwner()
            throws Exception {

        TestUser owner =
                createUser("creator-cover-owner");

        UUID workId =
                createWork(owner.id());

        byte[] coverContent =
                "creator-cover-content".getBytes();

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                coverContent
        );

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}/cover",
                                workId
                        )
                                .session(session)
                )
                .andExpect(
                        status().isOk()
                )
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
    void shouldRejectCreatorWorkCoverFromNonOwner()
            throws Exception {

        TestUser owner =
                createUser("creator-cover-owner");

        TestUser anotherUser =
                createUser("creator-cover-other");

        UUID workId =
                createWork(owner.id());

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                "private-cover-content".getBytes()
        );

        MockHttpSession session =
                login(anotherUser.username());

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}/cover",
                                workId
                        )
                                .session(session)
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/problem+json"
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Work access denied"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );
    }

    @Test
    void shouldRemoveNewCoverWhenReplacementRollsBack() {
        TestUser user = createUser("cover-rollback");

        UUID workId = createWork(user.id());

        byte[] originalContent =
                "original-cover".getBytes(StandardCharsets.UTF_8);

        catalogService.setCover(
                user.id(),
                workId,
                "image/webp",
                originalContent
        );

        String originalStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        Path originalFile =
                Path.of("target/test-work-cover-storage")
                        .resolve(originalStorageKey);

        assertThat(Files.exists(originalFile)).isTrue();

        assertThat(coverFiles(workId))
                .containsExactly(originalFile);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            catalogService.setCover(
                    user.id(),
                    workId,
                    "image/png",
                    "replacement-cover".getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            // Both files exist before transaction completion.
            assertThat(coverFiles(workId)).hasSize(2);

            status.setRollbackOnly();
        });

        String persistedStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        // The database must retain the original cover.
        assertThat(persistedStorageKey)
                .isEqualTo(originalStorageKey);

        // The original file must remain readable.
        assertThat(binaryStorage.read(originalStorageKey))
                .isEqualTo(originalContent);

        // The replacement file must be removed after rollback.
        assertThat(coverFiles(workId))
                .containsExactly(originalFile);
    }

    @Test
    void shouldRemovePreviousCoverAfterSuccessfulReplacement() {
        TestUser user = createUser("cover-replacement");

        UUID workId = createWork(user.id());

        byte[] originalContent =
                "original-cover".getBytes(StandardCharsets.UTF_8);

        byte[] replacementContent =
                "replacement-cover".getBytes(StandardCharsets.UTF_8);

        // Create and commit the original cover.
        catalogService.setCover(
                user.id(),
                workId,
                "image/webp",
                originalContent
        );

        String originalStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        Path originalFile =
                Path.of("target/test-work-cover-storage")
                        .resolve(originalStorageKey);

        assertThat(Files.exists(originalFile))
                .isTrue();

        assertThat(binaryStorage.read(originalStorageKey))
                .isEqualTo(originalContent);

        // Replace the cover and commit the transaction.
        catalogService.setCover(
                user.id(),
                workId,
                "image/png",
                replacementContent
        );

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        String replacementStorageKey =
                (String) row.get("cover_storage_key");

        Path replacementFile =
                Path.of("target/test-work-cover-storage")
                        .resolve(replacementStorageKey);

        // PostgreSQL must reference the new cover.
        assertThat(replacementStorageKey)
                .isNotEqualTo(originalStorageKey);

        assertThat(row.get("cover_content_type"))
                .isEqualTo("image/png");

        // The new cover must remain available.
        assertThat(Files.exists(replacementFile))
                .isTrue();

        assertThat(binaryStorage.read(replacementStorageKey))
                .isEqualTo(replacementContent);

        // The previous cover must be removed after commit.
        assertThat(Files.exists(originalFile))
                .isFalse();

        assertThat(coverFiles(workId))
                .containsExactly(replacementFile);
    }

    @Test
    void shouldWaitForWorkLockBeforeStoringReplacementCover()
            throws Exception {

        TestUser user = createUser("cover-concurrency");

        UUID workId = createWork(user.id());

        catalogService.setCover(
                user.id(),
                workId,
                "image/webp",
                "original-cover".getBytes(StandardCharsets.UTF_8)
        );

        String originalStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        Path storageRoot =
                Path.of("target/test-work-cover-storage");

        Path coverDirectory =
                storageRoot.resolve("covers")
                        .resolve(workId.toString());

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch uploadStarted = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        Future<?> lockingTask = null;
        Future<?> uploadTask = null;

        try (WatchService watcher =
                     FileSystems.getDefault().newWatchService()) {

            coverDirectory.register(
                    watcher,
                    StandardWatchEventKinds.ENTRY_CREATE
            );

            // Transaction A locks the work row.
            lockingTask = executor.submit(() ->
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(status -> {

                                jdbcTemplate.queryForObject(
                                        """
                                        SELECT id
                                        FROM work
                                        WHERE id = ?
                                        FOR UPDATE
                                        """,
                                        UUID.class,
                                        workId
                                );

                                lockAcquired.countDown();

                                try {
                                    if (!releaseLock.await(
                                            15,
                                            TimeUnit.SECONDS
                                    )) {
                                        throw new IllegalStateException(
                                                "Timed out waiting to release work lock"
                                        );
                                    }
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();

                                    throw new IllegalStateException(
                                            "Interrupted while holding work lock",
                                            exception
                                    );
                                }
                            })
            );

            assertThat(
                    lockAcquired.await(10, TimeUnit.SECONDS)
            ).isTrue();

            // Transaction B attempts to replace the cover.
            uploadTask = executor.submit(() -> {
                uploadStarted.countDown();

                catalogService.setCover(
                        user.id(),
                        workId,
                        "image/png",
                        "replacement-cover".getBytes(
                                StandardCharsets.UTF_8
                        )
                );
            });

            assertThat(
                    uploadStarted.await(10, TimeUnit.SECONDS)
            ).isTrue();

            // No new file should be created while
            // Transaction A holds the work lock.
            WatchKey fileCreation =
                    watcher.poll(2, TimeUnit.SECONDS);

            assertThat(fileCreation)
                    .as(
                            "Cover replacement must wait for the work lock "
                                    + "before storing a new file"
                    )
                    .isNull();

        } finally {
            releaseLock.countDown();

            try {
                if (lockingTask != null) {
                    lockingTask.get(15, TimeUnit.SECONDS);
                }

                if (uploadTask != null) {
                    uploadTask.get(15, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }
        }

        String currentStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(currentStorageKey)
                .isNotEqualTo(originalStorageKey);

        assertThat(coverFiles(workId))
                .containsExactly(
                        storageRoot.resolve(currentStorageKey)
                );
    }

    @Test
    void shouldPreserveCoverWhenMetadataUpdateCommitsConcurrently()
            throws Exception {

        TestUser user = createUser("cover-metadata");

        UUID workId = createWork(user.id());

        byte[] originalContent =
                "original-cover".getBytes(StandardCharsets.UTF_8);

        byte[] replacementContent =
                "replacement-cover".getBytes(StandardCharsets.UTF_8);

        catalogService.setCover(
                user.id(),
                workId,
                "image/webp",
                originalContent
        );

        CountDownLatch metadataUpdated =
                new CountDownLatch(1);

        CountDownLatch allowMetadataCommit =
                new CountDownLatch(1);

        CountDownLatch replacementStarted =
                new CountDownLatch(1);

        CountDownLatch replacementCompleted =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        Future<?> metadataTask =
                executor.submit(() -> {
                    TransactionTemplate transaction =
                            new TransactionTemplate(transactionManager);

                    transaction.executeWithoutResult(status -> {
                        catalogService.updateWork(
                                user.id(),
                                workId,
                                new UpdateWorkCommand(
                                        "Updated Concurrent Title",
                                        "Updated description.",
                                        WorkType.COMIC,
                                        ReadingDirection.LEFT_TO_RIGHT,
                                        PresentationMode.SINGLE_PAGE,
                                        Set.of()
                                )
                        );

                        metadataUpdated.countDown();

                        try {
                            if (!allowMetadataCommit.await(
                                    20,
                                    TimeUnit.SECONDS
                            )) {
                                throw new IllegalStateException(
                                        "Timed out waiting for metadata commit"
                                );
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();

                            throw new IllegalStateException(
                                    "Metadata transaction interrupted",
                                    exception
                            );
                        }
                    });
                });

        Future<String> replacementTask =
                executor.submit(() -> {
                    if (!metadataUpdated.await(
                            10,
                            TimeUnit.SECONDS
                    )) {
                        throw new IllegalStateException(
                                "Metadata update did not start"
                        );
                    }

                    replacementStarted.countDown();

                    catalogService.setCover(
                            user.id(),
                            workId,
                            "image/png",
                            replacementContent
                    );

                    String storageKey =
                            jdbcTemplate.queryForObject(
                                    """
                                    SELECT cover_storage_key
                                    FROM work
                                    WHERE id = ?
                                    """,
                                    String.class,
                                    workId
                            );

                    replacementCompleted.countDown();

                    return storageKey;
                });

        try {
            assertThat(
                    metadataUpdated.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            assertThat(
                    replacementStarted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // Allow the cover replacement to complete first
            // when metadata updates are not locking the work.
            // With a pessimistic lock, it should wait instead.
            replacementCompleted.await(
                    3,
                    TimeUnit.SECONDS
            );

            allowMetadataCommit.countDown();

            metadataTask.get(20, TimeUnit.SECONDS);

            String replacementStorageKey =
                    replacementTask.get(
                            20,
                            TimeUnit.SECONDS
                    );

            Map<String, Object> row =
                    jdbcTemplate.queryForMap(
                            """
                            SELECT title,
                                   cover_storage_key,
                                   cover_content_type
                            FROM work
                            WHERE id = ?
                            """,
                            workId
                    );

            assertThat(row.get("title"))
                    .isEqualTo("Updated Concurrent Title");

            assertThat(row.get("cover_storage_key"))
                    .isEqualTo(replacementStorageKey);

            assertThat(row.get("cover_content_type"))
                    .isEqualTo("image/png");

            assertThat(binaryStorage.read(replacementStorageKey))
                    .isEqualTo(replacementContent);

            Path replacementFile =
                    Path.of("target/test-work-cover-storage")
                            .resolve(replacementStorageKey);

            assertThat(coverFiles(workId))
                    .containsExactly(replacementFile);

        } finally {
            allowMetadataCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRemoveExistingCoverForWorkOwner()
            throws Exception {

        TestUser user = createUser("cover-removal");

        UUID workId = createWork(user.id());

        byte[] content =
                "original-cover".getBytes(StandardCharsets.UTF_8);

        catalogService.setCover(
                user.id(),
                workId,
                "image/webp",
                content
        );

        String storageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        Path coverFile =
                Path.of("target/test-work-cover-storage")
                        .resolve(storageKey);

        assertThat(Files.exists(coverFile))
                .isTrue();

        MockHttpSession session = login(user.username());

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(row.get("cover_storage_key"))
                .isNull();

        assertThat(row.get("cover_content_type"))
                .isNull();

        assertThat(Files.exists(coverFile))
                .isFalse();
    }

    @Test
    void shouldPreserveCoverWhenRemovalRollsBack() {
        TestUser user =
                createUser("cover-removal-rollback");

        UUID workId =
                createWork(user.id());

        byte[] content =
                "original-cover-content".getBytes(
                        StandardCharsets.UTF_8
                );

        catalogService.setCover(
                user.id(),
                workId,
                "image/webp",
                content
        );

        Map<String, Object> originalRow =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        String originalStorageKey =
                (String) originalRow.get(
                        "cover_storage_key"
                );

        assertThat(originalStorageKey)
                .isNotNull();

        assertThat(coverFiles(workId))
                .hasSize(1);

        TransactionTemplate transaction =
                new TransactionTemplate(
                        transactionManager
                );

        transaction.executeWithoutResult(status -> {
            catalogService.removeCover(
                    user.id(),
                    workId
            );

            // The physical file must not be deleted
            // before the transaction commits.
            assertThat(coverFiles(workId))
                    .hasSize(1);

            status.setRollbackOnly();
        });

        Map<String, Object> persistedRow =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(
                persistedRow.get("cover_storage_key")
        ).isEqualTo(originalStorageKey);

        assertThat(
                persistedRow.get("cover_content_type")
        ).isEqualTo("image/webp");

        assertThat(
                binaryStorage.read(originalStorageKey)
        ).isEqualTo(content);

        assertThat(coverFiles(workId))
                .hasSize(1);
    }

    @Test
    void shouldRemoveCoverIdempotentlyWhenWorkHasNoCover()
            throws Exception {

        TestUser user =
                createUser("cover-removal-idempotent");

        UUID workId =
                createWork(user.id());

        MockHttpSession session =
                login(user.username());

        // First removal: the work has no cover.
        mockMvc.perform(
                        delete(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        // Second removal: the operation remains idempotent.
        mockMvc.perform(
                        delete(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(
                row.get("cover_storage_key")
        ).isNull();

        assertThat(
                row.get("cover_content_type")
        ).isNull();
    }

    @Test
    void shouldRejectCoverRemovalFromAnotherUser()
            throws Exception {

        TestUser owner =
                createUser("cover-removal-owner");

        TestUser anotherUser =
                createUser("cover-removal-other");

        UUID workId =
                createWork(owner.id());

        byte[] content =
                "original-cover".getBytes(StandardCharsets.UTF_8);

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                content
        );

        String originalStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(originalStorageKey)
                .isNotNull();

        MockHttpSession session =
                login(anotherUser.username());

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.title")
                                .value("Work access denied")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(row.get("cover_storage_key"))
                .isEqualTo(originalStorageKey);

        assertThat(row.get("cover_content_type"))
                .isEqualTo("image/webp");

        assertThat(
                binaryStorage.read(originalStorageKey)
        ).isEqualTo(content);

        assertThat(coverFiles(workId))
                .hasSize(1);
    }

    @Test
    void shouldRejectUnauthenticatedCoverRemoval()
            throws Exception {

        TestUser owner =
                createUser("cover-unauth");

        UUID workId =
                createWork(owner.id());

        byte[] content =
                "original-cover".getBytes(StandardCharsets.UTF_8);

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                content
        );

        String originalStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(originalStorageKey)
                .isNotNull();

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized());

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(row.get("cover_storage_key"))
                .isEqualTo(originalStorageKey);

        assertThat(row.get("cover_content_type"))
                .isEqualTo("image/webp");

        assertThat(binaryStorage.read(originalStorageKey))
                .isEqualTo(content);

        assertThat(coverFiles(workId))
                .hasSize(1);
    }

    @Test
    void shouldRequireCsrfForCoverRemoval()
            throws Exception {

        TestUser owner =
                createUser("cover-removal-csrf");

        UUID workId =
                createWork(owner.id());

        byte[] content =
                "original-cover".getBytes(StandardCharsets.UTF_8);

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                content
        );

        String originalStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(originalStorageKey)
                .isNotNull();

        MockHttpSession session =
                login(owner.username());

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .session(session)
                )
                .andExpect(status().isForbidden());

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(row.get("cover_storage_key"))
                .isEqualTo(originalStorageKey);

        assertThat(row.get("cover_content_type"))
                .isEqualTo("image/webp");

        assertThat(
                binaryStorage.read(originalStorageKey)
        ).isEqualTo(content);

        assertThat(coverFiles(workId))
                .hasSize(1);
    }

    @Test
    void shouldReturnNotFoundWhenRemovingCoverFromNonexistentWork()
            throws Exception {

        TestUser user =
                createUser("cover-removal-notfound");

        UUID nonexistentWorkId =
                UUID.randomUUID();

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}/cover",
                                nonexistentWorkId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNotFound());

        Integer workCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        nonexistentWorkId
                );

        assertThat(workCount)
                .isZero();
    }

    @Test
    void shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently()
            throws Exception {

        TestUser owner =
                createUser("cover-removal-race");

        UUID workId =
                createWork(owner.id());

        byte[] originalContent =
                "original-cover".getBytes(
                        StandardCharsets.UTF_8
                );

        byte[] replacementContent =
                "replacement-cover".getBytes(
                        StandardCharsets.UTF_8
                );

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                originalContent
        );

        String originalStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(originalStorageKey).isNotNull();
        assertThat(coverFiles(workId)).hasSize(1);

        CountDownLatch removalReady =
                new CountDownLatch(1);

        CountDownLatch allowRemovalCommit =
                new CountDownLatch(1);

        CountDownLatch replacementStarted =
                new CountDownLatch(1);

        CountDownLatch replacementFinished =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> removal = executor.submit(() -> {
                TransactionTemplate transaction =
                        new TransactionTemplate(
                                transactionManager
                        );

                transaction.executeWithoutResult(status -> {
                    catalogService.removeCover(
                            owner.id(),
                            workId
                    );

                    removalReady.countDown();

                    try {
                        if (!allowRemovalCommit.await(
                                10,
                                TimeUnit.SECONDS
                        )) {
                            throw new IllegalStateException(
                                    "Timed out waiting for removal commit"
                            );
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                exception
                        );
                    }
                });
            });

            assertThat(
                    removalReady.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // The original file must still exist
            // before the removal transaction commits.
            assertThat(coverFiles(workId))
                    .hasSize(1);

            Future<?> replacement = executor.submit(() -> {
                replacementStarted.countDown();

                try {
                    catalogService.setCover(
                            owner.id(),
                            workId,
                            "image/png",
                            replacementContent
                    );
                } finally {
                    replacementFinished.countDown();
                }
            });

            assertThat(
                    replacementStarted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // Replacement must not complete while
            // the removal transaction holds the lock.
            assertThat(
                    replacementFinished.await(
                            300,
                            TimeUnit.MILLISECONDS
                    )
            ).isFalse();

            // No replacement file should have been
            // stored while the row remains locked.
            assertThat(coverFiles(workId))
                    .hasSize(1);

            allowRemovalCommit.countDown();

            removal.get(15, TimeUnit.SECONDS);
            replacement.get(15, TimeUnit.SECONDS);

            Map<String, Object> row =
                    jdbcTemplate.queryForMap(
                            """
                            SELECT cover_storage_key,
                                   cover_content_type
                            FROM work
                            WHERE id = ?
                            """,
                            workId
                    );

            String finalStorageKey =
                    (String) row.get(
                            "cover_storage_key"
                    );

            assertThat(finalStorageKey)
                    .isNotNull()
                    .isNotEqualTo(originalStorageKey);

            assertThat(
                    row.get("cover_content_type")
            ).isEqualTo("image/png");

            assertThat(
                    binaryStorage.read(finalStorageKey)
            ).isEqualTo(replacementContent);

            // Only the replacement cover should remain.
            assertThat(coverFiles(workId))
                    .hasSize(1);

        } finally {
            allowRemovalCommit.countDown();
            executor.shutdownNow();
        }
    }

    private List<Path> coverFiles(UUID workId) {
        Path coverDirectory =
                Path.of(
                        "target/test-work-cover-storage",
                        "covers",
                        workId.toString()
                );

        try (var files = Files.list(coverDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private UUID createWork(UUID ownerId) {
        return catalogService.createWork(
                new CreateWorkCommand(
                        ownerId,
                        "Covered Work "
                                + UUID.randomUUID(),
                        "A work with a cover.",
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
                        "Cover Test User",
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