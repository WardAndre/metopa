package metopa.catalog.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface WorkRepository
        extends JpaRepository<Work, UUID> {

    List<Work> findByOwnerIdOrderByUpdatedAtDesc(
            UUID ownerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Work w WHERE w.id = :workId")
    Optional<Work> findByIdForUpdate(
            @Param("workId") UUID workId
    );
}