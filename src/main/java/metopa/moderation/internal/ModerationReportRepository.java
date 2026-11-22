package metopa.moderation.internal;

import jakarta.persistence.LockModeType;
import metopa.moderation.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ModerationReportRepository
        extends JpaRepository<ModerationReport, UUID> {

    Page<ModerationReport> findByStatus(
            ReportStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT report
            FROM ModerationReport report
            WHERE report.id = :id
            """)
    Optional<ModerationReport> findByIdForUpdate(
            @Param("id") UUID id
    );
}
