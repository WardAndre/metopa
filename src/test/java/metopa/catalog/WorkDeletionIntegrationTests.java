package metopa.catalog;

import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreateInstallmentCommand;
import metopa.publication.InstallmentType;
import metopa.publication.PublicationService;
import metopa.publication.CreatePageCommand;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-work-deletion-storage"
        }
)
@AutoConfigureMockMvc
class WorkDeletionIntegrationTests {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldDeleteEmptyWorkForOwner()
            throws Exception {

        String username =
                "work-delete-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Work Deletion Test User",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(
                        username
                );

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Work To Delete",
                                "An empty work.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        Integer beforeDeletion =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(beforeDeletion).isEqualTo(1);

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Integer afterDeletion =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(afterDeletion).isZero();
    }

    @Test
    void shouldRejectDeletingWorkWithDraftInstallment()
            throws Exception {

        String username =
                "work-delete-draft-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Draft Work Owner",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Work With Draft",
                                "Work containing a draft installment.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Draft Chapter"
                        )
                );

        String installmentStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM installment
                        WHERE id = ?
                        """,
                        String.class,
                        installmentId
                );

        assertThat(installmentStatus)
                .isEqualTo("DRAFT");

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isConflict());

        Integer workCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        Integer installmentCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment
                        WHERE id = ?
                        """,
                        Integer.class,
                        installmentId
                );

        assertThat(workCount).isEqualTo(1);
        assertThat(installmentCount).isEqualTo(1);
    }

    @Test
    void shouldRejectDeletingWorkWithPublishedInstallment()
            throws Exception {

        String username =
                "work-delete-published-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Published Work Owner",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Published Work",
                                "Work containing published content.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Published Chapter"
                        )
                );

        publicationService.createPage(
                owner.id(),
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        "published-page-content".getBytes()
                )
        );

        publicationService.publishInstallment(
                owner.id(),
                installmentId
        );

        String installmentStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM installment
                        WHERE id = ?
                        """,
                        String.class,
                        installmentId
                );

        assertThat(installmentStatus)
                .isEqualTo("PUBLISHED");

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isConflict());

        Integer workCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        Integer installmentCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment
                        WHERE id = ?
                        """,
                        Integer.class,
                        installmentId
                );

        Integer pageCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment_page
                        WHERE installment_id = ?
                        """,
                        Integer.class,
                        installmentId
                );

        assertThat(workCount).isEqualTo(1);
        assertThat(installmentCount).isEqualTo(1);
        assertThat(pageCount).isEqualTo(1);

        String persistedStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM installment
                        WHERE id = ?
                        """,
                        String.class,
                        installmentId
                );

        assertThat(persistedStatus)
                .isEqualTo("PUBLISHED");
    }

    @Test
    void shouldDeleteWorkCoverAfterSuccessfulWorkDeletion()
            throws Exception {

        String username =
                "work-delete-cover-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Work Cover Deletion Owner",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Covered Work",
                                "A work with a cover.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        byte[] coverContent =
                "work-cover-content".getBytes();

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                coverContent
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

        assertThat(storageKey).isNotBlank();

        Path coverPath =
                Path.of(
                        "target/test-work-deletion-storage"
                ).resolve(storageKey);

        assertThat(Files.exists(coverPath))
                .isTrue();

        assertThat(Files.readAllBytes(coverPath))
                .isEqualTo(coverContent);

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Integer workCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCount).isZero();

        assertThat(Files.exists(coverPath))
                .isFalse();
    }

    @Test
    void shouldPreserveWorkAndCoverWhenDeletionRollsBack()
            throws Exception {

        String username =
                "rollback-work-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Rollback Work Owner",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Rollback Work",
                                "Work with a preserved cover.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        byte[] coverContent =
                "original-cover-content".getBytes();

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                coverContent
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

        assertThat(storageKey).isNotBlank();

        Path coverPath =
                Path.of(
                        "target/test-work-deletion-storage"
                ).resolve(storageKey);

        assertThat(Files.exists(coverPath))
                .isTrue();

        assertThat(Files.readAllBytes(coverPath))
                .isEqualTo(coverContent);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {

            catalogService.deleteWork(
                    owner.id(),
                    workId
            );

            entityManager.flush();

            Integer countDuringTransaction =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM work
                            WHERE id = ?
                            """,
                            Integer.class,
                            workId
                    );

            assertThat(countDuringTransaction)
                    .isZero();

            // The physical cover must not be deleted
            // before the transaction commits.
            assertThat(Files.exists(coverPath))
                    .isTrue();

            status.setRollbackOnly();
        });

        Integer workCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCount).isEqualTo(1);

        String restoredStorageKey =
                jdbcTemplate.queryForObject(
                        """
                        SELECT cover_storage_key
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(restoredStorageKey)
                .isEqualTo(storageKey);

        assertThat(Files.exists(coverPath))
                .isTrue();

        assertThat(Files.readAllBytes(coverPath))
                .isEqualTo(coverContent);
    }

    @Test
    void shouldRejectWorkDeletionFromNonOwner()
            throws Exception {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        String ownerUsername =
                "delete-owner-" + suffix;

        String otherUsername =
                "delete-other-" + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        ownerUsername,
                        ownerUsername + "@example.com",
                        "Work Owner",
                        PASSWORD
                )
        );

        identityService.createUser(
                new CreateUserCommand(
                        otherUsername,
                        otherUsername + "@example.com",
                        "Another User",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(
                        ownerUsername
                );

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Protected Work",
                                "A work owned by another user.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        byte[] coverContent =
                "protected-cover-content".getBytes();

        catalogService.setCover(
                owner.id(),
                workId,
                "image/webp",
                coverContent
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

        assertThat(storageKey).isNotBlank();

        Path coverPath =
                Path.of(
                        "target/test-work-deletion-storage"
                ).resolve(storageKey);

        assertThat(Files.exists(coverPath))
                .isTrue();

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(otherUsername)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(otherUsername)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
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

        Integer workCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCount).isEqualTo(1);

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

        assertThat(persistedStorageKey)
                .isEqualTo(storageKey);

        assertThat(Files.exists(coverPath))
                .isTrue();

        assertThat(Files.readAllBytes(coverPath))
                .isEqualTo(coverContent);
    }

    @Test
    void shouldRejectUnauthenticatedWorkDeletion()
            throws Exception {

        String username =
                "delete-unauthenticated-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Unauthenticated Deletion Test",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Protected Work",
                                "A work requiring authentication.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized());

        Integer workCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCount).isEqualTo(1);
    }

    @Test
    void shouldRejectWorkDeletionWithoutCsrf()
            throws Exception {

        String username =
                "delete-no-csrf-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "CSRF Deletion Test",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "CSRF Protected Work",
                                "A work protected against CSRF.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                        // Intentionally omit CSRF token.
                )
                .andExpect(status().isForbidden());

        Integer workCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCount).isEqualTo(1);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonexistentWork()
            throws Exception {

        String username =
                "delete-not-found-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Missing Work Test User",
                        PASSWORD
                )
        );

        UUID workId = UUID.randomUUID();

        Integer workCountBefore =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCountBefore).isZero();

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Work not found")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The requested work does not exist."
                                )
                );

        Integer workCountAfter =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCountAfter).isZero();
    }

    @Test
    void shouldRejectInstallmentCreationWhenWorkDeletionCommitsFirst()
            throws Exception {

        String username =
                "concurrent-delete-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Concurrent Deletion Owner",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Concurrent Work",
                                "A work used in a concurrency test.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        CountDownLatch deletionFlushed =
                new CountDownLatch(1);

        CountDownLatch allowDeletionCommit =
                new CountDownLatch(1);

        CountDownLatch creationStarted =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> deletionFuture =
                    executor.submit(() -> {

                        TransactionTemplate template =
                                new TransactionTemplate(
                                        transactionManager
                                );

                        template.executeWithoutResult(status -> {

                            catalogService.deleteWork(
                                    owner.id(),
                                    workId
                            );

                            entityManager.flush();

                            deletionFlushed.countDown();

                            try {
                                if (!allowDeletionCommit.await(
                                        15,
                                        TimeUnit.SECONDS
                                )) {
                                    throw new IllegalStateException(
                                            "Timed out waiting to commit deletion"
                                    );
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();

                                throw new IllegalStateException(
                                        "Deletion transaction interrupted",
                                        exception
                                );
                            }
                        });
                    });

            assertThat(
                    deletionFlushed.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            Future<UUID> creationFuture =
                    executor.submit(() -> {

                        creationStarted.countDown();

                        return publicationService.createInstallment(
                                owner.id(),
                                new CreateInstallmentCommand(
                                        workId,
                                        InstallmentType.CHAPTER,
                                        1,
                                        "Concurrent Chapter"
                                )
                        );
                    });

            assertThat(
                    creationStarted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // Creation should not complete while
            // the deletion transaction holds the lock.
            assertThatThrownBy(() ->
                    creationFuture.get(
                            500,
                            TimeUnit.MILLISECONDS
                    )
            ).isInstanceOf(TimeoutException.class);

            // Commit the deletion and release the lock.
            allowDeletionCommit.countDown();

            deletionFuture.get(
                    10,
                    TimeUnit.SECONDS
            );

            // Creation must revalidate the work after
            // the deletion transaction has committed.
            assertThatThrownBy(() ->
                    creationFuture.get(
                            10,
                            TimeUnit.SECONDS
                    )
            )
                    .isInstanceOf(
                            ExecutionException.class
                    )
                    .hasCauseInstanceOf(
                            WorkNotFoundException.class
                    );

            Integer workCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM work
                            WHERE id = ?
                            """,
                            Integer.class,
                            workId
                    );

            Integer installmentCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM installment
                            WHERE work_id = ?
                            """,
                            Integer.class,
                            workId
                    );

            assertThat(workCount).isZero();
            assertThat(installmentCount).isZero();

        } finally {
            allowDeletionCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectWorkDeletionWhenInstallmentCreationCommitsFirst()
            throws Exception {

        String username =
                "concurrent-create-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Concurrent Creation Owner",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Concurrent Creation Work",
                                "A work used in a concurrency test.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        CountDownLatch creationFlushed =
                new CountDownLatch(1);

        CountDownLatch allowCreationCommit =
                new CountDownLatch(1);

        CountDownLatch deletionStarted =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<UUID> creationFuture =
                    executor.submit(() -> {

                        TransactionTemplate template =
                                new TransactionTemplate(
                                        transactionManager
                                );

                        return template.execute(status -> {

                            UUID installmentId =
                                    publicationService.createInstallment(
                                            owner.id(),
                                            new CreateInstallmentCommand(
                                                    workId,
                                                    InstallmentType.CHAPTER,
                                                    1,
                                                    "Concurrent Chapter"
                                            )
                                    );

                            entityManager.flush();

                            creationFlushed.countDown();

                            try {
                                if (!allowCreationCommit.await(
                                        15,
                                        TimeUnit.SECONDS
                                )) {
                                    throw new IllegalStateException(
                                            "Timed out waiting to commit creation"
                                    );
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();

                                throw new IllegalStateException(
                                        "Creation transaction interrupted",
                                        exception
                                );
                            }

                            return installmentId;
                        });
                    });

            assertThat(
                    creationFlushed.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            Future<?> deletionFuture =
                    executor.submit(() -> {

                        deletionStarted.countDown();

                        catalogService.deleteWork(
                                owner.id(),
                                workId
                        );
                    });

            assertThat(
                    deletionStarted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // Deletion must not finish while the
            // installment creation holds the work lock.
            assertThatThrownBy(() ->
                    deletionFuture.get(
                            500,
                            TimeUnit.MILLISECONDS
                    )
            ).isInstanceOf(TimeoutException.class);

            // Allow installment creation to commit.
            allowCreationCommit.countDown();

            UUID installmentId =
                    creationFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            assertThat(installmentId).isNotNull();

            // Once creation commits, deletion must
            // detect the installment and be rejected.
            assertThatThrownBy(() ->
                    deletionFuture.get(
                            10,
                            TimeUnit.SECONDS
                    )
            )
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(
                            WorkHasInstallmentsException.class
                    );

            Integer workCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM work
                            WHERE id = ?
                            """,
                            Integer.class,
                            workId
                    );

            Integer installmentCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM installment
                            WHERE id = ?
                              AND work_id = ?
                            """,
                            Integer.class,
                            installmentId,
                            workId
                    );

            assertThat(workCount).isEqualTo(1);
            assertThat(installmentCount).isEqualTo(1);

        } finally {
            allowCreationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRemoveLibraryEntriesWhenDeletingWork()
            throws Exception {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        String ownerUsername =
                "delete-library-owner-" + suffix;

        String readerUsername =
                "delete-library-reader-" + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        ownerUsername,
                        ownerUsername + "@example.com",
                        "Library Work Owner",
                        PASSWORD
                )
        );

        identityService.createUser(
                new CreateUserCommand(
                        readerUsername,
                        readerUsername + "@example.com",
                        "Library Reader",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(ownerUsername);

        UserReference reader =
                identityService.findByUsername(readerUsername);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Work With Library Reference",
                                "A work referenced by a library.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        UUID libraryEntryId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO library_entry (
                    id,
                    user_id,
                    work_id,
                    added_at
                )
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                libraryEntryId,
                reader.id(),
                workId
        );

        Integer entryCountBefore =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM library_entry
                        WHERE work_id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(entryCountBefore).isEqualTo(1);

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(ownerUsername)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(ownerUsername)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Integer workCountAfter =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        Integer entryCountAfter =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM library_entry
                        WHERE work_id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCountAfter).isZero();
        assertThat(entryCountAfter).isZero();
    }

    @Test
    void shouldRemoveReadingProgressWhenDeletingWork()
            throws Exception {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        String ownerUsername =
                "delete-progress-owner-" + suffix;

        String readerUsername =
                "delete-progress-reader-" + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        ownerUsername,
                        ownerUsername + "@example.com",
                        "Reading Progress Work Owner",
                        PASSWORD
                )
        );

        identityService.createUser(
                new CreateUserCommand(
                        readerUsername,
                        readerUsername + "@example.com",
                        "Reading Progress Reader",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(ownerUsername);

        UserReference reader =
                identityService.findByUsername(readerUsername);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Work With Reading Progress",
                                "A work referenced by reading progress.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        UUID progressId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        // Simulate a legacy reading progress reference.
        // No installment or page is created for this work.
        jdbcTemplate.update(
                """
                INSERT INTO reading_progress (
                    id,
                    user_id,
                    work_id,
                    installment_id,
                    page_id,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                progressId,
                reader.id(),
                workId,
                installmentId,
                pageId
        );

        Integer progressCountBefore =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM reading_progress
                        WHERE work_id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(progressCountBefore).isEqualTo(1);

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(ownerUsername)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(ownerUsername)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Integer workCountAfter =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        Integer progressCountAfter =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM reading_progress
                        WHERE work_id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCountAfter).isZero();
        assertThat(progressCountAfter).isZero();
    }

    @Test
    void shouldPreventDeletingWorkWithInstallmentsAtDatabaseLevel() {

        String username =
                "fk-delete-work-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "FK Protection Owner",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Protected Work",
                                "A work protected by a foreign key.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        UUID installmentId =
                publicationService.createInstallment(
                        owner.id(),
                        new CreateInstallmentCommand(
                                workId,
                                InstallmentType.CHAPTER,
                                1,
                                "Protected Chapter"
                        )
                );

        assertThat(
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work WHERE id = ?",
                        Integer.class,
                        workId
                )
        ).isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM installment WHERE id = ?",
                        Integer.class,
                        installmentId
                )
        ).isEqualTo(1);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            try {
                assertThatThrownBy(() ->
                        jdbcTemplate.update(
                                "DELETE FROM work WHERE id = ?",
                                workId
                        )
                )
                        .isInstanceOf(
                                DataIntegrityViolationException.class
                        )
                        .hasMessageContaining(
                                "fk_installment_work"
                        );
            } finally {
                status.setRollbackOnly();
            }
        });

        assertThat(
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work WHERE id = ?",
                        Integer.class,
                        workId
                )
        ).isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM installment
                        WHERE id = ?
                          AND work_id = ?
                        """,
                        Integer.class,
                        installmentId,
                        workId
                )
        ).isEqualTo(1);
    }

    @Test
    void shouldRemoveWorkGenresWhenDeletingWork()
            throws Exception {

        String username =
                "delete-work-genres-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Work Genres Deletion Owner",
                        PASSWORD
                )
        );

        UserReference owner =
                identityService.findByUsername(username);

        UUID workId =
                catalogService.createWork(
                        new CreateWorkCommand(
                                owner.id(),
                                "Work With Genres",
                                "A work with multiple genres.",
                                WorkType.COMIC,
                                ReadingDirection.LEFT_TO_RIGHT,
                                PresentationMode.SINGLE_PAGE
                        )
                );

        catalogService.updateWork(
                owner.id(),
                workId,
                new UpdateWorkCommand(
                        "Work With Genres",
                        "A work with multiple genres.",
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE,
                        Set.of(
                                Genre.SCI_FI,
                                Genre.ADVENTURE
                        )
                )
        );

        Integer workCountBefore =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        Integer genreCountBefore =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work_genre
                        WHERE work_id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCountBefore).isEqualTo(1);
        assertThat(genreCountBefore).isEqualTo(2);

        MvcResult loginResult =
                mockMvc.perform(
                                formLogin("/api/auth/login")
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        delete(
                                "/api/works/{workId}",
                                workId
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Integer workCountAfter =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work
                        WHERE id = ?
                        """,
                        Integer.class,
                        workId
                );

        Integer genreCountAfter =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM work_genre
                        WHERE work_id = ?
                        """,
                        Integer.class,
                        workId
                );

        assertThat(workCountAfter).isZero();
        assertThat(genreCountAfter).isZero();
    }
}
