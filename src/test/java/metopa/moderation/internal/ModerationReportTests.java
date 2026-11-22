package metopa.moderation.internal;

import metopa.moderation.ReportReason;
import metopa.moderation.ReportStatus;
import metopa.moderation.ModerationDecision;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModerationReportTests {

    @Test
    void shouldCreateOpenReport() {
        UUID workId = UUID.randomUUID();
        UUID reporterUserId = UUID.randomUUID();

        ModerationReport report = new ModerationReport(
                workId,
                reporterUserId,
                ReportReason.COPYRIGHT,
                "Unauthorized reproduction of copyrighted content."
        );

        assertThat(report.getId()).isNotNull();
        assertThat(report.getWorkId()).isEqualTo(workId);
        assertThat(report.getReporterUserId()).isEqualTo(reporterUserId);
        assertThat(report.getReason()).isEqualTo(ReportReason.COPYRIGHT);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(report.getCreatedAt()).isNotNull();
        assertThat(report.getUpdatedAt()).isEqualTo(report.getCreatedAt());
    }

    @Test
    void shouldNormalizeReportDescription() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.AGE_RATING,
                "  Incorrect age classification.  "
        );

        assertThat(report.getDescription())
                .isEqualTo("Incorrect age classification.");
    }

    @Test
    void shouldRejectBlankDescription() {
        assertThatThrownBy(() -> new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.OTHER,
                "   "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDescriptionLongerThan2000Characters() {
        assertThatThrownBy(() -> new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.OTHER,
                "a".repeat(2001)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldStartReviewForOpenReport() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        Instant previousUpdatedAt = report.getUpdatedAt();

        report.startReview();

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.UNDER_REVIEW);

        assertThat(report.getUpdatedAt())
                .isAfterOrEqualTo(previousUpdatedAt);
    }

    @Test
    void shouldRejectStartingReviewMoreThanOnce() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.AGE_RATING,
                "Incorrect age classification."
        );

        report.startReview();

        Instant updatedAt = report.getUpdatedAt();

        assertThatThrownBy(report::startReview)
                .isInstanceOf(IllegalStateException.class);

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.UNDER_REVIEW);

        assertThat(report.getUpdatedAt())
                .isEqualTo(updatedAt);
    }

    @Test
    void shouldResolveReviewedReportWithoutViolation() {
        ModerationReport report = createReport();

        report.startReview();

        Instant previousUpdatedAt = report.getUpdatedAt();

        report.resolveNoViolation();

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.RESOLVED);

        assertThat(report.getDecision())
                .isEqualTo(ModerationDecision.NO_VIOLATION);

        assertThat(report.getUpdatedAt())
                .isAfterOrEqualTo(previousUpdatedAt);
    }

    @Test
    void shouldRejectResolvingOpenReport() {
        ModerationReport report = createReport();

        Instant previousUpdatedAt = report.getUpdatedAt();

        assertThatThrownBy(report::resolveNoViolation)
                .isInstanceOf(IllegalStateException.class);

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.OPEN);

        assertThat(report.getDecision()).isNull();

        assertThat(report.getUpdatedAt())
                .isEqualTo(previousUpdatedAt);
    }

    @Test
    void shouldRejectResolvingReportTwice() {
        ModerationReport report = createReport();

        report.startReview();
        report.resolveNoViolation();

        Instant resolvedUpdatedAt = report.getUpdatedAt();

        assertThatThrownBy(report::resolveNoViolation)
                .isInstanceOf(IllegalStateException.class);

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.RESOLVED);

        assertThat(report.getDecision())
                .isEqualTo(ModerationDecision.NO_VIOLATION);

        assertThat(report.getUpdatedAt())
                .isEqualTo(resolvedUpdatedAt);
    }

    private ModerationReport createReport() {
        return new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );
    }
}