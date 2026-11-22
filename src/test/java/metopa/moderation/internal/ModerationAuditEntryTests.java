package metopa.moderation.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModerationAuditEntryTests {

    @Test
    void shouldCreateReviewStartedAuditEntry() {
        UUID reportId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        ModerationAuditEntry entry = new ModerationAuditEntry(
                reportId,
                adminUserId,
                ModerationAuditAction.REVIEW_STARTED,
                "Review initiated by administrator."
        );

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getReportId()).isEqualTo(reportId);
        assertThat(entry.getAdminUserId()).isEqualTo(adminUserId);
        assertThat(entry.getAction())
                .isEqualTo(ModerationAuditAction.REVIEW_STARTED);
        assertThat(entry.getNote())
                .isEqualTo("Review initiated by administrator.");
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldNormalizeAuditNote() {
        ModerationAuditEntry entry = new ModerationAuditEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModerationAuditAction.REVIEW_STARTED,
                "  Review initiated.  "
        );

        assertThat(entry.getNote())
                .isEqualTo("Review initiated.");
    }

    @Test
    void shouldRejectBlankAuditNote() {
        assertThatThrownBy(() -> new ModerationAuditEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModerationAuditAction.REVIEW_STARTED,
                "   "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMissingAdministrator() {
        assertThatThrownBy(() -> new ModerationAuditEntry(
                UUID.randomUUID(),
                null,
                ModerationAuditAction.REVIEW_STARTED,
                "Review initiated."
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectAuditNoteLongerThan2000Characters() {
        assertThatThrownBy(() -> new ModerationAuditEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModerationAuditAction.REVIEW_STARTED,
                "a".repeat(2001)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}