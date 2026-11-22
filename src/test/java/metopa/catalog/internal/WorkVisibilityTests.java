package metopa.catalog.internal;

import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.catalog.WorkVisibility;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkVisibilityTests {

    @Test
    void shouldCreateWorkWithPublicVisibility() {
        Work work = createWork();

        assertThat(work.getVisibility())
                .isEqualTo(WorkVisibility.PUBLIC);
    }

    @Test
    void shouldRestrictPublicWork() {
        Work work = createWork();

        work.restrictVisibility();

        assertThat(work.getVisibility())
                .isEqualTo(WorkVisibility.RESTRICTED);
    }

    @Test
    void shouldRejectRestrictingAlreadyRestrictedWork() {
        Work work = createWork();

        work.restrictVisibility();

        assertThatThrownBy(work::restrictVisibility)
                .isInstanceOf(IllegalStateException.class);

        assertThat(work.getVisibility())
                .isEqualTo(WorkVisibility.RESTRICTED);
    }

    @Test
    void shouldRestoreRestrictedWork() {
        Work work = createWork();

        work.restrictVisibility();
        work.restoreVisibility();

        assertThat(work.getVisibility())
                .isEqualTo(WorkVisibility.PUBLIC);
    }

    @Test
    void shouldRejectRestoringPublicWork() {
        Work work = createWork();

        assertThatThrownBy(work::restoreVisibility)
                .isInstanceOf(IllegalStateException.class);

        assertThat(work.getVisibility())
                .isEqualTo(WorkVisibility.PUBLIC);
    }

    private Work createWork() {
        return new Work(
                UUID.randomUUID(),
                "Metopa Visibility Test",
                "Work used to validate visibility transitions.",
                WorkType.GRAPHIC_NOVEL,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );
    }
}
