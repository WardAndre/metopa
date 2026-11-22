package metopa.moderation;

import metopa.moderation.internal.ModerationReport;
import metopa.moderation.internal.ModerationReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ModerationStartReviewConcurrencyIntegrationTests {

    @Autowired
    private ModerationService moderationService;

    @Autowired
    private ModerationReportRepository reportRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createdReportId;

    @AfterEach
    void cleanUp() {
        if (createdReportId == null) {
            return;
        }

        jdbcTemplate.update(
                """
                DELETE FROM moderation_audit_entry
                WHERE report_id = ?
                """,
                createdReportId
        );

        jdbcTemplate.update(
                """
                DELETE FROM moderation_report
                WHERE id = ?
                """,
                createdReportId
        );
    }

    @Test
    void shouldAllowOnlyOneConcurrentReviewStart()
            throws Exception {

        UUID reportId = createReport();

        UUID firstAdminId = UUID.randomUUID();
        UUID secondAdminId = UUID.randomUUID();

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction =
                new CountDownLatch(1);
        CountDownLatch secondAttemptStarted =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> firstAttempt = executor.submit(() -> {
                TransactionTemplate transaction =
                        new TransactionTemplate(transactionManager);

                transaction.executeWithoutResult(status -> {
                    reportRepository
                            .findByIdForUpdate(reportId)
                            .orElseThrow();

                    lockAcquired.countDown();

                    await(releaseFirstTransaction);

                    moderationService.startReview(
                            reportId,
                            firstAdminId,
                            "Review initiated by first administrator."
                    );
                });
            });

            assertThat(
                    lockAcquired.await(15, TimeUnit.SECONDS)
            ).isTrue();

            Future<Boolean> secondAttempt = executor.submit(() -> {
                secondAttemptStarted.countDown();

                try {
                    moderationService.startReview(
                            reportId,
                            secondAdminId,
                            "Review initiated by second administrator."
                    );

                    return true;
                } catch (IllegalStateException expected) {
                    return false;
                }
            });

            assertThat(
                    secondAttemptStarted.await(
                            15,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // The first transaction still holds the row lock.
            assertThatThrownBy(() ->
                    secondAttempt.get(
                            200,
                            TimeUnit.MILLISECONDS
                    )
            ).isInstanceOf(TimeoutException.class);

            // Allow the first transaction to finish and commit.
            releaseFirstTransaction.countDown();

            firstAttempt.get(30, TimeUnit.SECONDS);

            // The second administrator must not start another review.
            assertThat(
                    secondAttempt.get(30, TimeUnit.SECONDS)
            ).isFalse();

            String persistedStatus =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT status
                            FROM moderation_report
                            WHERE id = ?
                            """,
                            String.class,
                            reportId
                    );

            assertThat(persistedStatus)
                    .isEqualTo("UNDER_REVIEW");

            Integer auditCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM moderation_audit_entry
                            WHERE report_id = ?
                            """,
                            Integer.class,
                            reportId
                    );

            assertThat(auditCount).isEqualTo(1);

            UUID recordedAdminId =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT admin_user_id
                            FROM moderation_audit_entry
                            WHERE report_id = ?
                            """,
                            UUID.class,
                            reportId
                    );

            assertThat(recordedAdminId)
                    .isEqualTo(firstAdminId);

        } finally {
            releaseFirstTransaction.countDown();

            executor.shutdownNow();

            assertThat(
                    executor.awaitTermination(
                            30,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
        }
    }

    private UUID createReport() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        reportRepository.saveAndFlush(report);

        createdReportId = report.getId();

        return createdReportId;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for transaction release."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Transaction wait interrupted.",
                    exception
            );
        }
    }
}
